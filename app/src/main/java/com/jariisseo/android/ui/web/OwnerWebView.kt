package com.jariisseo.android.ui.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jariisseo.android.BuildConfig

/**
 * #17: 사장님 모드 웹 뷰어 (손님 웹과 같은 페이지, mode=owner).
 * - JavaScript·localStorage 허용 (웹이 마지막으로 본 층을 localStorage에 둠)
 * - OwnerBridge 등록, 탭한 테이블은 onTableTap(webView, tableId, occupied) — 응답(apply/revert)은 호출한 쪽이 webView로
 * - 빈자리 요약은 onSummary(free, total)로 받음 (웹은 사장님 모드에서 요약 문구를 숨김, server#49)
 * - 페이지 자체를 못 불러오면(연결 실패·4xx·5xx) onError
 * - 서버와 다른 사이트는 WebView에서 열지 않음 (브리지가 다른 사이트에 노출되지 않게) → 외부 브라우저로
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun OwnerWebView(
    url: String,
    onReady: () -> Unit,
    onTableTap: (webView: WebView, tableId: String, occupied: Boolean) -> Unit,
    onSummary: (free: Int, total: Int) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    onCreated: (WebView) -> Unit = {},   // #23: 탭 말고도(전체 비우기) 웹에 반영할 때 쓰도록 WebView를 넘김
) {
    val ready by rememberUpdatedState(onReady)
    val tap by rememberUpdatedState(onTableTap)
    val summary by rememberUpdatedState(onSummary)
    val error by rememberUpdatedState(onError)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)   // debug: chrome://inspect 로 웹 콘솔 확인
            WebView(context).apply {
                // 기본(WRAP_CONTENT)이면 WebView 높이가 내용 높이를 따라가서 웹의 vh가 0에 가까워짐
                //   → 배치도 SVG의 max-height: 70vh 가 0이 되어 그림이 안 보임. 자리 크기에 맞춤
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    OwnerBridge(
                        onReady = { ready() },
                        onTableTap = { id, occupied -> tap(this, id, occupied) },
                        onSummary = { free, total -> summary(free, total) },
                        post = { main.post(it) },
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

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.host == host) return false
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        } catch (e: ActivityNotFoundException) {
                            Log.w(TAG, "외부 링크를 열 앱이 없음: ${request.url}")
                        }
                        return true
                    }
                }
                loadUrl(url)
            }.also(onCreated)
        },
        // [다시 시도] 등으로 주소가 같아도 다시 부를 땐 key로 새로 만듦 (호출하는 쪽)
        onRelease = { it.destroy() },
    )
}

private const val TAG = "OwnerWebView"
