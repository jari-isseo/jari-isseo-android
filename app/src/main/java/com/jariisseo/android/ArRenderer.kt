package com.jariisseo.android

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
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
    val floorPlanes: Int = 0,                     // #4: 바닥 높이의 평면만 (책상 윗면·의자 앉는 면 제외)
    val points: List<ScreenPoint> = emptyList(),
    val lastDistanceM: Float? = null,
    val message: String = "바닥이나 벽을 비춘 뒤 화면을 탭하세요",
    val boxes: List<ScreenBox> = emptyList(),     // 8교시: 인식 박스
    val detectInfo: String = "",                  // 8교시: 추론 시간 등
    // 9교시: 탑뷰 배치도 (#6: 매장 좌표, 미터, 0 이상. 가장 긴 벽이 x축과 평행)
    val mapItems: List<MapItem> = emptyList(),
    val cameraX: Float = 0f,
    val cameraZ: Float = 0f,
    val headingX: Float = 0f,                     // 카메라가 바라보는 방향 (바닥 평면에 투영)
    val headingZ: Float = -1f,
    val floorPolygon: List<Pair<Float, Float>> = emptyList(),   // #4: 바닥 외곽선 (매장 x, z)
    val walls: List<WallLine> = emptyList(),                    // #5: 벽 선분 (매장 x, z)
    val touchedWalls: List<WallLine> = emptyList(),             // #38: 기록한 벽 (짧은 선분, 매장 x, z). 완료되면 비움
    val outlineClosed: Boolean = false,                         // #38: 외곽선 완료 → floorPolygon·walls가 외곽선
)

/** 배치도에 올라간 가구 1개. confirmed = 여러 번 확인돼서 진짜일 가능성이 높은 것 */
data class MapItem(val label: String, val x: Float, val z: Float, val count: Int, val confirmed: Boolean)

/** #38: 벽 대기 버튼 (UI 스레드 → GL 스레드) */
enum class OutlineOp { RECORD, UNDO, RESET, CLOSE }

/**
 * GL 스레드에서 매 프레임 실행되는 조율자.
 * onState: 화면 상태가 바뀌면 호출 (⚠️ GL 스레드에서 불림)
 */
