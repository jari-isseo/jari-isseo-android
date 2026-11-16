package com.jariisseo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.net.ApiException
import com.jariisseo.android.net.Server
import com.jariisseo.android.net.floorNameError
import com.jariisseo.android.net.nextFloorName
import com.jariisseo.android.ui.CircleButton
import com.jariisseo.android.ui.Glyph
import com.jariisseo.android.ui.PageTitle
import com.jariisseo.android.ui.PillTab
import com.jariisseo.android.ui.PrimaryButton
import com.jariisseo.android.ui.theme.Bg
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Sub

/**
 * #25: 시안 D 층 추가 (홈 [+]). 새 층 이름 → [{이름} 스캔 시작].
 * 빈 층은 서버에 저장할 수 없어서, 층은 스캔 후 보정에서 저장할 때 생김.
 * 기본 이름 = 아직 안 쓴 "n층" 중 가장 작은 n (층 목록을 못 불러오면 "1층"부터)
 */
@Composable
fun FloorAddScreen(onBack: () -> Unit, onStart: (name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var suggested by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val names = try {
            Server.layouts.floors().map { it.name }
        } catch (_: ApiException) {
            emptyList()
        }
        val next = nextFloorName(names)
        suggested = next
        if (name.isEmpty()) name = next
    }
    val error = floorNameError(name)
    val n = suggested?.removeSuffix("층")?.toIntOrNull()
    val chips = listOfNotNull("지하 1층", n?.let { "${it}층" }, n?.let { "${it + 1}층" }, "루프탑", "테라스")

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
            Row(Modifier.padding(start = 16.dp, top = 24.dp)) { CircleButton(Glyph.BACK, "홈으로", onBack) }
            PageTitle("새 층 이름을\n정해 주세요", "손님 화면의 층 탭에 이 이름이 보여요", Modifier.padding(top = 16.dp))

            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field("층 이름", name, { name = it }, last = true, onDone = { if (error == null) onStart(name.trim()) })
                error?.let { Text(it, color = Danger, fontSize = 14.sp) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { chip -> PillTab(chip, selected = name.trim() == chip) { name = chip } }
                }
            }

            // 스캔 전에 알아두기
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(Color.White).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("스캔은 이렇게 해요", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Step(1, "벽", "벽마다 화면을 대고 볼륨 버튼을 눌러요")
                Step(2, "가구", "천천히 걸으며 테이블·의자를 비춰요")
                Text("계단에서는 위치가 끊겨요. 한 층씩 따로 스캔해 주세요.", fontSize = 13.sp, color = Sub, lineHeight = 20.sp)
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp)) {
                PrimaryButton(
                    if (error == null) "${name.trim()} 스캔 시작" else "스캔 시작",
                    { onStart(name.trim()) },
                    Modifier.fillMaxWidth(),
                    enabled = error == null,
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Brown), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Text("$title · $text", fontSize = 15.sp, lineHeight = 22.sp)
    }
}
