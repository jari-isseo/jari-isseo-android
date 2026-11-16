package com.jariisseo.android.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jariisseo.android.BuildConfig
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.FloorDto
import com.jariisseo.android.net.MyStore
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.apiCall
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.net.exportViewerUrl
import com.jariisseo.android.share.displayUrl
import com.jariisseo.android.share.qrMatrix
import com.jariisseo.android.share.savePngToGallery
import com.jariisseo.android.share.toBitmap
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.LineIcon
import com.jariisseo.android.ui.PageTitle
import com.jariisseo.android.ui.PillTab
import com.jariisseo.android.ui.PrimaryButton
import com.jariisseo.android.ui.web.ExportWebView
import com.jariisseo.android.ui.web.decodeExportPng
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Chevron
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk
import com.jariisseo.android.ui.theme.Sub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * #22: 시안 D 공유 (홈 오른쪽 위 [손님에게 알리기]). 서버가 준 shareUrl 복사·공유 시트, QR 표시·사진 저장.
 * #21: 아래에 '매장 이미지'(상태 색 없는 이미지 저장, StoreImageSection). 저장 권한·IO 저장은 QR과 같이 씀
 */
@Composable
fun ShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var attempt by remember { mutableIntStateOf(0) }
    var store by remember { mutableStateOf<MyStore?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        loadError = null
        try {
            store = apiCall { Server.api.myStore() }
        } catch (e: ApiException) {
            loadError = e.displayMessage()
        }
    }

    val url = store?.shareUrl
    val qr = remember(url) { url?.let { qrMatrix(it).toBitmap(1024) } }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    /** QR·매장 이미지 공용. what = 토스트 주어 ("QR을", "이미지를") */
    fun savePng(bitmap: Bitmap, name: String, what: String) {
        if (saving) return   // 저장 중 다시 눌러도 한 번만
        saving = true
        scope.launch {
            // PNG 압축·파일 쓰기는 IO 스레드에서, 토스트는 화면 스레드로 돌아와서
            val ok = withContext(Dispatchers.IO) { savePngToGallery(context, bitmap, name) }
            saving = false
            toast(if (ok) "$what 사진에 저장했어요" else "$what 저장하지 못했어요")
        }
    }
    // Android 9 이하만 저장 권한이 필요 (10+는 바로 저장). 허용되면 기다리던 저장(QR 또는 이미지)을 이어서
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = afterPermission
        afterPermission = null
        if (granted) action?.invoke() else toast("사진을 저장하려면 저장 권한을 허용해 주세요")
    }
    fun withSavePermission(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            afterPermission = action
            askPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }
    fun onSaveQr() = withSavePermission { qr?.let { savePng(it, "자리있어_QR_${store?.publicId}", "QR을") } }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 16.dp, top = 24.dp)) { CircleButton(Glyph.BACK, "홈으로", onBack) }
        PageTitle("손님에게\n빈자리를 알려요", modifier = Modifier.padding(top = 16.dp))

        val error = loadError
        when {
            error != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("링크를 불러오지 못했어요\n$error", fontSize = 14.sp, color = Sub)
                PillTab("다시 시도", selected = true) { attempt++ }
            }
            url == null || qr == null -> Text("불러오는 중…", Modifier.padding(24.dp), fontSize = 14.sp, color = Sub)
            else -> {
                // 빈자리 링크: 복사 · 공유 시트
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)).background(Color.White).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("빈자리 링크", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Bg).padding(start = 18.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(displayUrl(url), Modifier.weight(1f), fontSize = 14.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(
                            Modifier.height(40.dp).clip(CircleShape).background(Ink)
                                .clickable {
                                    clipboard.setText(AnnotatedString(url))
                                    // Android 13+는 시스템이 복사 알림을 띄움
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast("링크를 복사했어요")
                                }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("복사", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(send, "빈자리 링크 보내기"))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("카카오톡·인스타그램으로 보내기", Modifier.weight(1f), fontSize = 15.sp)
                        LineIcon(Glyph.CHEVRON, Chevron, size = 16.dp)
                    }
                }

                // 매장 앞에 붙일 QR (화면의 색 덩어리 1개)
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)).background(Sage).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Image(
                        qr.asImageBitmap(), "빈자리 링크 QR 코드",
                        Modifier.size(104.dp).clip(RoundedCornerShape(18.dp)).background(Color.White),
                        filterQuality = FilterQuality.None,   // 칸 경계가 흐려지지 않게
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("매장 앞에 붙일 QR", color = SageInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("찍으면 빈자리 화면이 바로 열려요", color = SageInk, fontSize = 13.sp, lineHeight = 20.sp)
                        Box(
                            Modifier.padding(top = 4.dp).height(40.dp).clip(CircleShape)
                                .border(BorderStroke(1.5.dp, SageInk), CircleShape)
                                .clickable(onClick = ::onSaveQr)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("QR 저장", color = SageInk, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                // #21: 상태 색 없는 매장 이미지
                val current = store
                if (current != null) {
                    StoreImageSection(current) { bitmap, name -> withSavePermission { savePng(bitmap, name, "이미지를") } }
                }
            }
        }
    }
}

