package com.jariisseo.android

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 3D 점(Anchor)을 화면에 투영한 2D 위치. visible=false면 카메라 뒤이거나 추적을 잃은 점 */
data class ScreenPoint(val x: Float, val y: Float, val visible: Boolean)

/** 인식 박스를 화면 픽셀 좌표로 바꾼 것 */
data class ScreenBox(
    val label: String, val score: Float,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
)

/** GL 스레드 → UI 스레드로 넘기는 화면 상태 (Spring의 응답 DTO 같은 역할) */
data class ArUiState(
    val status: String = "확인 중...",
    val horizontalPlanes: Int = 0,
    val verticalPlanes: Int = 0,
    val points: List<ScreenPoint> = emptyList(),
    val lastDistanceM: Float? = null,
    val message: String = "바닥이나 벽을 비춘 뒤 화면을 탭하세요",
    val boxes: List<ScreenBox> = emptyList(),     // 8교시: 인식 박스
    val detectInfo: String = "",                  // 8교시: 추론 시간 등
    // 9교시: 탑뷰 배치도 (월드 좌표, 미터. 위에서 내려다보면 x=좌우, z=앞뒤)
    val mapItems: List<MapItem> = emptyList(),
    val cameraX: Float = 0f,
    val cameraZ: Float = 0f,
    val headingX: Float = 0f,                     // 카메라가 바라보는 방향 (바닥 평면에 투영)
    val headingZ: Float = -1f,
)

/** 배치도에 올라간 가구 1개. confirmed = 여러 번 확인돼서 진짜일 가능성이 높은 것 */
data class MapItem(val label: String, val x: Float, val z: Float, val count: Int, val confirmed: Boolean)

/**
 * GL 스레드에서 매 프레임 실행되는 조율자.
 * onState: 화면 상태가 바뀌면 호출 (⚠️ GL 스레드에서 불림)
 */
