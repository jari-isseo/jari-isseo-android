package com.jariisseo.android

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"
const val TABLE_SIZE_M = 0.8f   // 아직 실측하지 않아 고정 (샘플의 기본 테이블 크기)
const val TABLE_SIZE_MIN = 0.3f  // 서버 검증 규칙 (크기 0.3~3m)
const val TABLE_SIZE_MAX = 3f

/** 접두사 + 랜덤 4자리, used에 없는 ID */
private fun newId(prefix: String, used: Set<String>, random: Random): String {
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

/** 테이블 하나의 위치만 바꾼 새 배치 */
fun moveTable(layout: Layout, id: String, x: Float, z: Float): Layout =
    layout.copy(tables = layout.tables.map { if (it.id == id) it.copy(x = x, z = z) else it })

/** (x, z)에 있는 의자 (중심에서 radius + slop(m) 안) */
fun hitChair(layout: Layout, x: Float, z: Float, slop: Float = 0f, radius: Float = CHAIR_RADIUS_M): ChairItem? =
    layout.chairs.lastOrNull { c ->
        val dx = x - c.x
        val dz = z - c.z
        dx * dx + dz * dz <= (radius + slop) * (radius + slop)
    }

const val CHAIR_RADIUS_M = 0.2f

/** #14: 새 테이블 (ID는 층·테이블·의자 전체에서 유일, 이름은 가장 큰 "N번" + 1) */
fun addTable(layout: Layout, x: Float, z: Float, random: Random = Random.Default): Layout {
    val used = layout.tables.map { it.id } + layout.chairs.map { it.id } + layout.floorId
    val next = (layout.tables.mapNotNull { it.name.removeSuffix("번").toIntOrNull() }.maxOrNull() ?: 0) + 1
    return layout.copy(tables = layout.tables + TableItem(newId("t-", used.toSet(), random), "${next}번", x, z))
}

fun removeTable(layout: Layout, id: String): Layout = layout.copy(tables = layout.tables.filterNot { it.id == id })

/** #14: 편집 시트 결과 반영. 크기는 서버 규칙(0.3~3m)으로 제한 */
fun updateTable(layout: Layout, table: TableItem): Layout {
    val fixed = table.copy(
        width = table.width.coerceIn(TABLE_SIZE_MIN, TABLE_SIZE_MAX),
        depth = table.depth.coerceIn(TABLE_SIZE_MIN, TABLE_SIZE_MAX),
        rotation = normalizeDegrees(table.rotation),
    )
    return layout.copy(tables = layout.tables.map { if (it.id == table.id) fixed else it })
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

    val points = floor + walls.flatMap { listOf(it.first, it.second) } + tables.map { it.x to it.z } + chairs.map { it.x to it.z }
    if (points.isEmpty()) return layout
    val ox = points.minOf { it.first }
    val oz = points.minOf { it.second }
    fun shift(p: Pair<Float, Float>) = (p.first - ox) to (p.second - oz)
    return layout.copy(
        floor = floor.map(::shift),
        walls = walls.map { shift(it.first) to shift(it.second) },
        tables = tables.map { it.copy(x = it.x - ox, z = it.z - oz) },
        chairs = chairs.map { it.copy(x = it.x - ox, z = it.z - oz) },
    )
}