private const val ALL_FLOORS = "all"
private const val EXPORT_TIMEOUT_MS = 15_000L

/**
 * #21: 시안 D '매장 이미지'. 층 탭 → 화면 밖 출력 모드 WebView로 상태 색 없는 PNG → 미리보기 → [이미지 저장].
 * 미리보기와 저장은 같은 이미지. 층이 2개 이상이면 "모든 층"(위아래로 한 장). 배치가 없으면 저장할 수 없음
 */
@Composable
private fun StoreImageSection(store: MyStore, onSave: (Bitmap, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var floorsAttempt by remember { mutableIntStateOf(0) }
    var floors by remember { mutableStateOf<List<FloorDto>?>(null) }
    var floorsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store.hasLayout, floorsAttempt) {
        if (!store.hasLayout) return@LaunchedEffect
        floorsError = null
        try {
            floors = apiCall { Server.api.getLayout() }.floors.sortedBy { it.order }
        } catch (e: ApiException) {
            floorsError = e.displayMessage()
        }
    }

    // 탭: 층 id → 이름, 층이 2개 이상이면 "모든 층"
    val tabs = floors?.let { list ->
        list.map { it.id to it.name } + if (list.size > 1) listOf(ALL_FLOORS to "모든 층") else emptyList()
    }.orEmpty()
    var selected by remember(tabs) { mutableStateOf(tabs.firstOrNull()?.first) }
    var exportAttempt by remember { mutableIntStateOf(0) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val floor = selected
    // 탭·[다시 시도]마다 새로 만듦. 웹이 배치를 못 그리면 onExport가 오지 않음 → 시간 제한
    LaunchedEffect(floor, exportAttempt) {
        image = null
        exportFailed = false
        if (floor == null) return@LaunchedEffect
        delay(EXPORT_TIMEOUT_MS)
        if (image == null) exportFailed = true
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("매장 이미지", Modifier.padding(horizontal = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("상태 색 없이 저장돼요. 네이버 플레이스 사진으로 올려보세요.", Modifier.padding(horizontal = 4.dp), fontSize = 13.sp, color = Sub)
        if (tabs.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { (id, name) -> PillTab(name, selected = id == floor) { selected = id } }
            }
        } else {
            tabs.firstOrNull()?.let { (_, name) -> PillTab(name, selected = true) {} }
        }

        Box(
            Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shown = image
            val error = floorsError
            when {
                !store.hasLayout -> Text("스캔을 마치면 저장할 수 있어요", fontSize = 14.sp, color = Sub)
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("층 정보를 불러오지 못했어요\n$error", fontSize = 14.sp, color = Sub, textAlign = TextAlign.Center)
                    PillTab("다시 시도", selected = true) { floorsAttempt++ }
                }
                shown != null -> Image(
                    shown.asImageBitmap(), "상태 색 없는 매장 이미지 미리보기",
                    Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit,
                )
                exportFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("이미지를 만들지 못했어요", fontSize = 14.sp, color = Sub)
                    PillTab("다시 시도", selected = true) { exportAttempt++ }
                }
                else -> Text("이미지를 만드는 중…", fontSize = 14.sp, color = Sub)
            }
            // 화면에 안 보이는 출력 WebView: 이미지가 오면(또는 실패하면) 치움
            if (floor != null && image == null && !exportFailed) {
                key(floor, exportAttempt) {
                    val requested = floor to exportAttempt
                    ExportWebView(
                        url = exportViewerUrl(BuildConfig.BASE_URL, store.publicId, floor),
                        onExport = { base64 ->
                            scope.launch {
                                val bitmap = withContext(Dispatchers.Default) { decodeExportPng(base64) }
                                if (requested != (selected to exportAttempt)) return@launch   // 그새 다른 층을 고름
                                if (bitmap == null) exportFailed = true else image = bitmap
                            }
                        },
                        onError = { if (requested == (selected to exportAttempt)) exportFailed = true },
                    )
                }
            }
        }
    }

    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)) {
        PrimaryButton(
            "이미지 저장",
            {
                val bitmap = image ?: return@PrimaryButton
                val label = tabs.firstOrNull { it.first == floor }?.second ?: ""
                // 파일 이름에 못 쓰는 글자(공백·/ 등)는 _로
                val safe = label.replace(Regex("[^\\p{L}\\p{N}]"), "_")
                onSave(bitmap, "자리있어_매장_${store.publicId}_$safe")
            },
            Modifier.fillMaxWidth(),
            enabled = image != null,
        )
    }
}
