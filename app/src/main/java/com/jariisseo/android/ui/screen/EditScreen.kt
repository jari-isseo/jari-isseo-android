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
import com.jariisseo.android.Layout
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.TopView
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.LineIcon
import com.jariisseo.android.ui.PageTitle
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Line
import com.jariisseo.android.ui.theme.Sub

/**
 * #10: 시안 D ⑥ 탑뷰 보정. #13: 스캔한 배치를 탑뷰로 보여주고 테이블 드래그·확대 축소.
 * 테이블 추가·회전은 #14, 벽 맞추기(방 외곽선 꼭짓점)는 #26, 서버 저장(업로드)은 #15 → 지금 [저장]은 이동만
 * layout = null: 아직 스캔하지 않음
 */
@Composable
fun EditScreen(layout: Layout?, onChange: (Layout) -> Unit, onBack: () -> Unit, onSave: () -> Unit) {
    var target by remember { mutableIntStateOf(0) }   // 0 = 테이블 옮기기, 1 = 벽 맞추기 (방 외곽선 꼭짓점, #26)

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

        PageTitle(
            "1층 배치 보정",
            if (target == 0) "테이블을 끌어서 실제 자리에 맞춰 주세요" else "방 모서리 점을 끌어서 실제 벽 위치에 맞춰 주세요",
            Modifier.padding(top = 16.dp),
        )

        // 편집 모드 (명사 대신 동작으로: 테이블 옮기기 / 벽 맞추기)
        Row(
            Modifier
                .padding(start = 20.dp, top = 16.dp)
                .border(1.5.dp, Line, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("테이블 옮기기", "벽 맞추기").forEachIndexed { i, label ->
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

        // 탑뷰 배치도 (남는 높이를 모두 씀)
        Box(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (layout == null) {
                Text("매장을 스캔하면 배치가 여기에 보여요", fontSize = 14.sp, color = Sub)
            } else {
                // 벽 맞추기 모드에서는 테이블이 끌리지 않게 (꼭짓점 편집은 #26)
                TopView(layout, onChange, Modifier.fillMaxSize(), tablesMovable = target == 0)
                Text(
                    "두 손가락으로 확대·축소",
                    Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp),
                    fontSize = 12.sp, color = Sub,
                )
            }
        }

        // 도구 (모양만, #14)
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
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
