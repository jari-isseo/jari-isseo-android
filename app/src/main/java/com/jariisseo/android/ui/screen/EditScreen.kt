package com.jariisseo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.LineIcon
import com.jariisseo.android.ui.PageTitle
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Line
import com.jariisseo.android.ui.theme.Sub

/**
 * #10: 시안 D ⑥ 탑뷰 보정. 배치도 표시·드래그는 #13, 테이블 추가·회전은 #14, 바닥 모양은 #26.
 * 지금은 화면 모양과 [저장] 이동만
 */
@Composable
fun EditScreen(onBack: () -> Unit, onSave: () -> Unit) {
    var target by remember { mutableIntStateOf(0) }   // 0 = 테이블, 1 = 바닥 모양

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Glyph.BACK, "뒤로", onBack)
            Box(
                Modifier.height(44.dp).clip(CircleShape).background(Brown).clickable(onClick = onSave).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("저장", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }

        PageTitle("1층 배치 보정", "테이블을 끌어서 실제 자리에 맞춰 주세요", Modifier.padding(top = 16.dp))

        // 편집 대상 세그먼트 (테이블 / 바닥 모양)
        Row(
            Modifier
                .padding(start = 20.dp, top = 16.dp)
                .border(1.5.dp, Line, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("테이블", "바닥 모양").forEachIndexed { i, label ->
                val selected = i == target
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(if (selected) Brown else Color.Transparent)
                        .clickable { target = i }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else Sub,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // 배치도 자리
        Box(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("스캔한 배치가 여기에 보여요", fontSize = 14.sp, color = Sub)
            Text("아래쪽이 입구", Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp), fontSize = 12.sp, color = Sub)
        }

        // 도구 (모양만)
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
        ) {
            listOf(Glyph.PLUS to "테이블 추가", Glyph.ROTATE to "90° 회전", Glyph.UNDO to "되돌리기").forEach { (glyph, label) ->
                Column(
                    Modifier.clickable {}.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        LineIcon(glyph, Ink)
                    }
                    Text(label, fontSize = 12.sp, color = Brown)
                }
            }
        }
    }
}
