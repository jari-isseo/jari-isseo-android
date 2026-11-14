package com.jariisseo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jariisseo.android.Layout
import com.jariisseo.android.hitTable
import com.jariisseo.android.moveTable
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Line
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk

private val FloorFill = Color(0xFFEEF2E8)
private val FloorEdge = Color(0xFFB9C7AA)
private val WallColor = Color(0xFF8C8378)
private const val MIN_ZOOM = 0.5f   // 처음 맞춘 크기 대비
private const val MAX_ZOOM = 4f

/**
 * #13: 매장 좌표(m, x 오른쪽 · z 아래) ↔ 화면 픽셀. 순수 클래스라 단위 테스트 가능
 */
data class TopViewTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toScreen(x: Float, z: Float) = Offset(x * scale + offsetX, z * scale + offsetY)
    fun toStore(sx: Float, sy: Float) = ((sx - offsetX) / scale) to ((sy - offsetY) / scale)

    fun panned(dx: Float, dy: Float) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /** 화면 점 (cx, cy) 아래의 매장 위치가 그대로 있도록 확대·축소. 배율은 [minScale, maxScale] */
    fun zoomed(factor: Float, cx: Float, cy: Float, minScale: Float, maxScale: Float): TopViewTransform {
        val s = (scale * factor).coerceIn(minScale, maxScale)
        val (x, z) = toStore(cx, cy)
        return TopViewTransform(s, cx - x * s, cy - z * s)
    }

    companion object {
        /** 점들이 (width × height) 안에 여백 padding을 두고 가운데 오도록. 점이 없으면 1m = 100px */
        fun fit(points: List<Pair<Float, Float>>, width: Float, height: Float, padding: Float): TopViewTransform {
            if (points.isEmpty()) return TopViewTransform(100f, width / 2, height / 2)
            val minX = points.minOf { it.first }
            val maxX = points.maxOf { it.first }
            val minZ = points.minOf { it.second }
            val maxZ = points.maxOf { it.second }
            val bw = (maxX - minX).coerceAtLeast(0.5f)
            val bh = (maxZ - minZ).coerceAtLeast(0.5f)
            // 영역이 작으면(화면 분할 등) 여백을 줄여서 배율이 항상 양수가 되게 (음수면 확대 범위가 뒤집혀 크래시)
            val pad = minOf(padding, width / 4, height / 4)
            val s = minOf((width - 2 * pad) / bw, (height - 2 * pad) / bh)
            return TopViewTransform(s, width / 2 - (minX + maxX) / 2 * s, height / 2 - (minZ + maxZ) / 2 * s)
        }
    }
}

/** 배치에 들어있는 모든 점 (화면 맞춤용) */
private fun Layout.points(): List<Pair<Float, Float>> =
    floor + walls.flatMap { listOf(it.first, it.second) } + tables.map { it.x to it.z } + chairs.map { it.x to it.z }

/**
 * #13: 탑뷰 배치도 (시안 D). 테이블을 끌면 이동, 빈 곳을 끌면 화면 이동, 두 손가락으로 확대·축소.
 * 테이블을 누르면 선택 표시 (편집 시트는 #14)
 */
@Composable
fun TopView(layout: Layout, onChange: (Layout) -> Unit, modifier: Modifier = Modifier, tablesMovable: Boolean = true) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var transform by remember { mutableStateOf<TopViewTransform?>(null) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var selected by remember { mutableStateOf<String?>(null) }
    val current by rememberUpdatedState(layout)
    val change by rememberUpdatedState(onChange)
    val movable by rememberUpdatedState(tablesMovable)
    val textMeasurer = rememberTextMeasurer()
    val padding = with(LocalDensity.current) { 28.dp.toPx() }

    // 처음·화면 크기가 바뀔 때·새 스캔(floorId)일 때 배치 전체가 보이게 맞춤
    LaunchedEffect(size, layout.floorId) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val fit = TopViewTransform.fit(layout.points(), size.width.toFloat(), size.height.toFloat(), padding)
        transform = fit
        baseScale = fit.scale
    }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val t0 = transform ?: return@awaitEachGesture
                    val (sx, sz) = t0.toStore(down.position.x, down.position.y)
                    val hit = if (movable) hitTable(current, sx, sz, slop = 12.dp.toPx() / t0.scale) else null
                    selected = hit?.id
                    // 테이블 위에서 시작 = 테이블 드래그 (잡은 지점과 테이블 중심의 차이 유지)
                    // 빈 곳에서 시작하거나 드래그 중 두 번째 손가락이 닿으면 = 화면 이동·확대·축소
                    var dragging = hit != null
                    val grabX = (hit?.x ?: 0f) - sx
                    val grabZ = (hit?.z ?: 0f) - sz
                    do {
                        val event = awaitPointerEvent()
                        if (dragging && event.changes.count { it.pressed } > 1) dragging = false
                        val t = transform
                        if (dragging && hit != null) {
                            val c = event.changes.firstOrNull { it.id == down.id && it.pressed }
                            if (c != null && t != null) {
                                val (x, z) = t.toStore(c.position.x, c.position.y)
                                change(moveTable(current, hit.id, x + grabX, z + grabZ))
                            }
                        } else if (t != null) {
                            val centroid = event.calculateCentroid()
                            if (centroid != Offset.Unspecified) {
                                val pan = event.calculatePan()
                                transform = t.panned(pan.x, pan.y)
                                    .zoomed(event.calculateZoom(), centroid.x, centroid.y, baseScale * MIN_ZOOM, baseScale * MAX_ZOOM)
                            }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val t = transform ?: return@Canvas

        // 바닥 (연한 세이지 + 테두리)
        if (layout.floor.size >= 3) {
            val path = Path().apply {
                layout.floor.forEachIndexed { i, (x, z) ->
                    val p = t.toScreen(x, z)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, FloorFill)
            drawPath(path, FloorEdge, style = Stroke(width = 2.dp.toPx()))
        }

        // 벽
        layout.walls.forEach { (a, b) ->
            drawLine(WallColor, t.toScreen(a.first, a.second), t.toScreen(b.first, b.second), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }

        // 의자 (작은 원)
        val chairR = (0.2f * t.scale).coerceAtLeast(4.dp.toPx())
        layout.chairs.forEach { drawCircle(Line, chairR, t.toScreen(it.x, it.z)) }

        // 테이블 (회전 반영, 선택 = 세이지 + 링)
        layout.tables.forEach { table ->
            val c = t.toScreen(table.x, table.z)
            val w = table.width * t.scale
            val h = table.depth * t.scale
            val isSelected = table.id == selected
            rotate(table.rotation, pivot = c) {
                val topLeft = Offset(c.x - w / 2, c.y - h / 2)
                val radius = CornerRadius(minOf(w, h) * 0.18f)
                if (isSelected) {
                    val ring = 5.dp.toPx()
                    drawRoundRect(
                        Sage.copy(alpha = 0.35f), Offset(topLeft.x - ring, topLeft.y - ring), Size(w + 2 * ring, h + 2 * ring),
                        CornerRadius(radius.x + ring),
                    )
                }
                val fill = if (isSelected) Sage else Brown
                if (table.shape == "ROUND") drawOval(fill, topLeft, Size(w, h)) else drawRoundRect(fill, topLeft, Size(w, h), radius)
            }
            val label = textMeasurer.measure(
                table.name.removeSuffix("번"),
                TextStyle(color = if (isSelected) SageInk else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black),
            )
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
        }
    }
}
