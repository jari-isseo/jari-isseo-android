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
import androidx.compose.material3.Text
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.jariisseo.android.ui.web.revertStatusJs
import kotlinx.coroutines.delay

private enum class ViewerState { LOADING, READY, ERROR }

private const val READY_TIMEOUT_MS = 10_000L

/**
 * #10: 시안 D ⑦ 사장님 뷰. 테이블 탭 전환은 #18, 영업 중/마감·전체 비우기는 #23, 층 탭은 #25.
 * #17: 배치도 = 손님 웹과 같은 페이지를 WebView 사장님 모드로 (내 매장 publicId → /s/{publicId}?mode=owner).
 *   웹이 다 그리면 OwnerBridge.onReady로 로딩 덮개를 걷음. 못 불러오면 에러 + [다시 시도]
 */
@Composable
fun ViewScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }                 // [다시 시도]마다 +1 → 주소부터 다시
    var url by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(ViewerState.LOADING) }
    var errorText by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<Pair<Int, Int>?>(null) }  // 빈 테이블 / 전체 (웹 onSummary)
    fun fail(text: String) {
        state = ViewerState.ERROR
        errorText = text
    }

    LaunchedEffect(attempt) {
        state = ViewerState.LOADING
        url = null
        summary = null
        try {
            val store = apiCall { Server.api.myStore() }
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
            // 영업 중 / 마감 (모양만, 실제 전환은 #23)
            Row(
                Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(role = Role.Switch) { open = !open }
                    .semantics { stateDescription = if (open) "영업 중" else "마감" }
                    .padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (open) "영업 중" else "마감", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (open) StickerCheck()
            }
        }

        Row(Modifier.padding(start = 20.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTab("1층", selected = true, onClick = {})
        }

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
                        onTableTap = { webView, tableId, occupied ->
                            // 상태 전환 API는 #18. 지금은 웹의 흐림만 바로 풀어 둠
                            Log.d("OwnerBridge", "onTableTap $tableId $occupied")
                            webView.evaluateJavascript(revertStatusJs(tableId), null)
                        },
                        onSummary = { free, total -> summary = free to total },
                        onError = { fail("인터넷 연결을 확인해 주세요") },
                        modifier = Modifier.fillMaxSize(),
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

        OutlineButton("1층 전체 비우기", {}, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp))
    }
}
