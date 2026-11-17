package com.jariisseo.android

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"
const val TABLE_SIZE_M = 0.8f   // 아직 실측하지 않아 고정 (샘플의 기본 테이블 크기)
const val TABLE_SIZE_MIN = 0.3f  // 서버 검증 규칙 (크기 0.3~3m)
const val TABLE_SIZE_MAX = 3f

/** 접두사 + 랜덤 4자리, used에 없는 ID (#25: 층 합치기에서도 씀) */
internal fun newId(prefix: String, used: Set<String>, random: Random): String {
    while (true) {
        val id = prefix + (1..4).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
        if (id !in used) return id
    }
}

/** #13: 배치 테이블 1개 (매장 좌표 m, rotation = 위에서 볼 때 시계 방향 도) */
data class TableItem(
    val id: String,
    val name: String,
    val x: Float,
    val z: Float,
    val width: Float = TABLE_SIZE_M,
    val depth: Float = TABLE_SIZE_M,
    val rotation: Float = 0f,
    val shape: String = "RECT",
)

data class ChairItem(val id: String, val x: Float, val z: Float)

/**
 * #13: 층 1개의 배치 (server `docs/samples/sample-layout.json`의 floors[] 항목).
 * 스캔 결과에서 만들고, 보정 화면에서 고치고, JSON으로 내보냄(#7)·업로드(#15)
 */
data class Layout(
    val floorId: String,
    val floor: List<Pair<Float, Float>>,
    val walls: List<WallLine>,
    val tables: List<TableItem>,
    val chairs: List<ChairItem>,
)

/**
 * 스캔 결과(매장 좌표) → 배치. 확정된 테이블·의자만 (소파는 스키마에 없어 제외).
 * ID는 접두사 + 랜덤 4자리, 매장 전체에서 유일. 테이블 이름은 "1번", "2번"…
 */
fun layoutOf(state: ArUiState, random: Random = Random.Default): Layout {
    val used = mutableSetOf<String>()
    fun newId(prefix: String): String = newId(prefix, used, random).also { used.add(it) }

    val floorId = newId("f-")
    val confirmed = state.mapItems.filter { it.confirmed }
    val tables = confirmed.filter { it.label == "테이블" }.mapIndexed { i, t ->
        TableItem(newId("t-"), "${i + 1}번", t.x, t.z)
    }
    val chairs = confirmed.filter { it.label == "의자" }.map { ChairItem(newId("c-"), it.x, it.z) }
    return Layout(floorId, state.floorPolygon, state.walls, tables, chairs)
}

/** (x, z)에 있는 테이블 (위에 그려진 = 목록 뒤쪽 먼저). 회전을 고려하고, 가장자리에서 slop(m)까지 인정 */
fun hitTable(layout: Layout, x: Float, z: Float, slop: Float = 0f): TableItem? =
    layout.tables.lastOrNull { t ->
        // 테이블 중심 기준으로 되돌려 돌린 좌표에서 직사각형 안인지
        val r = Math.toRadians(-t.rotation.toDouble())
        val dx = x - t.x
        val dz = z - t.z
        val lx = (dx * cos(r) - dz * sin(r)).toFloat()
        val lz = (dx * sin(r) + dz * cos(r)).toFloat()
        abs(lx) <= t.width / 2 + slop && abs(lz) <= t.depth / 2 + slop
    }

/** 테이블 위치를 바꾼 새 배치. 그 테이블에 붙은 의자도 같이 옮김 */
fun moveTable(layout: Layout, id: String, x: Float, z: Float): Layout {
    val t = layout.tables.firstOrNull { it.id == id } ?: return layout
    val own = chairsOf(layout, t).map { it.id }.toSet()
    val dx = x - t.x
    val dz = z - t.z
    return layout.copy(
        tables = layout.tables.map { if (it.id == id) it.copy(x = x, z = z) else it },
        chairs = layout.chairs.map { if (it.id in own) it.copy(x = it.x + dx, z = it.z + dz) else it },
    )
}

