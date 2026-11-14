package com.jariisseo.android

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"
const val TABLE_SIZE_M = 0.8f   // 아직 실측하지 않아 고정 (샘플의 기본 테이블 크기)

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
    fun newId(prefix: String): String {
        while (true) {
            val id = prefix + (1..4).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
            if (used.add(id)) return id
        }
    }

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