class ArRenderer(
    private val detector: Detector?,               // 8교시: 모델 로드 실패 시 null
    private val onState: (ArUiState) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var session: Session? = null   // UI 스레드가 쓰고 GL 스레드가 읽음

    private val background = BackgroundRenderer()
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var viewportChanged = false
    private var textureBoundTo: Session? = null

    // ── 7교시: 탭 → 앵커 ──
    // UI 스레드가 넣고 GL 스레드가 꺼내는 큐 (스레드 안전)
    private val pendingTaps = ConcurrentLinkedQueue<FloatArray>()
    @Volatile private var clearRequested = false
    private val anchors = mutableListOf<Anchor>()     // GL 스레드에서만 다룸
    private var message = ArUiState().message
    private var lastState: ArUiState? = null

    // 3D → 2D 변환용 행렬 (매 프레임 새로 만들지 않도록 재사용)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProjMatrix = FloatArray(16)
    private val world = FloatArray(4)
    private val clip = FloatArray(4)

    // ── 8교시: 백그라운드 인식 ──
    // 추론은 무거워서(수십~수백 ms) GL 스레드에서 하면 카메라 화면이 끊김 → 전용 스레드 1개
    private val detectExecutor = Executors.newSingleThreadExecutor()
    private val detecting = AtomicBoolean(false)          // 추론 중이면 새 프레임은 건너뜀
    @Volatile private var latestDetections: List<Detection> = emptyList()
    @Volatile private var detectionGeneration = 0         // 9교시: 새 결과가 나올 때마다 +1
    @Volatile private var detectInfo = if (detector == null) "인식기 없음" else "인식 준비 중..."
    private var lastDetectTimestamp = 0L
    private val cornerIn = FloatArray(4)
    private val cornerOut = FloatArray(4)

    // ── 9교시: 바닥 투영 + 중복 병합 ──
    /** 배치도의 가구 1개. 위치는 Anchor로 들고 있어서 ARCore가 지도를 보정하면 같이 따라감 */
    private class MapObject(val label: String, val anchor: Anchor) {
        var count = 1   // 몇 번 관측됐는지
    }
    private val mapObjects = mutableListOf<MapObject>()   // GL 스레드에서만 다룸
    private var processedGeneration = 0                   // 이미 투영한 인식 결과 번호

    /** 앱 종료 시: 인식 스레드를 멈추고 모델 해제 */
    fun release() {
        detectExecutor.shutdown()
        detectExecutor.awaitTermination(1, TimeUnit.SECONDS)
        detector?.close()
    }

    /** UI 스레드에서 호출: 탭 위치를 큐에 넣기만 함 (실제 처리는 다음 프레임에 GL 스레드에서) */
    fun onTap(x: Float, y: Float) {
        pendingTaps.add(floatArrayOf(x, y))
    }

    /** UI 스레드에서 호출: 점 모두 지우기 요청 */
    fun clear() {
        clearRequested = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        background.createOnGlThread()
        textureBoundTo = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return

        if (textureBoundTo !== session) {
            session.setCameraTextureName(background.textureId)
            textureBoundTo = session
            viewportChanged = true
        }
        if (viewportChanged) {
            session.setDisplayGeometry(Surface.ROTATION_0, viewportWidth, viewportHeight)
            viewportChanged = false
        }

        try {
            val frame = session.update()
            background.draw(frame)
            val camera = frame.camera

            if (clearRequested) {
                anchors.forEach { it.detach() }   // ARCore에 "이 점 더 이상 추적 안 해도 돼" 알림
                anchors.clear()
                mapObjects.forEach { it.anchor.detach() }   // 9교시: 배치도도 초기화
                mapObjects.clear()
                clearRequested = false
                message = "점과 배치도를 모두 지웠어요"
            }
            handleTaps(frame, camera)
            maybeStartDetection(frame)
            if (camera.trackingState == TrackingState.TRACKING) projectDetections(frame)   // 9교시
            report(buildState(session, frame, camera))
        } catch (e: Exception) {
            report(ArUiState(status = "오류: ${e.javaClass.simpleName}"))
        }
    }

    /** 쌓인 탭을 하나씩 꺼내 Hit Test → 평면에 맞으면 앵커 생성 */
    private fun handleTaps(frame: Frame, camera: Camera) {
        while (true) {
            val tap = pendingTaps.poll() ?: return
            if (camera.trackingState != TrackingState.TRACKING) {
                message = "추적 중일 때만 점을 찍을 수 있어요"
                continue
            }
            // 화면 (x, y)에서 카메라 방향으로 광선을 쏴서 맞은 것들 (가까운 순)
            val hit = frame.hitTest(tap[0], tap[1]).firstOrNull { h ->
                val trackable = h.trackable
                trackable is Plane && trackable.isPoseInPolygon(h.hitPose)   // 인식된 평면 "안쪽"만
            }
            if (hit == null) {
                message = "여기서 평면을 못 찾았어요. 바닥·벽을 비추며 천천히 움직여 보세요"
                continue
            }
            anchors += hit.createAnchor()
            val kind = if ((hit.trackable as Plane).type == Plane.Type.VERTICAL) "벽" else "바닥"
            message = "점 ${anchors.size}개 (방금 찍은 곳: $kind)"
        }
    }

    /**
     * 인식 스레드가 쉬고 있으면: 현재 카메라 이미지를 복사해서 넘김.
     * 바쁘면 이번 프레임은 건너뜀 (화면은 30fps, 인식은 가능한 만큼만)
     */
    private fun maybeStartDetection(frame: Frame) {
        val detector = detector ?: return
        if (frame.timestamp == lastDetectTimestamp) return
        if (!detecting.compareAndSet(false, true)) return     // 이미 추론 중

        val yuv = try {
            // CPU용 카메라 이미지 (보통 640x480). 빨리 close해야 하므로 복사 후 바로 닫음
            frame.acquireCameraImage().use { YuvFrame.from(it) }
        } catch (e: Exception) {   // NotYetAvailableException 등: 다음 프레임에 다시
            detecting.set(false)
            return
        }
        lastDetectTimestamp = frame.timestamp

        detectExecutor.execute {
            try {
                latestDetections = detector.detect(yuv)
                detectionGeneration++      // GL 스레드에 "새 결과 있음" 알림 (이 스레드만 씀)
                detectInfo = "추론 ${detector.lastInferenceMs}ms · 물체 ${latestDetections.size}개 · 좌표 ${detector.coordMode}"
            } catch (e: Exception) {
                detectInfo = "인식 오류: ${e.javaClass.simpleName} ${e.message}"
            } finally {
                detecting.set(false)
            }
        }
    }

    /**
     * 9교시: 새 인식 결과가 있으면, 각 박스의 "아래쪽 가운데"(다리가 바닥에 닿는 곳)를
     * 바닥에 Hit Test → 실제 위치(m) → 기존 가구와 가까우면 병합, 아니면 새로 추가
     */
    private fun projectDetections(frame: Frame) {
        val generation = detectionGeneration
        if (generation == processedGeneration) return   // 이미 처리한 결과
        processedGeneration = generation

        for (d in latestDetections) {
            val box = toView(frame, d)
            // 박스 아래가 화면 밖으로 잘렸으면 진짜 바닥 접점을 모름 → 건너뜀
            if (box.bottom > viewportHeight - EDGE_MARGIN_PX) continue

            val hit = frame.hitTest((box.left + box.right) / 2f, box.bottom)
                .filter { h ->
                    val p = h.trackable
                    p is Plane && p.type == Plane.Type.HORIZONTAL_UPWARD_FACING && p.isPoseInPolygon(h.hitPose)
                }
                .minByOrNull { it.hitPose.ty() }   // 수평면이 여러 개면(테이블 윗면 등) 가장 낮은 것 = 바닥
                ?: continue                        // 바닥을 아직 못 찾은 곳
            addOrMerge(d.label, hit)
        }
    }

    /** 같은 종류가 병합 거리 안에 있으면 "같은 물체"로 보고 관측 횟수만 +1 */
    private fun addOrMerge(label: String, hit: com.google.ar.core.HitResult) {
        val pose = hit.hitPose
        val nearest = mapObjects
            .filter { it.label == label && it.anchor.trackingState == TrackingState.TRACKING }
            .minByOrNull { floorDistance(it.anchor.pose, pose) }

        if (nearest != null && floorDistance(nearest.anchor.pose, pose) < MERGE_DISTANCE_M.getValue(label)) {
            nearest.count++
        } else {
            mapObjects += MapObject(label, hit.createAnchor())
        }
    }

    /** 바닥 평면 위 거리 (높이 y는 무시하고 x, z만) */
    private fun floorDistance(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dz * dz)
    }

    /** 카메라 이미지 좌표의 박스 → 화면 좌표 (ARCore가 회전·잘림을 모두 계산해 줌) */
    private fun toView(frame: Frame, d: Detection): ScreenBox {
        cornerIn[0] = d.left; cornerIn[1] = d.top; cornerIn[2] = d.right; cornerIn[3] = d.bottom
        frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS, cornerIn, Coordinates2d.VIEW, cornerOut)
        return ScreenBox(
            label = d.label, score = d.score,
            left = min(cornerOut[0], cornerOut[2]), top = min(cornerOut[1], cornerOut[3]),
            right = max(cornerOut[0], cornerOut[2]), bottom = max(cornerOut[1], cornerOut[3]),
        )
    }

    private fun buildState(session: Session, frame: Frame, camera: Camera): ArUiState {
        // 지금 추적 중인 평면 (다른 평면에 합쳐진 것은 제외)
        val planes = session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }

        // 3D 월드 좌표 → 화면 좌표 변환 행렬 = 투영 × 뷰
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)
        camera.getViewMatrix(viewMatrix, 0)
        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val lastDistance =
            if (anchors.size >= 2) distance(anchors[anchors.size - 2].pose, anchors.last().pose)
            else null

        return ArUiState(
            status = statusOf(camera),
            horizontalPlanes = planes.count { it.type != Plane.Type.VERTICAL },
            verticalPlanes = planes.count { it.type == Plane.Type.VERTICAL },
            points = anchors.map { toScreen(it) },
            lastDistanceM = lastDistance,
            message = message,
            boxes = latestDetections.map { toView(frame, it) },
            detectInfo = detectInfo,
            mapItems = mapObjects
                .filter { it.anchor.trackingState == TrackingState.TRACKING }
                .map {
                    val p = it.anchor.pose
                    MapItem(it.label, p.tx(), p.tz(), it.count, confirmed = it.count >= CONFIRM_COUNT)
                },
            cameraX = camera.pose.tx(),
            cameraZ = camera.pose.tz(),
            // 카메라는 자기 -Z 방향을 바라봄 → 그 방향의 x, z 성분
            headingX = -camera.pose.zAxis[0],
            headingZ = -camera.pose.zAxis[2],
        )
    }

    /** 앵커의 3D 위치를 화면 픽셀 좌표로 변환 */
    private fun toScreen(anchor: Anchor): ScreenPoint {
        if (anchor.trackingState != TrackingState.TRACKING) return ScreenPoint(0f, 0f, false)
        val pose = anchor.pose
        world[0] = pose.tx(); world[1] = pose.ty(); world[2] = pose.tz(); world[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProjMatrix, 0, world, 0)
        if (clip[3] <= 0f) return ScreenPoint(0f, 0f, false)   // 카메라 뒤쪽
        val ndcX = clip[0] / clip[3]                           // -1(왼쪽) ~ 1(오른쪽)
        val ndcY = clip[1] / clip[3]                           // -1(아래) ~ 1(위)
        return ScreenPoint(
            x = (ndcX + 1f) / 2f * viewportWidth,
            y = (1f - ndcY) / 2f * viewportHeight,             // 화면 y는 위→아래라 뒤집기
            visible = true,
        )
    }

    /** 두 점 사이의 직선 거리 (미터) */
    private fun distance(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // 상태가 바뀌었을 때만 알림 (data class라 == 가 값 비교)
    private fun report(state: ArUiState) {
        if (state != lastState) {
            lastState = state
            onState(state)
        }
    }

    private fun statusOf(camera: Camera): String = when (camera.trackingState) {
        TrackingState.TRACKING -> "추적 중 ✅"
        TrackingState.PAUSED -> "추적 대기: " + when (camera.trackingFailureReason) {
            TrackingFailureReason.NONE -> "초기화 중 (폰을 천천히 좌우로 움직여 주세요)"
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "너무 어두워요"
            TrackingFailureReason.EXCESSIVE_MOTION -> "너무 빨리 움직여요"
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "특징이 부족해요 (흰 벽·유리)"
            else -> camera.trackingFailureReason.name
        }
        TrackingState.STOPPED -> "추적 중지됨"
    }

    companion object {
        const val EDGE_MARGIN_PX = 20f      // 박스 아래가 화면 끝에서 이만큼 안쪽이어야 바닥 접점으로 인정
        const val CONFIRM_COUNT = 3         // 이만큼 관측돼야 "확정" (가끔 나오는 오인식 걸러내기)
        // 종류별 병합 거리: 의자는 서로 붙어 있어서 작게, 테이블·소파는 크게
        val MERGE_DISTANCE_M = mapOf("의자" to 0.35f, "테이블" to 0.6f, "소파" to 0.8f)
    }
}
