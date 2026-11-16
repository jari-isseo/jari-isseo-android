package com.jariisseo.android.ui.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.roundToInt

/** #21: 저장 이미지 긴 변 상한 (업로드 제한을 넘지 않게. 작은 이미지는 키우지 않음) */
const val EXPORT_MAX_SIDE = 4096

/**
 * #21: 화면에 안 보이는 출력 모드 WebView (exportViewerUrl). 웹이 상태 색 없는 PNG를 만들면 onExport(base64).
 * 배치가 없으면 웹은 onExport를 보내지 않음 → 기다리는 시간 제한은 부르는 쪽에서.
 * 서버와 다른 사이트로는 이동하지 않음 (브리지가 다른 사이트에 노출되지 않게)
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun ExportWebView(url: String, onExport: (pngBase64: String) -> Unit, onError: () -> Unit) {
    val export by rememberUpdatedState(onExport)
    val error by rememberUpdatedState(onError)

    // 레이아웃에 붙어 있어야 JS가 정상으로 돔 → 1dp·투명으로 둠
    AndroidView(
        modifier = Modifier.size(1.dp).alpha(0f),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    OwnerBridge(
                        onReady = {},
                        onTableTap = { _, _ -> },
                        onSummary = { _, _ -> },
                        post = { main.post(it) },
                        onExport = { export(it) },
                    ),
                    "OwnerBridge",
                )
                val host = Uri.parse(url).host
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                        if (request.isForMainFrame) error()
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame && response.statusCode >= 400) error()
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != host
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

/** 웹이 보낸 base64 PNG → 비트맵 (긴 변이 EXPORT_MAX_SIDE를 넘으면 줄임). 못 읽으면 null. 메인 스레드 밖에서 부를 것 */
fun decodeExportPng(base64: String): Bitmap? {
    val bytes = try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= EXPORT_MAX_SIDE) return bitmap
    val scale = EXPORT_MAX_SIDE.toFloat() / longest
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
}
