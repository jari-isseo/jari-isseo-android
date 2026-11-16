package com.jariisseo.android.ui.web

import android.webkit.JavascriptInterface
import kotlinx.serialization.json.JsonPrimitive

/**
 * #17: 웹 → 앱 브리지 (API 정의서 6장, server `static/viewer/bridge.js`). WebView에 "OwnerBridge"로 등록.
 * JS 스레드에서 불리므로 post로 UI 스레드에 넘김 (실제는 메인 Handler, 테스트는 바로 실행)
 */
class OwnerBridge(
    private val onReady: () -> Unit,
    private val onTableTap: (tableId: String, occupied: Boolean) -> Unit,
    private val onSummary: (free: Int, total: Int) -> Unit,
    private val post: (() -> Unit) -> Unit,
    private val onExport: (pngBase64: String) -> Unit = {},   // #21: 출력 모드(ExportWebView)만 씀
) {
    /** 지금 보이는 층의 빈 테이블 수 / 전체 (server#49: 처음 그릴 때·상태 바뀔 때·층 바꿀 때) */
    @JavascriptInterface
    fun onSummary(free: Int, total: Int) = post { onSummary.invoke(free, total) }

    /** 처음 그리기 완료 */
    @JavascriptInterface
    fun onReady() = post { onReady.invoke() }

    /** 사장님이 테이블을 탭 (occupied = 탭하기 전 지금 상태). 웹은 앱이 apply/revert 할 때까지 그 테이블을 흐리게 둠 */
    @JavascriptInterface
    fun onTableTap(tableId: String, occupied: Boolean) = post { onTableTap.invoke(tableId, occupied) }

    /** 출력 모드 PNG (base64, data: 접두사 없음). #21: 공유 화면 매장 이미지 */
    @JavascriptInterface
    fun onExport(pngBase64: String) = post { onExport.invoke(pngBase64) }
}

/** 앱 → 웹: 상태를 바꾸지 않고 흐림만 풀기. tableId는 JSON 문자열로 넣어 따옴표·특수문자를 이스케이프 */
fun revertStatusJs(tableId: String): String = "window.revertStatus(${JsonPrimitive(tableId)})"

/** #18 앱 → 웹: API 성공 → 서버가 확정한 상태로 반영 */
fun applyStatusJs(tableId: String, occupied: Boolean): String =
    "window.applyStatus(${JsonPrimitive(tableId)}, $occupied)"
