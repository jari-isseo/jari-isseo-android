package com.jariisseo.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.jariisseo.android.CHAIR_RADIUS_M
import com.jariisseo.android.Layout
import com.jariisseo.android.floorEdgeMids
import com.jariisseo.android.floorError
import com.jariisseo.android.hitChair
import com.jariisseo.android.hitFloorEdgeMid
import com.jariisseo.android.hitFloorPoint
import com.jariisseo.android.hitTable
import com.jariisseo.android.moveChair
import com.jariisseo.android.moveFloorPoint
import com.jariisseo.android.moveTable
import com.jariisseo.android.normalizeOrigin
import com.jariisseo.android.tablesOutsideFloor
import com.jariisseo.android.ui.theme.Brown
import com.jariisseo.android.ui.theme.Danger
import com.jariisseo.android.ui.theme.Line
import com.jariisseo.android.ui.theme.Sage
import com.jariisseo.android.ui.theme.SageInk

private val FloorFill = Color(0xFFEEF2E8)
private val FloorEdge = Color(0xFFB9C7AA)
private val WallColor = Color(0xFF8C8378)
private const val MIN_ZOOM = 0.5f   // 처음 맞춘 크기 대비
private const val MAX_ZOOM = 4f
private const val FOCUS_ZOOM = 2f   // #14: 편집 시트를 열 때 확대 (처음 맞춘 크기 대비)
const val FOCUS_ANIM_MS = 300

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
fun Layout.points(): List<Pair<Float, Float>> =
    floor + walls.flatMap { listOf(it.first, it.second) } + tables.map { it.x to it.z } + chairs.map { it.x to it.z }

/**
 * #13: 탑뷰 배치도 (시안 D). 테이블을 끌면 이동, 빈 곳을 끌면 화면 이동, 두 손가락으로 확대·축소.
 * #14: 의자도 끌어서 이동, 테이블 탭 = onTableTap(편집 시트), 테이블·의자 길게 누름 = onDelete.
 * 끌기를 시작할 때 onEditStart 한 번 (되돌리기 단위). fitKey가 바뀌면 배치 전체가 보이게 다시 맞춤.
 * focusId: 편집 시트가 아래를 가리는 동안 그 테이블을 위쪽(높이 1/4)으로 옮기며 확대하고 선택 표시 (애니메이션)
 * #26: floorEditable이면 테이블·의자 대신 바닥 꼭짓점을 끌어서 이동, 변 가운데(+) 탭 = onFloorPointAdd,
 *   꼭짓점 길게 누름 = onFloorPointDelete. 꼬인 바닥은 빨간 테두리, 바닥 밖 테이블은 빨간 링 (두 모드 모두)
 */
