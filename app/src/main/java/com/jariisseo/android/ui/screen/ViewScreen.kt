package com.jariisseo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.app.Activity
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.BuildConfig
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.UpdateStatusRequest
import com.jariisseo.android.net.UpdateStoreRequest
import com.jariisseo.android.net.apiCall
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.net.ownerViewerUrl
import com.jariisseo.android.ui.BigNumber
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.OutlineButton
import com.jariisseo.android.ui.PillTab
import com.jariisseo.android.ui.StickerCheck
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk
import com.jariisseo.android.ui.theme.Sub
import com.jariisseo.android.ui.web.OwnerWebView
import com.jariisseo.android.ui.web.applyStatusJs
import com.jariisseo.android.ui.web.revertStatusJs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class ViewerState { LOADING, READY, ERROR }

private const val READY_TIMEOUT_MS = 10_000L

/**
 * #10: 시안 D ⑦ 사장님 뷰. #25: 층 탭은 웹이 그림.
 * #17: 배치도 = 손님 웹과 같은 페이지를 WebView 사장님 모드로 (내 매장 publicId → /s/{publicId}?mode=owner).
 *   웹이 다 그리면 OwnerBridge.onReady로 로딩 덮개를 걷음. 못 불러오면 에러 + [다시 시도]
 * #18: 테이블 탭 → PUT 상태 전환 → 성공 applyStatus / 실패 revertStatus + 토스트
 * #19: 카운터 태블릿용 — 화면 꺼짐 방지, 가로 화면 가능
 * #23: 영업 중/마감 토글 → PATCH open (먼저 바꿔 보이고 실패 시 되돌림). 손님 웹은 마감이면 '영업 종료'
 *   [전체 비우기] → 확인 → 모든 층 테이블을 손님 없음으로 → 응답대로 배치도에 바로 반영 (손님 웹은 SSE)
 */
