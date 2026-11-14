package com.jariisseo.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.CheckEdge
import com.jariisseo.android.ui.theme.CheckGreen
import com.jariisseo.android.ui.theme.Chevron
import com.jariisseo.android.ui.theme.Ink
import com.jariisseo.android.ui.theme.Line
import com.jariisseo.android.ui.theme.Sub

// #10: 시안 D에서 반복되는 요소만 모음

/** 주요 버튼: 갈색 알약 (화면당 1개) */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(58.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Brown, contentColor = Color.White,
            disabledContainerColor = Brown.copy(alpha = 0.35f), disabledContentColor = Color.White,
        ),
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

/** 보조 버튼: 테두리 알약 */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

/** 알약 탭·칩: 선택 = 갈색 채움, 아니면 테두리 */
@Composable
fun PillTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Brown) else Modifier.border(1.5.dp, Line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Brown,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 원형 아이콘 버튼 (48dp). fill = null이면 테두리만 */
@Composable
fun CircleButton(glyph: Glyph, description: String, onClick: () -> Unit, fill: Color? = null, tint: Color = Ink) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier.border(1.5.dp, Line, CircleShape))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(glyph, tint, size = 20.dp) }
}

/** 큰 숫자 + "/ 전체" (값이 없으면 "-") */
@Composable
fun BigNumber(value: String, total: String? = null, color: Color = Ink, size: Int = 72) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, lineHeight = size.sp)
        if (total != null) {
            Text("/ $total", Modifier.padding(bottom = (size / 9).dp), color = color, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 흰 카드 행 (72dp, 둥글기 22): 제목 + 회색 부제 + 오른쪽 내용 + 화살표 */
@Composable
fun CardRow(title: String, sub: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 13.sp, color = Sub)
        }
        trailing()
        LineIcon(Glyph.CHEVRON, Chevron, size = 16.dp)
    }
}

/** 화면 제목 (굵게) + 회색 설명 */
@Composable
fun PageTitle(title: String, sub: String? = null, modifier: Modifier = Modifier, size: Int = 30) {
    Column(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = size.sp, fontWeight = FontWeight.Black, lineHeight = (size * 1.3).sp)
        if (sub != null) Text(sub, fontSize = 14.sp, color = Sub)
    }
}

enum class Glyph { BACK, CLOSE, CHEVRON, PLUS, ARROW, HOME, SHARE, STORE, ROTATE, UNDO, TABLE }

/** 선으로 그린 아이콘 (24 단위 좌표계, 아이콘 라이브러리 없이) */
@Composable
fun LineIcon(glyph: Glyph, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 2.2f * s
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = w, cap = StrokeCap.Round)
        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) = drawArc(
            color, start, sweep, useCenter = false,
            topLeft = Offset((cx - r) * s, (cy - r) * s), size = Size(2 * r * s, 2 * r * s),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
        when (glyph) {
            Glyph.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Glyph.CHEVRON -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
            Glyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Glyph.ARROW -> { line(5f, 12f, 19f, 12f); line(13f, 6f, 19f, 12f); line(19f, 12f, 13f, 18f) }
            Glyph.HOME -> {
                line(3f, 11f, 12f, 4f); line(12f, 4f, 21f, 11f)
                line(5f, 10f, 5f, 20f); line(19f, 10f, 19f, 20f); line(5f, 20f, 19f, 20f)
                line(10f, 20f, 10f, 14f); line(10f, 14f, 14f, 14f); line(14f, 14f, 14f, 20f)
            }
            Glyph.SHARE -> {
                line(12f, 15f, 12f, 4f); line(8f, 8f, 12f, 4f); line(12f, 4f, 16f, 8f)
                line(5f, 12f, 5f, 19f); line(5f, 19f, 19f, 19f); line(19f, 19f, 19f, 12f)
            }
            Glyph.STORE -> {
                line(5.5f, 4f, 18.5f, 4f); line(5.5f, 4f, 4f, 9f); line(18.5f, 4f, 20f, 9f); line(4f, 9f, 20f, 9f)
                line(5f, 12f, 5f, 20f); line(19f, 12f, 19f, 20f); line(5f, 20f, 19f, 20f)
                line(10f, 20f, 10f, 16f); line(10f, 16f, 14f, 16f); line(14f, 16f, 14f, 20f)
            }
            Glyph.ROTATE -> { arc(12f, 12f, 8f, 0f, 315f); line(20f, 4f, 20f, 9f); line(20f, 9f, 15f, 9f) }
            Glyph.UNDO -> { line(9f, 14f, 4f, 9f); line(4f, 9f, 9f, 4f); line(4f, 9f, 14f, 9f); arc(14f, 15f, 6f, -90f, 180f); line(14f, 21f, 11f, 21f) }
            Glyph.TABLE -> { line(4f, 10f, 20f, 10f); line(6f, 10f, 6f, 19f); line(18f, 10f, 18f, 19f); line(8f, 6f, 16f, 6f) }
        }
    }
}

/** '영업 중' 체크 (흰 테두리 + 진한 초록 테두리 + 초록, 스티커 느낌) */
@Composable
fun StickerCheck(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 32.dp, height = 28.dp)) {
        val s = this.size.width / 32f
        val a = Offset(6f * s, 15f * s)
        val b = Offset(13f * s, 22f * s)
        val c = Offset(26f * s, 6f * s)
        // 아래 층부터: 그림자 → 흰 테두리 → 진한 초록 테두리 → 초록
        listOf(
            Triple(Ink.copy(alpha = 0.22f), 12f, Offset(1.5f * s, 2f * s)),
            Triple(Color.White, 12f, Offset.Zero),
            Triple(CheckEdge, 8f, Offset.Zero),
            Triple(CheckGreen, 5.5f, Offset.Zero),
        ).forEach { (color, width, d) ->
            drawLine(color, a + d, b + d, strokeWidth = width * s, cap = StrokeCap.Round)
            drawLine(color, b + d, c + d, strokeWidth = width * s, cap = StrokeCap.Round)
        }
    }
}