@Composable
fun TopView(
    layout: Layout,
    onChange: (Layout) -> Unit,
    modifier: Modifier = Modifier,
    tablesMovable: Boolean = true,
    fitKey: Any = layout.floorId,
    focusId: String? = null,
    onEditStart: () -> Unit = {},
    onTableTap: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    floorEditable: Boolean = false,
    onFloorPointAdd: (Int) -> Unit = {},
    onFloorPointDelete: (Int) -> Unit = {},
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var transform by remember { mutableStateOf<TopViewTransform?>(null) }
    var baseScale by remember { mutableFloatStateOf(1f) }
    var selected by remember { mutableStateOf<String?>(null) }
    val current by rememberUpdatedState(layout)
    val change by rememberUpdatedState(onChange)
    val movable by rememberUpdatedState(tablesMovable)
    val editStart by rememberUpdatedState(onEditStart)
    val tableTap by rememberUpdatedState(onTableTap)
    val delete by rememberUpdatedState(onDelete)
    val floorEdit by rememberUpdatedState(floorEditable)
    val pointAdd by rememberUpdatedState(onFloorPointAdd)
    val pointDelete by rememberUpdatedState(onFloorPointDelete)
    val textMeasurer = rememberTextMeasurer()
    val padding = with(LocalDensity.current) { 28.dp.toPx() }

    // focusId가 생기면 0 → 1, 없어지면 1 → 0 (편집 시트·제목 숨김과 같은 길이). 닫힐 때도 그 테이블 기준으로 돌아가게 기억
    val focusProgress by animateFloatAsState(if (focusId != null) 1f else 0f, tween(FOCUS_ANIM_MS), label = "focus")
    var focusTableId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusId) { if (focusId != null) focusTableId = focusId }

    // 처음·화면 크기가 바뀔 때·새 스캔(floorId)·배치 회전(fitKey)일 때 배치 전체가 보이게 맞춤.
    // 포커스 중이면 맞춘 화면에서 그 테이블을 위쪽(높이 1/4)으로 옮기며 확대 (진행도만큼 섞음)
    LaunchedEffect(size, fitKey, focusTableId, focusProgress) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val fit = TopViewTransform.fit(current.points(), w, h, padding)
        baseScale = fit.scale
        val focus = current.tables.firstOrNull { it.id == focusTableId }
        if (focus == null || focusProgress == 0f) {
            transform = fit
            return@LaunchedEffect
        }
        // 확대: 2배까지, 테이블이 화면 폭 1/3을 넘지 않게, 처음보다 작아지지는 않게
        val zoom = minOf(fit.scale * FOCUS_ZOOM, w / 3 / maxOf(focus.width, focus.depth)).coerceAtLeast(fit.scale)
        val scale = fit.scale + (zoom - fit.scale) * focusProgress
        val from = fit.toScreen(focus.x, focus.z)
        val tx = from.x + (w / 2 - from.x) * focusProgress
        val ty = from.y + (h / 4 - from.y) * focusProgress
        transform = TopViewTransform(scale, tx - focus.x * scale, ty - focus.z * scale)
    }

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val t0 = transform ?: return@awaitEachGesture
                    val (sx, sz) = t0.toStore(down.position.x, down.position.y)
                    val slop = 12.dp.toPx() / t0.scale
                    val table = if (movable && !floorEdit) hitTable(current, sx, sz, slop) else null
                    val chair = if (movable && !floorEdit && table == null) hitChair(current, sx, sz, slop) else null
                    // #26: 꼭짓점·변 가운데는 손가락으로 잡기 쉽게 더 넓게
                    val pointSlop = 20.dp.toPx() / t0.scale
                    val point = if (floorEdit) hitFloorPoint(current, sx, sz, pointSlop) else null
                    val mid = if (floorEdit && point == null) hitFloorEdgeMid(current, sx, sz, pointSlop) else null
                    val hitId = table?.id ?: chair?.id
                    selected = hitId
                    // 테이블·의자·꼭짓점 위에서 시작 = 그 대상 (잡은 지점과 중심의 차이 유지)
                    //   그대로 떼면 탭, 그대로 오래 누르면 삭제, touchSlop 넘게 움직이면 끌기
                    // 변 가운데(+)는 탭만 (끌면 화면 이동)
                    // 빈 곳에서 시작하거나 두 번째 손가락이 닿으면 = 화면 이동·확대·축소
                    val grabbed = hitId != null || point != null
                    var dragging = grabbed
                    var moved = false
                    val pointAt = point?.let { current.floor[it] }
                    val grabX = (table?.x ?: chair?.x ?: pointAt?.first ?: 0f) - sx
                    val grabZ = (table?.z ?: chair?.z ?: pointAt?.second ?: 0f) - sz
                    val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    var now = down.uptimeMillis
                    do {
                        val event = if (grabbed && !moved) {
                            withTimeoutOrNull((longPressAt - now).coerceAtLeast(0L)) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // 길게 누름 → 삭제, 손을 뗄 때까지 나머지 입력은 무시
                            selected = null
                            if (point != null) pointDelete(point) else delete(hitId!!)
                            do {
                                val rest = awaitPointerEvent()
                                rest.changes.forEach { it.consume() }
                            } while (rest.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        now = event.changes.first().uptimeMillis
                        val multi = event.changes.count { it.pressed } > 1
                        if (dragging && multi) dragging = false
                        if (!moved) {
                            val c = event.changes.firstOrNull { it.id == down.id }
                            if (multi || (c != null && (c.position - down.position).getDistance() > viewConfiguration.touchSlop)) {
                                moved = true
                                if (dragging) editStart()
                            }
                        }
                        val t = transform
                        if (dragging) {
                            val c = event.changes.firstOrNull { it.id == down.id && it.pressed }
                            if (moved && c != null && t != null) {
                                val (x, z) = t.toStore(c.position.x, c.position.y)
                                change(
                                    when {
                                        point != null -> moveFloorPoint(current, point, x + grabX, z + grabZ)
                                        table != null -> moveTable(current, table.id, x + grabX, z + grabZ)
                                        else -> moveChair(current, chair!!.id, x + grabX, z + grabZ)
                                    },
                                )
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
                    if (!moved && table != null) tableTap(table.id)
                    if (!moved && mid != null) pointAdd(mid)
                }
            },
    ) {
        val t = transform ?: return@Canvas

        // 바닥 (연한 세이지 + 테두리)
        val floorInvalid = floorError(normalizeOrigin(layout)) != null   // 저장 검사와 같은 기준 (EditScreen)
        if (layout.floor.size >= 3) {
            val path = Path().apply {
                layout.floor.forEachIndexed { i, (x, z) ->
                    val p = t.toScreen(x, z)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, FloorFill)
            // #26: 꼬인 바닥(저장 불가)은 빨간 테두리
            drawPath(path, if (floorInvalid) Danger else FloorEdge, style = Stroke(width = 2.dp.toPx()))
        }

        // 벽. #26: 벽 = 바닥 각 변이라 바닥 테두리를 덮음 → 꼬인 바닥이면 벽도 빨간색
        layout.walls.forEach { (a, b) ->
            drawLine(if (floorInvalid) Danger else WallColor, t.toScreen(a.first, a.second), t.toScreen(b.first, b.second), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }

        // 의자 (작은 원, 선택 = 세이지 + 링)
        val chairR = (CHAIR_RADIUS_M * t.scale).coerceAtLeast(4.dp.toPx())
        layout.chairs.forEach {
            val c = t.toScreen(it.x, it.z)
            if (it.id == selected) {
                drawCircle(Sage.copy(alpha = 0.35f), chairR + 5.dp.toPx(), c)
                drawCircle(Sage, chairR, c)
            } else {
                drawCircle(Line, chairR, c)
            }
        }

        // 테이블 (회전 반영, 선택 = 세이지 + 링, #26: 바닥 밖 = 빨간 링)
        val outside = tablesOutsideFloor(layout)
        layout.tables.forEach { table ->
            val c = t.toScreen(table.x, table.z)
            val w = table.width * t.scale
            val h = table.depth * t.scale
            val isSelected = table.id == selected || table.id == focusId
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
                if (table.id in outside) {
                    val ring = 3.dp.toPx()
                    drawRoundRect(
                        Danger, Offset(topLeft.x - ring, topLeft.y - ring), Size(w + 2 * ring, h + 2 * ring),
                        CornerRadius(radius.x + ring), style = Stroke(width = 2.dp.toPx()),
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

        // #26: 바닥 모양 모드 = 변 가운데 +(추가), 꼭짓점 흰 점(끌기·길게 눌러 삭제)
        if (floorEditable) {
            val plus = 5.dp.toPx()
            floorEdgeMids(layout).forEach { (x, z) ->
                val c = t.toScreen(x, z)
                drawCircle(Color.White, 9.dp.toPx(), c)
                drawCircle(FloorEdge, 9.dp.toPx(), c, style = Stroke(width = 1.5.dp.toPx()))
                drawLine(Brown, Offset(c.x - plus, c.y), Offset(c.x + plus, c.y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Brown, Offset(c.x, c.y - plus), Offset(c.x, c.y + plus), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            layout.floor.forEach { (x, z) ->
                val c = t.toScreen(x, z)
                drawCircle(Color.White, 10.dp.toPx(), c)
                drawCircle(Brown, 10.dp.toPx(), c, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}
