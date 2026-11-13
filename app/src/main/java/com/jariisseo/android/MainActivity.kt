package com.jariisseo.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jariisseo.android.ui.theme.JariIsseoTheme
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // ★ 글자 하나(statusText) 대신 화면 상태 전체를 들고 있음
    private var uiState by mutableStateOf(ArUiState())
    private var modelInfo by mutableStateOf("모델 로딩 중...")   // ★ 8교시
    private var wallMode by mutableStateOf(false)              // #38: 벽 대기 버튼 줄 표시 (꺼도 기록·외곽선은 유지)

    private var session: Session? = null          // ARCore 엔진. 처음엔 없음(null)
    private var installRequested = false          // AR 서비스 설치 화면을 이미 띄웠는지

    // ★ 8교시: Detector는 assets(Context)가 필요해서 onCreate에서 만들어야 함 → renderer도 onCreate에서
    private lateinit var renderer: ArRenderer
    private lateinit var glView: GLSurfaceView

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) showStatus("카메라 권한이 거부됐어요")
        }

    @SuppressLint("ClickableViewAccessibility")   // POC라 접근성용 performClick 처리는 생략
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★ 8교시: 모델 로드 → 실패해도 앱은 계속 (AR 측정 기능은 동작)
        val detector = try {
            Detector(this).also { modelInfo = it.describe() }
        } catch (e: Exception) {
            modelInfo = "모델 로드 실패: ${e.message}"
            null
        }
        // GL 스레드에서 온 상태를 UI 스레드로 넘겨서 화면 갱신
        renderer = ArRenderer(
            detector,
            onState = { state -> runOnUiThread { uiState = state } },
            onWallRecord = { ok -> runOnUiThread { vibrate(ok) } },   // #38: 화면이 벽에 붙어 있어 진동으로 알림
        )

        glView = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            // ★ 손가락을 뗄 때 그 위치를 렌더러에 전달
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) renderer.onTap(event.x, event.y)
                true
            }
        }

        setContent {
            JariIsseoTheme {
                ScanScreen()
            }
        }

        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @androidx.compose.runtime.Composable
    private fun ScanScreen() {
        val state = uiState
        Box(Modifier.fillMaxSize()) {
            // 1층: 카메라 영상 (OpenGL)
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

            // ★ 2층: 찍은 점과 선 (카메라 영상과 같은 크기라 픽셀 좌표가 그대로 맞음)
            Canvas(Modifier.fillMaxSize()) {
                val pts = state.points
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    if (a.visible && b.visible) {
                        drawLine(Color.White, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 5f)
                    }
                }
                pts.filter { it.visible }.forEach {
                    drawCircle(Color.Yellow, radius = 16f, center = Offset(it.x, it.y))
                }

                // ★ 8교시: 인식 박스 + 라벨
                state.boxes.forEach { b ->
                    val color = when (b.label) {
                        "테이블" -> Color.Green
                        "의자" -> Color.Cyan
                        else -> Color.Magenta
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(b.left, b.top),
                        size = Size(b.right - b.left, b.bottom - b.top),
                        style = Stroke(width = 5f),
                    )
                    labelPaint.color = color.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        "${b.label} ${(b.score * 100).toInt()}%", b.left + 8f, b.top + 44f, labelPaint
                    )
                }
            }

            // 3층 위쪽: 추적 상태 + 평면 개수
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.status, color = Color.White)
                Text(
                    "평면: 바닥 ${state.floorPlanes}개 · 벽 ${state.walls.size}개",
                    color = Color.White,
                )
                Text(modelInfo, color = Color.Yellow)   // ★ 8교시
                Text(state.detectInfo, color = Color.Yellow)
            }

            // ★ 3층 아래쪽: [#38 벽 대기 버튼] / [왼쪽] 거리 + 안내 + 가구 수 · [오른쪽] 9교시 탑뷰 미니맵 / [버튼]
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
            ) {
                if (wallMode) {
                    Text(
                        if (state.outlineClosed) "외곽선 완료"
                        else "벽을 돌아가며 순서대로: 화면을 벽에 평평하게 대고 볼륨 버튼 (진동 1번 = 기록, 2번 = 실패)",
                        color = Color.White, fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { renderer.outlineOp(OutlineOp.RECORD) }, modifier = Modifier.weight(1f)) {
                            Text("기록")
                        }
                        TextButton(onClick = { renderer.outlineOp(OutlineOp.UNDO) }) { Text("되돌리기") }
                        TextButton(onClick = { renderer.outlineOp(OutlineOp.RESET) }) { Text("다시") }
                        TextButton(onClick = { renderer.outlineOp(OutlineOp.CLOSE) }) { Text("완료") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.lastDistanceM?.let { "두 점 거리: %.2f m".format(it) }
                                ?: "점을 2개 찍으면 거리가 나와요",
                            color = Color.White,
                            fontSize = 18.sp,
                        )
                        Text(state.message, color = Color.LightGray, fontSize = 12.sp)
                        val confirmed = state.mapItems.filter { it.confirmed }
                        Text(
                            "확정: 테이블 ${confirmed.count { it.label == "테이블" }} · " +
                                "의자 ${confirmed.count { it.label == "의자" }} · " +
                                "소파 ${confirmed.count { it.label == "소파" }}",
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MiniMap(state, Modifier.size(150.dp))
                }
                Spacer(Modifier.height(6.dp))
                val small = PaddingValues(horizontal = 10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { renderer.clear() }, contentPadding = small) { Text("모두 지우기", fontSize = 13.sp) }
                    Button(onClick = { wallMode = !wallMode }, contentPadding = small) {
                        Text(if (wallMode) "벽 대기 끝" else "벽 대기", fontSize = 13.sp)
                    }
                    Button(onClick = { exportLayout() }, contentPadding = small) { Text("JSON 내보내기", fontSize = 13.sp) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraPermission()) return

        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }

                session = Session(this).apply {
                    configure(Config(this).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        focusMode = Config.FocusMode.AUTO
                    })
                }
            } catch (e: Exception) {
                showStatus("세션 생성 실패: ${e.javaClass.simpleName}")
                return
            }
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            showStatus("카메라를 열 수 없어요 (다른 앱이 사용 중?)")
            session?.close()
            session = null
            renderer.session = null
            return
        }

        renderer.session = session
        glView.onResume()
    }

    // 그리기 스레드를 "먼저" 멈추고 카메라 반납
    override fun onPause() {
        super.onPause()
        glView.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        renderer.session = null
        renderer.release()   // ★ 8교시: 인식 스레드 정지 + 모델 해제
        session?.close()
        session = null
        super.onDestroy()
    }

    // 박스 라벨 글자용 (Compose Canvas에서 Android의 Paint로 글자 그리기)
    private val labelPaint = Paint().apply {
        textSize = 40f
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }

    /** #38: 벽 대기 중에는 볼륨 버튼 = [기록] (화면을 벽에 대고 있어 화면 버튼을 못 누름) */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!wallMode || !volume) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) renderer.outlineOp(OutlineOp.RECORD)   // 길게 눌러도 1번만
        return true                                                         // 음량은 바꾸지 않음
    }

    /** #38: 기록 성공 = 길게 1번, 실패 = 짧게 2번 */
    private fun vibrate(ok: Boolean) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = if (ok) longArrayOf(0, 250) else longArrayOf(0, 100, 120, 100)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * #7: 지금 화면 상태를 배치 JSON으로 저장 → 공유 시트로 보내기.
     * 상태 문구(status)는 매 프레임 덮어써져서 안내는 Toast로
     */
    private fun exportLayout() {
        val state = uiState
        val problem = when {
            state.floorPolygon.size < 3 -> "바닥 외곽선이 아직 없어요. 바닥을 더 비춰주세요"
            state.mapItems.none { it.confirmed && it.label == "테이블" } -> "확정된 테이블이 없어요"
            else -> null
        }
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_SHORT).show()
            return
        }

        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val name = "layout-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json"
        val file = File(dir, name).apply { writeText(toLayoutJson(state)) }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "배치 JSON 보내기"))
    }

    private fun showStatus(text: String) {
        uiState = uiState.copy(status = text)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
}
