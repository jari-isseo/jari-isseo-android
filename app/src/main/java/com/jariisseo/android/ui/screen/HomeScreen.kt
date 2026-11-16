package com.jariisseo.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import com.jariisseo.android.net.FloorDto
import com.jariisseo.android.net.FloorTarget
import com.jariisseo.android.net.MAX_FLOORS
import com.jariisseo.android.net.displayMessage
import com.jariisseo.android.ui.PillTab
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.apiCall
import com.jariisseo.android.ui.BigNumber
import com.jariisseo.android.ui.CardRow
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.LineIcon
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Chevron
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk
import com.jariisseo.android.ui.theme.Sub

/** #16: ARCore 미지원 기기 안내 */
private const val NO_AR_MESSAGE = "이 기기는 AR 스캔을 지원하지 않아요 · 데모 배치로 둘러보세요"

/**
 * #10: 시안 D ④ 홈. 빈자리 수는 서버 연동 후 (가짜 숫자 대신 "-")
 * #42: 제목 = 서버의 매장 이름 (불러오기 전·실패 시 "내 매장"). 가게 정보에서 돌아오면 다시 불러옴
 * #22: 오른쪽 위 [손님에게 알리기] → 공유 화면
 * #25: 층 목록 = 서버 배치의 층 (이름 · 테이블 수). 행을 누르면 층 관리 시트, [+] = 층 추가 (10개까지)
 * #16: scanSupported = false(ARCore 미지원)면 [+]·[첫 층 스캔하기]·[다시 스캔]을 막고 안내 (보정·상태 관리는 그대로)
 */
@Composable
fun HomeScreen(
    onView: () -> Unit,
    onEditFloor: (FloorTarget) -> Unit,
    onRescanFloor: (FloorTarget) -> Unit,
    onAddFloor: () -> Unit,
    onStore: () -> Unit,
    onShare: () -> Unit,
    scanSupported: Boolean = true,
) {
    val context = LocalContext.current
    var storeName by remember { mutableStateOf<String?>(null) }
    var floorsAttempt by remember { mutableIntStateOf(0) }   // 층 관리 후·[다시 시도]마다 +1
    var floors by remember { mutableStateOf<List<FloorDto>?>(null) }
    var floorsError by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<FloorDto?>(null) }   // 관리 시트의 층
    LaunchedEffect(Unit) {
        // 401은 AuthInterceptor가 로그인으로 보냄, 그 밖의 실패는 "내 매장" 그대로
        try {
            storeName = apiCall { Server.api.myStore() }.name
        } catch (_: ApiException) {
        }
    }
    LaunchedEffect(floorsAttempt) {
        floorsError = null
        try {
            floors = Server.layouts.floors()
        } catch (e: ApiException) {
            floorsError = e.displayMessage()
        }
    }
    fun addFloor() {
        if (!scanSupported) {
            Toast.makeText(context, NO_AR_MESSAGE, Toast.LENGTH_SHORT).show()
        } else if ((floors?.size ?: 0) >= MAX_FLOORS) {
            Toast.makeText(context, "층은 ${MAX_FLOORS}개까지 만들 수 있어요", Toast.LENGTH_SHORT).show()
        } else {
            onAddFloor()
        }
    }

    managing?.let { floor ->
        val list = floors.orEmpty()
        FloorSheet(
            floor = floor,
            index = list.indexOfFirst { it.id == floor.id },
            count = list.size,
            onDismiss = { managing = null },
            onEdit = { managing = null; onEditFloor(FloorTarget.Existing(floor.id, floor.name)) },
            onRescan = { managing = null; onRescanFloor(FloorTarget.Existing(floor.id, floor.name)) },
            onChanged = { managing = null; floorsAttempt++ },
            scanSupported = scanSupported,
        )
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 40.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("자리있어", fontSize = 14.sp, color = Sub, fontWeight = FontWeight.Medium)
                    Text(
                        storeName ?: "내 매장", fontSize = 32.sp, fontWeight = FontWeight.Black,
                        lineHeight = 40.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                CircleButton(Glyph.SHARE, "손님에게 알리기", onClick = onShare, fill = Color.White)
            }

            // 세이지 카드: 지금 빈자리 (화면의 색 덩어리 1개)
            Column(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Sage)
                    .clickable(onClick = onView)
                    .padding(24.dp),
            ) {
                Text("지금 빈자리", color = SageInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                BigNumber("-", color = SageInk, size = 96)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("스캔을 마치면 여기에 보여요", Modifier.weight(1f), color = SageInk, fontSize = 14.sp)
                    Row(
                        Modifier.height(48.dp).clip(CircleShape).background(Brown).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("상태 관리", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        LineIcon(Glyph.ARROW, Color.White, size = 16.dp)
                    }
                }
            }

            Text("층", Modifier.padding(start = 24.dp, top = 28.dp, bottom = 10.dp), fontSize = 20.sp, fontWeight = FontWeight.Black)
            // 하단 메뉴에 가리지 않게 아래 여백, 층이 많으면 스크롤
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val list = floors
                val error = floorsError
                when {
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("층을 불러오지 못했어요\n$error", fontSize = 14.sp, color = Sub)
                        PillTab("다시 시도", selected = true) { floorsAttempt++ }
                    }
                    list == null -> Text("층을 불러오는 중…", fontSize = 14.sp, color = Sub)
                    list.isEmpty() -> CardRow(
                        "첫 층 스캔하기",
                        if (scanSupported) "아직 스캔한 층이 없어요" else NO_AR_MESSAGE,
                        onClick = ::addFloor,
                    )
                    else -> list.forEach { floor ->
                        CardRow(floor.name, "테이블 ${floor.tables.size}개", onClick = { managing = floor })
                    }
                }
            }
        }

        // 하단 메뉴: 홈 · (+ 층 추가하고 스캔) · 가게 정보
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(12.dp, CircleShape, ambientColor = Brown, spotColor = Brown)
                .clip(CircleShape)
                .background(Color.White),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).semantics { contentDescription = "홈" }, contentAlignment = Alignment.Center) {
                LineIcon(Glyph.HOME, Ink, size = 24.dp)
            }
            Spacer(Modifier.size(60.dp))
            Box(Modifier.size(48.dp).semantics { contentDescription = "가게 정보" }.clickable(onClick = onStore), contentAlignment = Alignment.Center) {
                LineIcon(Glyph.STORE, Chevron, size = 24.dp)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-58).dp)
                .size(60.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(if (scanSupported) Brown else Brown.copy(alpha = 0.35f))   // #16: 미지원이면 흐리게 (누르면 안내)
                .semantics { contentDescription = "층 추가하고 스캔하기" }
                .clickable(onClick = ::addFloor),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Glyph.PLUS, Color.White, size = 26.dp) }
    }
}