class ArRenderer(
    private val detector: Detector?,               // 8교시: 모델 로드 실패 시 null
    private val onState: (ArUiState) -> Unit,
    private val onWallRecord: (Boolean) -> Unit,   // #38: 벽 기록 결과 (성공/실패). ⚠️ GL 스레드에서 불림
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
    /**
     * 배치도의 가구 1개. 위치는 Anchor로 들고 있어서 ARCore가 지도를 보정하면 같이 따라감.
     * 관측마다 앵커 기준 오프셋(dx, dz)을 누적 → 위치 = 앵커 + 오프셋 평균 (첫 관측은 오프셋 0)
     */
    private class MapObject(val label: String, val anchor: Anchor) {
        var count = 1   // 몇 번 관측됐는지
        private var sumDx = 0f
        private var sumDz = 0f

        fun x() = anchor.pose.tx() + sumDx / count
        fun z() = anchor.pose.tz() + sumDz / count

        fun addObservation(pose: Pose) {
            sumDx += pose.tx() - anchor.pose.tx()
            sumDz += pose.tz() - anchor.pose.tz()
            count++
        }

        /** 다른 가구를 흡수: 두 평균을 관측 수로 가중 평균 (이 앵커는 그대로) */
        fun absorb(other: MapObject) {
            val total = count + other.count
            val x = (x() * count + other.x() * other.count) / total
            val z = (z() * count + other.z() * other.count) / total
            count = total
            sumDx = (x - anchor.pose.tx()) * total
            sumDz = (z - anchor.pose.tz()) * total
        }
    }
    private val mapObjects = mutableListOf<MapObject>()   // GL 스레드에서만 다룸
    private var processedGeneration = 0                   // 이미 투영한 인식 결과 번호

    // ── #4·#5: 바닥 외곽선·벽 (본 것을 누적, 1초에 한 번 다시 계산) ──
    // 누적은 기준 앵커(추적 시작 위치에 1개) 좌표로 → ARCore가 지도를 보정해도 가구(앵커)와 함께 따라감.
    // 기준 앵커는 바닥과 무관하게 유지 → 바닥을 다시 잡아도 벽 좌표가 바뀌지 않음
    private var mapAnchor: Anchor? = null
    private val floorMap = FloorPolygon()
    private var floorLocal = emptyList<Pair<Float, Float>>()   // 기준 앵커 좌표 외곽선
    private var floorLocalY: Float? = null                     // 지금 바닥으로 보는 높이 (기준 앵커 좌표 y)
    private var lastFloorBuildNs = 0L
    private val wallMap = WallSegments()
    private var wallsLocal = emptyList<WallLine>()

    // ── #38: 벽에 폰 대기 → 벽 선 교점으로 방 외곽선 (완료되면 자동 인식 바닥·벽 대신 사용) ──
    // 기록은 기준 앵커 좌표로 → ARCore가 지도를 보정해도 따라감. 폰을 벽에 대면 카메라가 가려져
    // 추적을 놓칠 수 있어 앵커를 새로 만들지 않고, 직전 추적 pose도 씀
    private val pendingOutlineOps = ConcurrentLinkedQueue<OutlineOp>()
    private val wallTouchesLocal = mutableListOf<WallTouch>()   // GL 스레드에서만 다룸
    private var outlineClosed = false
    // 카메라·기준 앵커가 둘 다 추적 중일 때의 "기준 앵커 기준 카메라 pose" (추적을 놓친 프레임의 pose는 섞지 않음)
    private var lastLocalCameraPose: Pose? = null
    private var lastTrackedNs = 0L

    /** 앱 종료 시: 인식 스레드를 멈추고 모델 해제 */
    fun release() {
        detectExecutor.execute { detector?.close() }   // 진행 중인 추론이 끝난 뒤 실행됨
        detectExecutor.shutdown()                       // 새 작업만 막고, 기다리지 않음
    }

    /** UI 스레드에서 호출: 탭 위치를 큐에 넣기만 함 (실제 처리는 다음 프레임에 GL 스레드에서) */
    fun onTap(x: Float, y: Float) {
        pendingTaps.add(floatArrayOf(x, y))
    }

    /** UI 스레드에서 호출: 점 모두 지우기 요청 */
    fun clear() {
        clearRequested = true
    }

    /** UI 스레드에서 호출: 벽 기록·되돌리기·다시·완료 (처리는 다음 프레임에 GL 스레드에서) */
    fun outlineOp(op: OutlineOp) {
        pendingOutlineOps.add(op)
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
                resetMap()                                  // #4·#5: 누적한 바닥·벽도 초기화 (#38 기록한 벽 포함)
                clearRequested = false
                message = "점과 배치도를 모두 지웠어요"
            }
            val anchor = trackedMapAnchor()
            if (camera.trackingState == TrackingState.TRACKING && anchor != null) {
                lastLocalCameraPose = anchor.pose.inverse().compose(camera.pose)
                lastTrackedNs = frame.timestamp
            }
            handleOutlineOps(frame)
            handleTaps(frame, camera)
            if (outlineClosed) {   // #40: 가구 인식은 가구 단계(외곽선 완료 후)에서만
                maybeStartDetection(frame)
                if (camera.trackingState == TrackingState.TRACKING) projectDetections(frame)   // 9교시
            }
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

    private fun handleOutlineOps(frame: Frame) {
        while (true) {
            when (pendingOutlineOps.poll() ?: return) {
                OutlineOp.RECORD -> onWallRecord(recordWall(frame))
                OutlineOp.UNDO -> {
                    if (outlineClosed) outlineClosed = false else wallTouchesLocal.removeLastOrNull()
                    message = "벽 ${wallTouchesLocal.size}개 기록"
                }
                OutlineOp.RESET -> {
                    wallTouchesLocal.clear()
                    outlineClosed = false
                    message = "벽을 처음부터 기록해 주세요"
                }
                OutlineOp.CLOSE -> {
                    if (wallTouchesLocal.size < 3) {
                        message = "벽을 3개 이상 기록해야 완료할 수 있어요"
                    } else if (FloorPolygon.area(CornerOutline.fromWalls(wallTouchesLocal)) < MIN_OUTLINE_AREA_M2) {
                        // 같은 벽을 여러 번 기록하는 등 벽 방향이 겹치면 넓이 0인 외곽선이 됨
                        message = "벽 방향이 겹치거나 나란해요. 다른 벽을 기록해 주세요"
                    } else {
                        outlineClosed = true
                        message = "외곽선 완료 (벽 ${wallTouchesLocal.size}개) · 매장을 천천히 걸으며 테이블·의자를 비춰 주세요"
                    }
                }
            }
        }
    }

    /**
     * #38: 폰 화면을 벽에 댄 순간의 카메라 pose → 벽 1개. 기록했으면 true.
     * 화면을 대면 뒷면 카메라가 방 안을 봐서 추적이 유지됨 (뒷면을 대도 벽 선은 같음).
     * 위치 = 벽 위 한 점, 카메라 Z축을 바닥에 투영 = 벽 법선.
     * 카메라·기준 앵커가 둘 다 추적 중일 때 저장한 기준 앵커 기준 pose를 씀 → 이번 프레임에 추적을 놓쳤으면
     * 직전(RECENT_POSE_NS 이내) 값. 추적을 놓친 프레임의 앵커 pose와 과거 카메라 pose를 섞지 않음
     */
    private fun recordWall(frame: Frame): Boolean {
        if (outlineClosed) {
            message = "외곽선이 완료됐어요. [되돌리기]로 다시 고칠 수 있어요"
            return false
        }
        val local = lastLocalCameraPose
        if (local == null) {
            message = "아직 위치를 못 잡았어요. 주변을 비추며 천천히 움직여 주세요"
            return false
        }
        val ageNs = frame.timestamp - lastTrackedNs
        if (ageNs > RECENT_POSE_NS) {
            message = "추적을 놓쳤어요. 벽에서 폰을 떼고 주변을 비춘 뒤 다시 기록해 주세요"
            return false
        }
        val p = local.translation
        val n = local.zAxis
        if (sqrt(n[0] * n[0] + n[2] * n[2]) < 0.5f) {   // 폰이 바닥·천장 쪽을 향함 (60° 이상 기울어짐)
            message = "폰을 세워서 화면을 벽에 평평하게 대 주세요"
            return false
        }
        wallTouchesLocal += WallTouch(p[0], p[2], n[0], n[2])
        val source = if (ageNs == 0L) "추적 중" else "직전 pose ${ageNs / 1_000_000}ms 전"
        Log.d(TAG, "벽 기록 #%d: (%.2f, %.2f) 법선 (%.2f, %.2f) · %s".format(
            wallTouchesLocal.size, p[0], p[2], n[0], n[2], source,
        ))
        message = "벽 ${wallTouchesLocal.size}개 기록 ($source) · 다음 벽으로"
        return true
    }

    /** 기록한 벽 → 지금의 월드 좌표 (기준 앵커 기준) */
    private fun wallTouchesWorld(): List<WallTouch> {
        val pose = mapAnchor?.takeIf { it.trackingState != TrackingState.STOPPED }?.pose ?: return emptyList()
        return wallTouchesLocal.map { w ->
            val p = pose.transformPoint(floatArrayOf(w.x, 0f, w.z))
            val n = pose.rotateVector(floatArrayOf(w.nx, 0f, w.nz))
            WallTouch(p[0], p[2], n[0], n[2])
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
     * 바닥에 Hit Test → 카메라 반대쪽으로 반 깊이만큼 밀어 중심 추정 → 기존 가구와 가까우면 병합, 아니면 새로 추가
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
            val center = estimateCenter(frame.camera.pose, hit.hitPose, HALF_DEPTH_M.getValue(d.label))
            addOrMerge(d.label, hit.trackable as Plane, center, hit.hitPose)
        }
    }

    /**
     * 박스 아래 접점은 카메라에 가장 가까운 다리 → 어느 방향에서 보느냐에 따라 위치가 달라짐.
     * 카메라 → 접점 수평 방향으로 반 깊이만큼 더 밀어 가구 중심을 추정 (높이는 바닥 그대로)
     */
    private fun estimateCenter(camera: Pose, contact: Pose, halfDepth: Float): Pose {
        val dx = contact.tx() - camera.tx()
        val dz = contact.tz() - camera.tz()
        val len = sqrt(dx * dx + dz * dz)
        if (len < 0.01f) return contact   // 바로 위에서 내려다본 경우: 방향을 알 수 없음
        return Pose.makeTranslation(
            contact.tx() + dx / len * halfDepth,
            contact.ty(),
            contact.tz() + dz / len * halfDepth,
        )
    }

    /** 같은 종류가 병합 거리 안에 있으면 "같은 물체"로 보고 관측 위치를 평균에 누적 */
    private fun addOrMerge(label: String, plane: Plane, pose: Pose, contact: Pose) {
        val nearest = mapObjects
            .filter { it.label == label && it.anchor.trackingState == TrackingState.TRACKING }
            .minByOrNull { floorDistance(it, pose) }

        val merged = nearest != null && floorDistance(nearest, pose) < MERGE_DISTANCE_M.getValue(label)
        if (merged) {
            nearest!!.addObservation(pose)
        } else {
            mapObjects += MapObject(label, plane.createAnchor(pose))
        }
        // #2·#3: 관측 위치(center)와 누적 평균(avg) 비교용 (adb logcat -s ArRenderer)
        val obj = if (merged) nearest!! else mapObjects.last()
        Log.d(TAG, "%s contact=(%.2f, %.2f) center=(%.2f, %.2f) avg=(%.2f, %.2f) → %s".format(
            label, contact.tx(), contact.tz(), pose.tx(), pose.tz(), obj.x(), obj.z(),
            if (merged) "병합 #${mapObjects.indexOf(obj)} (${obj.count}회)" else "새로 추가 #${mapObjects.lastIndex}",
        ))
        if (merged) remerge(obj)
    }

    /**
     * 평균이 움직여 같은 종류의 다른 가구와 병합 거리 안에 들어오면 하나로 합침.
     * 첫 관측이 튀어 따로 생긴 중복을 정리 (관측 수가 많은 쪽이 남고, 나머지 앵커는 해제)
     */
    private fun remerge(obj: MapObject) {
        val other = mapObjects
            .filter { it !== obj && it.label == obj.label && it.anchor.trackingState == TrackingState.TRACKING }
            .minByOrNull { floorDistance(it, obj.x(), obj.z()) }
            ?: return
        if (floorDistance(other, obj.x(), obj.z()) >= MERGE_DISTANCE_M.getValue(obj.label)) return

        val (keep, drop) = if (obj.count >= other.count) obj to other else other to obj
        val dropIndex = mapObjects.indexOf(drop)
        keep.absorb(drop)
        drop.anchor.detach()
        mapObjects.remove(drop)
        Log.d(TAG, "%s 재병합 #%d → #%d avg=(%.2f, %.2f) (%d회)".format(
            obj.label, dropIndex, mapObjects.indexOf(keep), keep.x(), keep.z(), keep.count,
        ))
    }

    /** 가구 평균 위치와 관측 위치의 바닥 평면 위 거리 (높이 y는 무시하고 x, z만) */
    private fun floorDistance(obj: MapObject, b: Pose) = floorDistance(obj, b.tx(), b.tz())

    private fun floorDistance(obj: MapObject, x: Float, z: Float): Float {
        val dx = obj.x() - x
        val dz = obj.z() - z
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
        val floors = floorPlanes(planes)
        if (frame.timestamp - lastFloorBuildNs >= FLOOR_BUILD_INTERVAL_NS) {
            lastFloorBuildNs = frame.timestamp
            ensureMapAnchor(session, camera)
            updateFloorPolygon(floors)
            updateWalls(session)
        }

        // 3D 월드 좌표 → 화면 좌표 변환 행렬 = 투영 × 뷰
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)
        camera.getViewMatrix(viewMatrix, 0)
        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val lastDistance =
            if (anchors.size >= 2) distance(anchors[anchors.size - 2].pose, anchors.last().pose)
            else null

        // #38: 벽 기록 외곽선이 완료됐으면 자동 인식 벽 대신 외곽선(벽 선 교점, 직각 정리)과 그 변을 씀
        // #40: 외곽선 완료 = 가구 단계. 벽 단계에서는 자동 인식 바닥은 빼고 자동 벽만 참고선으로
        val touches = wallTouchesWorld()
        val outline = if (outlineClosed) CornerOutline.fromWalls(touches).takeIf { it.size >= 3 } else null
        val floorWorld = outline ?: emptyList()
        val wallsWorld = outline?.let(CornerOutline::edges) ?: wallsWorld()
        // #40: 외곽선 밖(NEAR_M 초과) 가구는 제외 (유리 너머·옆방)
        val items = mapObjects.filter {
            it.anchor.trackingState == TrackingState.TRACKING && (outline == null || CornerOutline.near(outline, it.x() to it.z()))
        }
        val pending = if (outline == null) touches.map { CornerOutline.segment(it) } else emptyList()
        // #6: 월드 좌표 → 매장 좌표 (가장 긴 벽이 x축과 평행, 최소점 (0, 0))
        // #40: 외곽선이 있으면 외곽선만 기준 → 가구가 추가돼도 좌표가 안 바뀜 (벽에 붙은 가구는 최대 -NEAR_M)
        val store = if (outline != null) StoreFrame.of(outline, wallsWorld, emptyList())
            else StoreFrame.of(floorWorld, wallsWorld, items.map { it.x() to it.z() } + pending.flatMap { listOf(it.first, it.second) })
        val (camX, camZ) = store.point(camera.pose.tx() to camera.pose.tz())
        // 카메라는 자기 -Z 방향을 바라봄 → 그 방향의 x, z 성분
        val (headX, headZ) = store.direction(-camera.pose.zAxis[0], -camera.pose.zAxis[2])

        return ArUiState(
            status = statusOf(camera),
            floorPlanes = floors.size,
            points = anchors.map { toScreen(it) },
            lastDistanceM = lastDistance,
            message = message,
            boxes = if (outline != null) latestDetections.map { toView(frame, it) } else emptyList(),
            detectInfo = detectInfo,
            mapItems = items.map {
                val (x, z) = store.point(it.x() to it.z())
                MapItem(it.label, x, z, it.count, confirmed = it.count >= CONFIRM_COUNT)
            },
            cameraX = camX,
            cameraZ = camZ,
            headingX = headX,
            headingZ = headZ,
            floorPolygon = floorWorld.map(store::point),
            walls = wallsWorld.map(store::wall),
            touchedWalls = pending.map(store::wall),
            outlineClosed = outline != null,
        )
    }

    /** 기준 앵커가 없으면(처음·지우기 후·추적 완전 중지) 지금 카메라 위치에 만듦. 방향은 월드 축 그대로(y = 위) */
    private fun ensureMapAnchor(session: Session, camera: Camera) {
        if (mapAnchor?.trackingState == TrackingState.STOPPED) resetMap()
        if (mapAnchor != null || camera.trackingState != TrackingState.TRACKING) return
        val p = camera.pose
        mapAnchor = session.createAnchor(Pose.makeTranslation(p.tx(), p.ty(), p.tz()))
    }

    /** 추적 중인 기준 앵커 (놓쳤으면 null) */
    private fun trackedMapAnchor() = mapAnchor?.takeIf { it.trackingState == TrackingState.TRACKING }

    /** 기준 앵커 좌표 (x, z) → 지금의 월드 (x, z). 앵커를 놓쳤으면 null */
    private fun anchorToWorld(): ((Pair<Float, Float>) -> Pair<Float, Float>)? {
        val pose = trackedMapAnchor()?.pose ?: return null
        val y = floorLocalY ?: 0f
        return { (x, z) -> pose.transformPoint(floatArrayOf(x, y, z)).let { it[0] to it[2] } }
    }

    /** 기준 앵커 좌표 벽 선분 → 지금의 월드 좌표 */
    private fun wallsWorld(): List<WallLine> {
        val toWorld = anchorToWorld() ?: return emptyList()
        return wallsLocal.map { (a, b) -> toWorld(a) to toWorld(b) }
    }

    /** 누적한 바닥만 비움 (더 낮은 진짜 바닥이 나타났을 때). 벽은 유지 */
    private fun resetFloor() {
        floorMap.clear()
        floorLocal = emptyList()
    }

    /** 기준 앵커와 누적한 바닥·벽을 모두 비움 ("모두 지우기", 추적 중지) */
    private fun resetMap() {
        mapAnchor?.detach()
        mapAnchor = null
        resetFloor()
        floorLocalY = null
        wallMap.clear()
        wallsLocal = emptyList()
        wallTouchesLocal.clear()   // #38: 기준 앵커 좌표라 함께 비움
        outlineClosed = false
        lastLocalCameraPose = null
    }

    /** 평면 경계 점들의 월드 좌표 (x, y, z) */
    private fun boundaryWorld(plane: Plane): List<FloatArray> {
        val buf = plane.polygon
        return (0 until buf.limit() / 2).map { i ->
            plane.centerPose.transformPoint(floatArrayOf(buf.get(2 * i), 0f, buf.get(2 * i + 1)))
        }
    }

    /**
     * #5: 수직 평면 중 벽만 골라 바닥 위 선분으로 → 평면마다 최신 선분 1개 저장 → 벽 목록 다시 묶기.
     * 책상 옆판·PC 본체(낮음), 작은 조각(좁음)은 제외. 모니터 등 짧은 것은 WallSegments가 최소 길이로 거름.
     * 다른 평면에 합쳐졌거나 추적이 완전히 끝난 평면은 지우고, 잠시 안 보이는 평면은 마지막 선분 유지
     */
    private fun updateWalls(session: Session) {
        val anchor = trackedMapAnchor() ?: return
        val localY = floorLocalY ?: return   // 바닥을 아직 못 잡았으면 벽 높이를 잴 수 없음
        val floorY = anchor.pose.transformPoint(floatArrayOf(0f, localY, 0f))[1]
        val toLocal = anchor.pose.inverse()

        var candidateCount = 0
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.type != Plane.Type.VERTICAL) continue
            if (plane.subsumedBy != null || plane.trackingState == TrackingState.STOPPED) {
                wallMap.remove(plane)
                continue
            }
            if (plane.trackingState != TrackingState.TRACKING) continue   // 잠시 놓침: 마지막 선분 유지
            val line = wallLineOf(plane, floorY, toLocal)
            if (line == null) wallMap.remove(plane) else { wallMap.put(plane, line); candidateCount++ }
        }
        // 방 중심 = 바닥 외곽선 꼭짓점 평균 (겹친 평행 벽 중 바깥쪽 고르기용)
        val center = floorLocal.takeIf { it.isNotEmpty() }?.let { f -> f.sumOf { it.first.toDouble() }.toFloat() / f.size to f.sumOf { it.second.toDouble() }.toFloat() / f.size }
        val walls = wallMap.walls(center, floorLocal)
        if (walls != wallsLocal) {
            Log.d(TAG, "벽 후보 평면 %d개 → 벽 선분 %d개 (길이 %s)".format(
                candidateCount, walls.size,
                walls.joinToString { (a, b) -> "%.1fm".format(sqrt((b.first - a.first).let { it * it } + (b.second - a.second).let { it * it })) },
            ))
            walls.forEachIndexed { i, (a, b) ->
                // 각도: x축 기준 0~180° (같은 방향 벽끼리 비교용)
                val deg = (Math.toDegrees(atan2((b.second - a.second).toDouble(), (b.first - a.first).toDouble())) + 180.0) % 180.0
                Log.d(TAG, "  벽 #%d: (%.2f, %.2f) → (%.2f, %.2f), 각도 %.0f°".format(i, a.first, a.second, b.first, b.second, deg))
            }
        }
        wallsLocal = walls
    }

    /** 벽 후보 평면 → 기준 앵커 좌표의 바닥 위 선분 (벽이 아니면 null) */
    private fun wallLineOf(plane: Plane, floorY: Float, toLocal: Pose): WallLine? {
        val world = boundaryWorld(plane)
        if (world.isEmpty()) return null
        val top = world.maxOf { it[1] } - floorY
        val height = top - (world.minOf { it[1] } - floorY)
        // 모니터(꼭대기 ~1.25m, 화면 높이 ~0.35m)·책상 옆판·의자 등받이 제외
        if (top < WALL_MIN_TOP_M || height < WALL_MIN_HEIGHT_M) return null
        val pts = world.map { toLocal.transformPoint(it).let { p -> p[0] to p[2] } }
        // 벽 방향 = 바닥 위에서 법선(평면 y축)에 수직인 방향
        val n = toLocal.rotateVector(plane.centerPose.yAxis)
        val len = sqrt(n[0] * n[0] + n[2] * n[2])
        if (len < 1e-3f) return null
        val dx = -n[2] / len
        val dz = n[0] / len
        val cx = pts.sumOf { it.first.toDouble() }.toFloat() / pts.size
        val cz = pts.sumOf { it.second.toDouble() }.toFloat() / pts.size
        val ts = pts.map { (x, z) -> (x - cx) * dx + (z - cz) * dz }
        val t0 = ts.min()
        val t1 = ts.max()
        if (t1 - t0 < WALL_MIN_OBS_M) return null
        Log.d(TAG, "벽 후보 %08x: 꼭대기 %.2fm · 높이 %.2fm · 폭 %.2fm · 중심 (%.2f, %.2f)".format(plane.hashCode(), top, height, t1 - t0, cx, cz))
        return ((cx + dx * t0) to (cz + dz * t0)) to ((cx + dx * t1) to (cz + dz * t1))
    }

    /** 위를 향한 수평면 중 가장 낮은 높이 ± FLOOR_TOLERANCE_M 안의 것 = 바닥 (책상 윗면·의자 앉는 면 제외) */
    private fun floorPlanes(planes: List<Plane>): List<Plane> {
        val up = planes.filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
        val floorY = up.minOfOrNull { it.centerPose.ty() } ?: return emptyList()
        return up.filter { it.centerPose.ty() - floorY < FLOOR_TOLERANCE_M }
    }

    /** 바닥 평면들의 경계(평면 로컬 x, z) → 월드 → 기준 앵커 좌표 (x, z) → 누적 → 합집합 외곽선 */
    private fun updateFloorPolygon(floors: List<Plane>) {
        val lowest = floors.minByOrNull { it.centerPose.ty() } ?: return
        val anchor = trackedMapAnchor() ?: return
        val toLocal = anchor.pose.inverse()
        val lowestY = toLocal.transformPoint(lowest.centerPose.translation)[1]
        // 처음이거나, 지금까지 바닥으로 본 것(예: 책상 윗면)보다 더 낮은 진짜 바닥이 나타나면 바닥만 다시 누적 (벽은 유지)
        val prevY = floorLocalY
        if (prevY == null || lowestY < prevY - FLOOR_TOLERANCE_M) {
            if (prevY != null) Log.d(TAG, "바닥 높이 변경 %.2f → %.2f: 누적 바닥 초기화 (벽 유지)".format(prevY, lowestY))
            resetFloor()
            floorLocalY = lowestY
        }

        val pieces = floors.map { plane ->
            boundaryWorld(plane).map { toLocal.transformPoint(it).let { p -> p[0] to p[2] } }
        }
        floorMap.add(pieces)
        val polygon = floorMap.polygon()
        if (polygon != floorLocal) {
            Log.d(TAG, "바닥 조각 %d개 누적 → 외곽선 꼭짓점 %d개, 면적 %.1fm²".format(
                floors.size, polygon.size, FloorPolygon.area(polygon),
            ))
        }
        floorLocal = polygon
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
        // 의자 0.55: 실측 로그에서 같은 의자 관측 간 최대 0.51m, 다른 의자 간 최소 0.65m (#3)
        // 테이블·소파: 실측 없이 크기 비율로 추정 (의자 약 0.45m → 0.55, 테이블 약 0.8m → 0.8)
        val MERGE_DISTANCE_M = mapOf("의자" to 0.55f, "테이블" to 0.8f, "소파" to 1.0f)
        // 박스 아래 접점(카메라 쪽 끝) → 가구 중심까지 거리. 카메라 반대 방향으로 이만큼 밀어 중심 추정
        val HALF_DEPTH_M = mapOf("의자" to 0.2f, "테이블" to 0.35f, "소파" to 0.4f)
        // #4: 가장 낮은 수평면에서 이 높이 안이면 바닥 (책상 윗면 ~0.7m, 의자 앉는 면 ~0.45m는 제외)
        const val FLOOR_TOLERANCE_M = 0.15f
        private const val FLOOR_BUILD_INTERVAL_NS = 1_000_000_000L   // 바닥 외곽선·벽은 1초에 한 번만 계산
        // #5: 벽 후보 — 평면 꼭대기가 바닥에서 이 높이 이상 (책상 옆판 ~0.75m, 모니터 ~1.25m 제외),
        // 평면 세로 높이가 이 이상 (모니터 화면 ~0.35m 제외), 가로 폭이 이 이상
        const val WALL_MIN_TOP_M = 1.5f
        const val WALL_MIN_HEIGHT_M = 0.6f
        const val WALL_MIN_OBS_M = 0.6f
        // #38: 벽에 대서 추적을 놓쳤을 때 이 시간 안의 직전 추적 pose를 씀
        private const val RECENT_POSE_NS = 1_500_000_000L
        // #38: 이보다 좁은 외곽선은 완료하지 않음 (벽 방향이 겹친 경우)
        private const val MIN_OUTLINE_AREA_M2 = 1f
        private const val TAG = "ArRenderer"
    }
}
