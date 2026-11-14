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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.jariisseo.android.net.Server
import com.jariisseo.android.ui.BigNumber
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.JariIsseoTheme
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // ★ 글자 하나(statusText) 대신 화면 상태 전체를 들고 있음
    private var uiState by mutableStateOf(ArUiState())
    private var modelInfo by mutableStateOf("모델 로딩 중...")   // ★ 8교시
    private var editLayout by mutableStateOf<Layout?>(null)    // #13: 보정 중인 배치 ([스캔 완료] 때 새로 만듦)

    private var session: Session? = null          // ARCore 엔진. 처음엔 없음(null)
    private var installRequested = false          // AR 서비스 설치 화면을 이미 띄웠는지

    // #10: AR(카메라·인식)은 스캔 화면이 보이고 액티비티가 resume 상태일 때만 동작
    private var scanVisible = false
    private var resumed = false

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
        Server.init(this)   // #11: 서버 API·토큰 저장

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

        // #10: 로그인 → 홈 → 스캔 → 보정 → 뷰 (스캔 화면만 여기서 그림)
        setContent {
            JariIsseoTheme {
                AppNav(
                    scanContent = { onClose, onDone ->
                        // #13: [스캔 완료] = 지금 스캔 결과로 보정할 배치를 새로 만들고 보정 화면으로 (이전 보정 내용은 덮어씀)
                        ScanScreen(onClose) {
                            editLayout = layoutOf(uiState)
                            onDone()
                        }
                    },
                    layout = editLayout,
                    onLayoutChange = { editLayout = it },
                )
            }
        }

        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    @Composable
    private fun ScanScreen(onClose: () -> Unit, onDone: () -> Unit) {
        val state = uiState

        // #10: 스캔 화면에 들어오면 AR 시작, 나가면 정지 (벽·가구 기록은 renderer에 유지)
        DisposableEffect(Unit) {
            scanVisible = true
            startAr()
            onDispose {
                scanVisible = false
                stopAr()
            }
        }

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
                        "테이블" -> Color(0xFFC9DDB9)   // 시안 D 연한 세이지
                        "의자" -> Color.White
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

            // 3층 위쪽: #10 시안 D 상단바 (원형 X + 제목) + #40 단계 표시 + 개발용 정보
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        CircleButton(Glyph.CLOSE, "스캔 그만두기", onClose, fill = Color.White.copy(alpha = 0.16f), tint = Color.White)
                    }
                    Text("1층 스캔", Modifier.align(Alignment.Center), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                StepIndicator(step = if (state.outlineClosed) 2 else 1, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                // 개발용 정보 (추적 상태·평면·모델·추론·두 점 거리)
                Column(
                    Modifier
                        .padding(start = 12.dp, top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    val dev = Color.White.copy(alpha = 0.85f)
                    Text(state.status, color = dev, fontSize = 11.sp)
                    Text("평면: 바닥 ${state.floorPlanes}개 · 벽 ${state.walls.size}개", color = dev, fontSize = 11.sp)
                    Text(modelInfo, color = dev, fontSize = 11.sp)   // ★ 8교시
                    if (state.detectInfo.isNotEmpty()) Text(state.detectInfo, color = dev, fontSize = 11.sp)
                    state.lastDistanceM?.let { Text("두 점 거리: %.2f m".format(it), color = dev, fontSize = 11.sp) }
                }
            }

            // 3층 아래쪽: #10 시안 D 하단 시트. #40: 외곽선 미완료 = 1단계(벽), 완료 = 2단계(가구)
            ScanSheet(state, onDone, Modifier.align(Alignment.BottomCenter))
        }
    }

    @Composable
    private fun ScanSheet(state: ArUiState, onDone: () -> Unit, modifier: Modifier) {
        val wallStep = !state.outlineClosed
        val confirmed = state.mapItems.filter { it.confirmed }
        val dim = Color.White.copy(alpha = 0.75f)
        // 카메라를 최대한 보이게: 하단에 떠 있는 어두운 반투명 패널 (흰 시트 대신)
        Column(
            modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xC71C1714))
                // 패널 빈 곳을 누른 터치가 뒤의 카메라(탭 = 점 찍기)로 새지 않게: 패널이 터치를 받는 대상이 되기만 하면
                // 뒤의 형제(카메라 뷰)에는 전달되지 않음. consume()은 하지 않음 → 하면 버튼이 클릭을 취소함
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }
                .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val walls = state.touchedWalls.size   // 1단계에서 기록한 벽 수 (완료되면 비워짐)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (wallStep) "기록한 벽" else "찾은 테이블", Modifier.padding(bottom = 3.dp), fontSize = 14.sp, color = dim)
                        BigNumber(if (wallStep) "$walls" else "${confirmed.count { it.label == "테이블" }}", color = Color.White, size = 32)
                        Text("개", Modifier.padding(bottom = 2.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        if (wallStep) "화면을 벽에 대고 볼륨 버튼 · 3개부터 완료"
                        else "의자 ${confirmed.count { it.label == "의자" }} · 천천히 걸으며 비춰 주세요",
                        fontSize = 12.sp, color = dim,
                    )
                    Text(state.message, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
                }
                MiniMap(state, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wallStep) {
                    GlassButton("기록", { renderer.outlineOp(OutlineOp.RECORD) }, Modifier.weight(1f))
                    LightButton("벽 완료", { renderer.outlineOp(OutlineOp.CLOSE) }, Modifier.weight(2f), enabled = walls >= 3)
                } else {
                    // [벽 다시] = 완료만 풀고 1단계로 (기록한 벽·인식한 가구는 유지)
                    GlassButton("벽 다시", { renderer.outlineOp(OutlineOp.UNDO) }, Modifier.weight(1f))
                    LightButton("스캔 완료", onDone, Modifier.weight(2f))
                }
            }

            // 보조 동작 (작은 글자)
            // 1단계: [처음부터] = 기록한 벽만 다시 (가구는 아직 없음)
            // 2단계: [스캔 처음부터] = 벽·가구·AR 기준 모두 지움 → 실수 방지로 확인 창
            var confirmReset by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (wallStep) {
                    SmallAction("되돌리기") { renderer.outlineOp(OutlineOp.UNDO) }
                    SmallAction("처음부터") { renderer.outlineOp(OutlineOp.RESET) }
                } else {
                    // #7 검증용: 보정 전 스캔 원본을 내보냄 (보정한 배치는 #15 업로드에서 [저장]으로)
                    SmallAction("스캔 원본 JSON") { exportLayout() }
                    SmallAction("스캔 처음부터") { confirmReset = true }
                }
            }
            if (confirmReset) {
                AlertDialog(
                    onDismissRequest = { confirmReset = false },
                    title = { Text("스캔을 처음부터 할까요?") },
                    text = { Text("기록한 벽과 찾은 테이블·의자가 모두 지워져요.") },
                    confirmButton = {
                        TextButton(onClick = {
                            renderer.clear()
                            confirmReset = false
                        }) { Text("지우기", color = Danger, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmReset = false }) { Text("취소", color = Ink) }
                    },
                    containerColor = Color.White,
                )
            }
        }
    }

    /** 스캔 패널 주요 버튼: 어두운 카메라 위라 흰 알약 + 진한 글자 */
    @Composable
    private fun LightButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(50.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White, contentColor = Ink,
                disabledContainerColor = Color.White.copy(alpha = 0.35f), disabledContentColor = Ink,
            ),
        ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }

    /** 스캔 패널 보조 버튼: 반투명 흰 테두리 알약 */
    @Composable
    private fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(50.dp),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }

    @Composable
    private fun SmallAction(text: String, onClick: () -> Unit) {
        TextButton(onClick = onClick) { Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f)) }
    }

    /** #40: 상단 단계 표시 (시안 D 알약). 지금 단계 = 흰 알약 + 갈색 원, 끝난 단계 = 세이지 원 ✓, 다음 단계 = 흐린 테두리 */
    @Composable
    private fun StepIndicator(step: Int, modifier: Modifier = Modifier) {
        Row(
            modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StepPill(1, "벽", step)
            StepPill(2, "가구", step)
        }
    }

    @Composable
    private fun StepPill(n: Int, label: String, step: Int) {
        val active = n == step
        val done = n < step
        val dim = Color.White.copy(alpha = 0.75f)
        Row(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.Transparent)
                .padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            active -> Modifier.background(Brown)
                            done -> Modifier.background(Sage)
                            else -> Modifier.border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✓" else "$n",
                    color = when {
                        active -> Color.White
                        done -> SageInk
                        else -> dim
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.Black,
                )
            }
            Text(
                label,
                color = if (active) Ink else dim,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        startAr()
    }

    // 그리기 스레드를 "먼저" 멈추고 카메라 반납
    override fun onPause() {
        super.onPause()
        resumed = false
        stopAr()
    }

    /** #10: 스캔 화면이 보이고 resume 상태일 때만 세션 생성·재개 (기존 onResume 본문) */
    private fun startAr() {
        if (!scanVisible || !resumed || !hasCameraPermission()) return

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

    /** #10: 그리기 스레드를 "먼저" 멈추고 카메라 반납 (기존 onPause 본문) */
    private fun stopAr() {
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

    /**
     * #38: 1단계(벽)에서는 볼륨 버튼 = [기록] (화면을 벽에 대고 있어 화면 버튼을 못 누름).
     * #40: 2단계에서는 원래 음량 조절. #10: 스캔 화면이 아니면 원래 음량 조절
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!scanVisible || uiState.outlineClosed || !volume) return super.onKeyDown(keyCode, event)
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
            !state.outlineClosed -> "1단계(벽)를 먼저 완료해 주세요"
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
