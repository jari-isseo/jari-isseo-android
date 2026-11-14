package com.jariisseo.android.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * #10: 시안 D ④ 홈. 매장 이름·빈자리 수는 서버 연동 후, 층 목록은 #25, 공유는 #22에서 채움.
 * 지금은 "1층" 한 줄과 이동만 (가짜 숫자 대신 "-")
 */
@Composable
fun HomeScreen(onView: () -> Unit, onEdit: () -> Unit, onScan: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 40.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("자리있어", fontSize = 14.sp, color = Sub, fontWeight = FontWeight.Medium)
                    Text("내 매장", fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                CircleButton(Glyph.SHARE, "손님에게 알리기", onClick = {}, fill = Color.White)   // 공유 화면은 #22
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
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardRow("1층", "아직 스캔하지 않았어요", onClick = onEdit)
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
            // 가게 정보 화면(매장 이름 등 수정)은 아직 issue 없음 → 지금은 이동 없음
            Box(Modifier.size(48.dp).semantics { contentDescription = "가게 정보" }.clickable {}, contentAlignment = Alignment.Center) {
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
                .background(Brown)
                .semantics { contentDescription = "층 추가하고 스캔하기" }
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Glyph.PLUS, Color.White, size = 26.dp) }
    }
}