/** (x, z)에 있는 의자 (중심에서 radius + slop(m) 안) */
fun hitChair(layout: Layout, x: Float, z: Float, slop: Float = 0f, radius: Float = CHAIR_RADIUS_M): ChairItem? =
    layout.chairs.lastOrNull { c ->
        val dx = x - c.x
        val dz = z - c.z
        dx * dx + dz * dz <= (radius + slop) * (radius + slop)
    }

const val CHAIR_RADIUS_M = 0.2f
/**
 * 테이블에 붙은 의자: 테이블 가장자리에서 의자 지름 안에 있고, 가장자리가 가장 가까운 테이블이 이 테이블인 것.
 * (스키마에 의자-테이블 연결이 없어 위치로 판단. 중심 거리로 고르면 긴 테이블 끝 의자가 옆 테이블 것이 됨)
 */
fun chairsOf(layout: Layout, t: TableItem): List<ChairItem> = layout.chairs.filter { c ->
    edgeDistance(t, c.x, c.z) <= 2 * CHAIR_RADIUS_M &&
        layout.tables.minByOrNull { edgeDistance(it, c.x, c.z) }?.id == t.id
}

/** (x, z)에서 테이블 가장자리까지 거리 (안쪽이면 0). 원형은 chairSpots와 같은 반지름 max(가로, 세로) / 2 */
private fun edgeDistance(t: TableItem, x: Float, z: Float): Float {
    val (lx, lz) = toTableLocal(t, x, z)
    return if (t.shape == "ROUND") max(hypot(lx, lz) - max(t.width, t.depth) / 2, 0f)
    else hypot(max(abs(lx) - t.width / 2, 0f), max(abs(lz) - t.depth / 2, 0f))
}

/**
 * 테이블 둘레에 의자 n개를 놓을 위치 (변 바깥, 의자 반지름만큼 띄움).
 * 원형: 앞(+z)부터 같은 간격. 사각: 긴 두 변에 나눠서 (홀수면 앞쪽 변에 1개 더), 변 위에서 같은 간격
 */
fun chairSpots(t: TableItem, n: Int): List<Pair<Float, Float>> {
    if (n <= 0) return emptyList()
    val local = if (t.shape == "ROUND") {
        val r = max(t.width, t.depth) / 2 + CHAIR_RADIUS_M
        (0 until n).map { i ->
            val a = 2 * Math.PI * i / n
            (r * sin(a)).toFloat() to (r * cos(a)).toFloat()
        }
    } else {
        val wide = t.width >= t.depth
        val len = if (wide) t.width else t.depth
        val off = (if (wide) t.depth else t.width) / 2 + CHAIR_RADIUS_M
        fun side(k: Int, sign: Int) = (0 until k).map { i -> (-len / 2 + len * (i + 0.5f) / k) to sign * off }
        (side((n + 1) / 2, 1) + side(n / 2, -1)).map { (along, across) -> if (wide) along to across else across to along }
    }
    return local.map { (lx, lz) -> fromTableLocal(t, lx, lz) }
}

/** 테이블의 의자 수를 n개로: 붙은 의자를 지우고 둘레에 다시 놓음 (있던 의자 id는 앞에서부터 재사용) */
fun setChairCount(layout: Layout, tableId: String, n: Int, random: Random = Random.Default): Layout {
    val t = layout.tables.firstOrNull { it.id == tableId } ?: return layout
    val own = chairsOf(layout, t)
    val used = (layout.tables.map { it.id } + layout.chairs.map { it.id } + layout.floorId).toMutableSet()
    val placed = chairSpots(t, n).mapIndexed { i, (x, z) ->
        ChairItem(own.getOrNull(i)?.id ?: newId("c-", used, random).also { used.add(it) }, x, z)
    }
    return layout.copy(chairs = layout.chairs - own.toSet() + placed)
}

/** 매장 좌표 → 테이블 기준 좌표 (hitTable과 같은 회전) */
private fun toTableLocal(t: TableItem, x: Float, z: Float): Pair<Float, Float> {
    val r = Math.toRadians(-t.rotation.toDouble())
    val dx = x - t.x
    val dz = z - t.z
    return (dx * cos(r) - dz * sin(r)).toFloat() to (dx * sin(r) + dz * cos(r)).toFloat()
}