@Composable
fun ViewScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf<Boolean?>(null) }          // 서버 값 (불러오기 전 null)
    var togglingOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val webView = remember { arrayOf<WebView?>(null) }               // 전체 비우기 결과를 웹에 반영할 때
    var attempt by remember { mutableIntStateOf(0) }                 // [다시 시도]마다 +1 → 주소부터 다시
    var url by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(ViewerState.LOADING) }
    var errorText by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<Pair<Int, Int>?>(null) }  // 빈 테이블 / 전체 (웹 onSummary)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableSetOf<String>() }                // #18: 상태 전환 요청 중인 테이블
    val skipReset = remember { mutableSetOf<String>() }              // #23: 비우기 중 탭한 테이블 → 비우기 결과로 덮지 않음

    // #19: 뷰에 있는 동안 화면 꺼짐 방지 (나가면 해제)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    fun fail(text: String) {
        state = ViewerState.ERROR
        errorText = text
    }

    LaunchedEffect(attempt) {
        state = ViewerState.LOADING
        url = null
        summary = null
        // [다시 시도] 때 토글 저장(PATCH)이 진행 중이면 끝나길 기다림 → 늦게 온 GET이 토글 결과를 옛 값으로 덮지 않게.
        //   불러오는 동안은 토글을 "확인 중"으로 막음
        snapshotFlow { togglingOpen }.first { !it }
        open = null
        try {
            val store = apiCall { Server.api.myStore() }
            open = store.open
            url = ownerViewerUrl(BuildConfig.BASE_URL, store.publicId, BuildConfig.VIEWER_SAMPLE)
        } catch (e: ApiException) {
            fail(e.displayMessage())
            return@LaunchedEffect
        }
        // 웹 안에서 데이터 불러오기가 실패하면 onReady가 오지 않음 → 일정 시간 뒤 에러로
        delay(READY_TIMEOUT_MS)
        if (state == ViewerState.LOADING) fail("배치도를 그리지 못했어요")
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Glyph.BACK, "홈으로", onBack)
            // 영업 중 / 마감 (#23)
            val label = when (open) {
                true -> "영업 중"
                false -> "마감"
                null -> "확인 중"
            }
            Row(
                Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(role = Role.Switch, enabled = open != null) {
                        val before = open ?: return@clickable
                        if (togglingOpen) return@clickable   // 요청 중엔 무시
                        togglingOpen = true
                        open = !before
                        scope.launch {
                            try {
                                open = apiCall { Server.api.updateStore(UpdateStoreRequest(open = !before)) }.open
                            } catch (e: ApiException) {
                                open = before
                                Toast.makeText(context, e.displayMessage(), Toast.LENGTH_SHORT).show()
                            } finally {
                                togglingOpen = false
                            }
                        }
                    }
                    .semantics { stateDescription = label }
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (open == true) StickerCheck()
            }
        }

        // #25: 층 이동은 웹의 층 탭 (사장님 모드도 층이 2개 이상이면 보임) → 앱에는 층 탭 없음

        // 빈자리 요약 (작은 세이지 카드). 숫자는 웹이 OwnerBridge.onSummary로 알려줌 (server#49 전에는 "-").
        //   웹은 사장님 모드에서 배치도만 그리고 요약 문구는 숨김 → 배치도를 크게
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Sage)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // 숫자는 웹에 지금 보이는 층 기준 (웹이 마지막으로 본 층을 기억) → 층 번호를 붙이지 않음 (층 이름은 #25)
                Text("빈자리", color = SageInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val s = summary
                BigNumber(s?.first?.toString() ?: "-", total = s?.second?.toString(), color = SageInk, size = 36)
            }
            Text(
                "테이블을 누르면\n손님 있음·없음이 바뀌어요",
                color = SageInk, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.End,
            )
        }

        // 배치도 (WebView 사장님 모드, 남는 높이를 모두 씀)
        Box(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val current = url
            if (current != null && state != ViewerState.ERROR) {
                key(attempt) {
                    OwnerWebView(
                        url = current,
                        onReady = { if (state == ViewerState.LOADING) state = ViewerState.READY },
                        onTableTap = onTableTap@{ webView, tableId, occupied ->
                            if (!pending.add(tableId)) return@onTableTap   // 요청 중인 테이블은 무시
                            if (resetting) skipReset.add(tableId)
                            scope.launch {
                                try {
                                    val res = apiCall { Server.api.changeStatus(tableId, UpdateStatusRequest(!occupied)) }
                                    webView.evaluateJavascript(applyStatusJs(tableId, res.occupied), null)
                                } catch (e: ApiException) {
                                    webView.evaluateJavascript(revertStatusJs(tableId), null)
                                    Toast.makeText(context, e.displayMessage(), Toast.LENGTH_SHORT).show()
                                } finally {
                                    pending.remove(tableId)
                                }
                            }
                        },
                        onSummary = { free, total -> summary = free to total },
                        onError = { fail("인터넷 연결을 확인해 주세요") },
                        modifier = Modifier.fillMaxSize(),
                        onCreated = { webView[0] = it },
                    )
                }
            }
            when (state) {
                ViewerState.LOADING -> Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Text("배치도를 불러오는 중…", fontSize = 14.sp, color = Sub)
                }
                ViewerState.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("배치도를 불러오지 못했어요", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(errorText, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = Sub, textAlign = TextAlign.Center)
                    Box(Modifier.padding(top = 14.dp)) { PillTab("다시 시도", selected = true) { attempt++ } }
                }
                ViewerState.READY -> Unit
            }
        }

        // #23: 서버 API가 매장 전체 단위라 "1층"이 아니라 모든 층
        OutlineButton(
            if (resetting) "비우는 중…" else "전체 비우기",
            { if (!resetting) confirmReset = true },
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("전체 비우기") },
            text = { Text("모든 층의 테이블을 '손님 없음'으로 바꿀까요?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    resetting = true
                    // 이미 요청 중이거나 비우는 동안 탭한 테이블은 비우기보다 나중에 처리될 수 있음 → 그 탭 결과·SSE에 맡김
                    skipReset.clear()
                    skipReset.addAll(pending)
                    scope.launch {
                        try {
                            val res = apiCall { Server.api.resetStatuses() }
                            // 손님 웹은 SSE로 바뀜. 내 배치도는 응답대로 바로 (웹이 아직 없으면 SSE에 맡김)
                            webView[0]?.takeIf { state == ViewerState.READY }?.let { w ->
                                res.statuses.filter { it.tableId !in skipReset }
                                    .forEach { w.evaluateJavascript(applyStatusJs(it.tableId, it.occupied), null) }
                            }
                            Toast.makeText(context, "모든 테이블을 비웠어요", Toast.LENGTH_SHORT).show()
                        } catch (e: ApiException) {
                            Toast.makeText(context, e.displayMessage(), Toast.LENGTH_SHORT).show()
                        } finally {
                            resetting = false
                        }
                    }
                }) { Text("비우기") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
}
