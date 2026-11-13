package com.jariisseo.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Paint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.jariisseo.android.ui.theme.JariIsseoTheme
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException

class MainActivity : ComponentActivity() {

    // ★ 글자 하나(statusText) 대신 화면 상태 전체를 들고 있음
    private var uiState by mutableStateOf(ArUiState())
    private var modelInfo by mutableStateOf("모델 로딩 중...")   // ★ 8교시

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
        renderer = ArRenderer(detector) { state -> runOnUiThread { uiState = state } }

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

            // ★ 3층 아래쪽: [왼쪽] 거리 + 안내 + 가구 수 + 버튼 / [오른쪽] 9교시 탑뷰 미니맵
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { renderer.clear() }) { Text("모두 지우기") }
                }
                Spacer(Modifier.width(8.dp))
                MiniMap(state, Modifier.size(150.dp))
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

    private fun showStatus(text: String) {
        uiState = uiState.copy(status = text)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
}