private fun fromTableLocal(t: TableItem, lx: Float, lz: Float): Pair<Float, Float> {
    val r = Math.toRadians(t.rotation.toDouble())
    return t.x + (lx * cos(r) - lz * sin(r)).toFloat() to t.z + (lx * sin(r) + lz * cos(r)).toFloat()
}

/** #14: 새 테이블 (ID는 층·테이블·의자 전체에서 유일, 이름은 가장 큰 "N번" + 1) */
fun addTable(layout: Layout, x: Float, z: Float, random: Random = Random.Default): Layout {
    val used = layout.tables.map { it.id } + layout.chairs.map { it.id } + layout.floorId
    val next = (layout.tables.mapNotNull { it.name.removeSuffix("번").toIntOrNull() }.maxOrNull() ?: 0) + 1
    return layout.copy(tables = layout.tables + TableItem(newId("t-", used.toSet(), random), "${next}번", x, z))
}

/** 테이블과 거기 붙은 의자를 함께 지움 */
fun removeTable(layout: Layout, id: String): Layout {
    val own = layout.tables.firstOrNull { it.id == id }?.let { chairsOf(layout, it) }.orEmpty().toSet()
    return layout.copy(tables = layout.tables.filterNot { it.id == id }, chairs = layout.chairs - own)
}

/** #14: 편집 시트 결과 반영. 크기는 서버 규칙(0.3~3m)으로 제한. 크기·회전·모양이 바뀌면 붙은 의자를 다시 놓음 */
fun updateTable(layout: Layout, table: TableItem): Layout {
    val fixed = table.copy(
        width = table.width.coerceIn(TABLE_SIZE_MIN, TABLE_SIZE_MAX),
        depth = table.depth.coerceIn(TABLE_SIZE_MIN, TABLE_SIZE_MAX),
        rotation = normalizeDegrees(table.rotation),
    )
    val old = layout.tables.firstOrNull { it.id == table.id }
    val own = if (old == null || old.copy(name = fixed.name) == fixed) emptyList() else chairsOf(layout, old)
    val spots = chairSpots(fixed, own.size)
    return layout.copy(
        tables = layout.tables.map { if (it.id == table.id) fixed else it },
        chairs = layout.chairs.map { c ->
            val i = own.indexOf(c)
            if (i < 0) c else c.copy(x = spots[i].first, z = spots[i].second)
        },
    )
}

fun moveChair(layout: Layout, id: String, x: Float, z: Float): Layout =
    layout.copy(chairs = layout.chairs.map { if (it.id == id) it.copy(x = x, z = z) else it })

fun removeChair(layout: Layout, id: String): Layout = layout.copy(chairs = layout.chairs.filterNot { it.id == id })

/** [0, 360) */
fun normalizeDegrees(d: Float): Float = ((d % 360f) + 360f) % 360f

/**
 * #14: 배치 전체를 위에서 볼 때 시계 방향으로 degrees만큼 회전 (테이블 rotation과 같은 방향).
 * 회전 후 바닥·벽·가구의 최소점이 (0, 0)이 되도록 이동 (StoreFrame과 같은 "0 이상" 규칙)
 */
fun rotateLayout(layout: Layout, degrees: Float): Layout {
    val r = Math.toRadians(degrees.toDouble())
    val cos = cos(r).toFloat()
    val sin = sin(r).toFloat()
    fun rot(p: Pair<Float, Float>) = (p.first * cos - p.second * sin) to (p.first * sin + p.second * cos)

    val floor = layout.floor.map(::rot)
    val walls = layout.walls.map { rot(it.first) to rot(it.second) }
    val tables = layout.tables.map { t ->
        val (x, z) = rot(t.x to t.z)
        t.copy(x = x, z = z, rotation = normalizeDegrees(t.rotation + degrees))
    }
    val chairs = layout.chairs.map { c -> val (x, z) = rot(c.x to c.z); c.copy(x = x, z = z) }

    return normalizeOrigin(layout.copy(floor = floor, walls = walls, tables = tables, chairs = chairs))
}

