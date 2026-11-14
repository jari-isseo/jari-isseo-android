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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * #10: 시안 D ⑦ 사장님 뷰. 배치도(WebView 사장님 모드)는 #17, 테이블 탭 전환은 #18,
 * 영업 중/마감·전체 비우기는 #23, 층 탭은 #25. 지금은 화면 모양과 뒤로 가기만
 */
@Composable
fun ViewScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf(true) }

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

        // 세이지 카드: 이 층 빈자리
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Sage)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("1층 빈자리", color = SageInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                BigNumber("-", color = SageInk, size = 64)
            }
            Text(
                "테이블을 누르면 손님 있음·없음이 바뀌어요",
                Modifier.widthIn(max = 130.dp),
                color = SageInk, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.End,
            )
        }

        // 배치도 자리
        Box(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .fillMaxWidth()
                .height(266.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("매장 배치도가 여기에 보여요", fontSize = 14.sp, color = Sub)
        }

        Spacer(Modifier.weight(1f))
        OutlineButton("1층 전체 비우기", {}, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp))
    }
}