/** 바닥·벽·가구의 최소점이 (0, 0)이 되도록 이동 (서버 좌표 0~100m, StoreFrame과 같은 "0 이상" 규칙) */
fun normalizeOrigin(layout: Layout): Layout {
    val points = layout.floor + layout.walls.flatMap { listOf(it.first, it.second) } +
        layout.tables.map { it.x to it.z } + layout.chairs.map { it.x to it.z }
    if (points.isEmpty()) return layout
    val ox = points.minOf { it.first }
    val oz = points.minOf { it.second }
    fun shift(p: Pair<Float, Float>) = (p.first - ox) to (p.second - oz)
    return layout.copy(
        floor = layout.floor.map(::shift),
        walls = layout.walls.map { shift(it.first) to shift(it.second) },
        tables = layout.tables.map { it.copy(x = it.x - ox, z = it.z - oz) },
        chairs = layout.chairs.map { it.copy(x = it.x - ox, z = it.z - oz) },
    )
}

// ── #26: 바닥 모양(꼭짓점) 편집. 벽은 항상 바닥 각 변 (스캔 결과와 같은 규칙, #40) ──

private fun Layout.withFloor(floor: List<Pair<Float, Float>>) = copy(floor = floor, walls = CornerOutline.edges(floor))

fun moveFloorPoint(layout: Layout, i: Int, x: Float, z: Float): Layout =
    layout.withFloor(layout.floor.mapIndexed { k, p -> if (k == i) x to z else p })

/** 변 edge(꼭짓점 edge → edge+1)의 가운데에 꼭짓점 추가 */
fun addFloorPoint(layout: Layout, edge: Int): Layout {
    val f = layout.floor
    val a = f[edge]
    val b = f[(edge + 1) % f.size]
    val mid = (a.first + b.first) / 2 to (a.second + b.second) / 2
    return layout.withFloor(f.subList(0, edge + 1) + mid + f.subList(edge + 1, f.size))
}

/** 꼭짓점 삭제 (3개 이하면 그대로) */
fun removeFloorPoint(layout: Layout, i: Int): Layout =
    if (layout.floor.size <= 3) layout else layout.withFloor(layout.floor.filterIndexed { k, _ -> k != i })

/** (x, z)에서 slop(m) 안의 가장 가까운 꼭짓점 */
fun hitFloorPoint(layout: Layout, x: Float, z: Float, slop: Float): Int? =
    layout.floor.indices
        .map { it to hypot(layout.floor[it].first - x, layout.floor[it].second - z) }
        .filter { it.second <= slop }
        .minByOrNull { it.second }?.first

/** (x, z)에서 slop(m) 안의 가장 가까운 변 가운데 → 그 변 번호 */
fun hitFloorEdgeMid(layout: Layout, x: Float, z: Float, slop: Float): Int? =
    floorEdgeMids(layout).withIndex()
        .map { it.index to hypot(it.value.first - x, it.value.second - z) }
        .filter { it.second <= slop }
        .minByOrNull { it.second }?.first

/** 각 변의 가운데 (변 i = 꼭짓점 i → i+1) */
fun floorEdgeMids(layout: Layout): List<Pair<Float, Float>> =
    if (layout.floor.size < 3) emptyList()
    else CornerOutline.edges(layout.floor).map { (a, b) -> (a.first + b.first) / 2 to (a.second + b.second) / 2 }

/** 저장할 수 없는 바닥이면 그 이유 (서버 검증과 같은 규칙) */
fun floorError(layout: Layout): String? = when {
    layout.floor.size < 3 -> "바닥 꼭짓점이 3개 이상 필요해요"
    !FloorPolygon.isSimple(layout.floor) -> "바닥 선이 서로 겹치거나 교차해요"
    else -> null
}

/** 중심이 바닥 밖인 테이블 id */
fun tablesOutsideFloor(layout: Layout): Set<String> =
    if (layout.floor.size < 3) emptySet()
    else layout.tables.filterNot { FloorPolygon.contains(layout.floor, it.x, it.z) }.map { it.id }.toSet()
