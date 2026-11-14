package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val state = ArUiState(
        floorPolygon = listOf(0f to 0f, 4f to 0f, 4f to 3f, 0f to 3f),
        walls = listOf((0f to 0f) to (4f to 0f)),
        mapItems = listOf(
            MapItem("테이블", 1f, 1f, 5, confirmed = true),
            MapItem("테이블", 3f, 1f, 5, confirmed = true),
            MapItem("테이블", 2f, 2f, 1, confirmed = false),
            MapItem("의자", 0.5f, 1f, 5, confirmed = true),
            MapItem("소파", 2f, 2.5f, 5, confirmed = true),
        ),
    )

    @Test
    fun layoutOf_confirmedTablesAndChairs_namedInOrder() {
        val layout = layoutOf(state)

        assertEquals(listOf("1번", "2번"), layout.tables.map { it.name })
        assertEquals(1, layout.chairs.size)
        assertEquals(state.floorPolygon, layout.floor)
        val ids = layout.tables.map { it.id } + layout.chairs.map { it.id } + layout.floorId
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(layout.tables.all { Regex("t-[0-9a-z]{4}").matches(it.id) })
    }

    @Test
    fun hitTable_insideOutsideAndSlop() {
        val layout = layoutOf(state)   // 테이블 0.8×0.8, 중심 (1, 1)·(3, 1)

        assertEquals("1번", hitTable(layout, 1.3f, 1.3f)?.name)
        assertNull(hitTable(layout, 1.5f, 1f))
        assertEquals("1번", hitTable(layout, 1.5f, 1f, slop = 0.15f)?.name)
        assertEquals("2번", hitTable(layout, 3f, 1f)?.name)
    }

    @Test
    fun hitTable_respectsRotation() {
        // 2.0×0.4 긴 테이블을 90° 돌리면 세로로 김
        val long = TableItem("t-aaaa", "1번", 2f, 2f, width = 2f, depth = 0.4f, rotation = 90f)
        val layout = Layout("f-aaaa", emptyList(), emptyList(), listOf(long), emptyList())

        assertEquals("1번", hitTable(layout, 2f, 2.8f)?.name)   // 세로 방향 끝
        assertNull(hitTable(layout, 2.8f, 2f))                   // 가로 방향은 좁음
    }

    @Test
    fun moveTable_changesOnlyThatTable() {
        val layout = layoutOf(state)
        val first = layout.tables[0]
        val moved = moveTable(layout, first.id, 2.5f, 2.5f)

        assertEquals(2.5f, moved.tables[0].x)
        assertEquals(2.5f, moved.tables[0].z)
        assertEquals(layout.tables[1], moved.tables[1])
        assertEquals(layout.chairs, moved.chairs)
    }

    @Test
    fun addTable_uniqueIdAndNextName() {
        val layout = layoutOf(state)
        val added = addTable(layout, 2f, 2f)

        assertEquals(layout.tables, added.tables.dropLast(1))
        val t = added.tables.last()
        assertEquals("3번", t.name)
        assertEquals(2f, t.x)
        val ids = added.tables.map { it.id } + added.chairs.map { it.id } + added.floorId
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun removeAndMove_changeOnlyTarget() {
        val layout = layoutOf(state)
        val chair = layout.chairs[0]

        assertEquals(listOf(layout.tables[1]), removeTable(layout, layout.tables[0].id).tables)
        assertTrue(removeChair(layout, chair.id).chairs.isEmpty())
        val moved = moveChair(layout, chair.id, 3f, 2f)
        assertEquals(ChairItem(chair.id, 3f, 2f), moved.chairs[0])
        assertEquals(layout.tables, moved.tables)
    }

    @Test
    fun updateTable_clampsSizeAndRotation() {
        val layout = layoutOf(state)
        val edited = layout.tables[0].copy(name = "창가", width = 5f, depth = 0.1f, rotation = 450f, shape = "ROUND")
        val t = updateTable(layout, edited).tables[0]

        assertEquals("창가", t.name)
        assertEquals(TABLE_SIZE_MAX, t.width)
        assertEquals(TABLE_SIZE_MIN, t.depth)
        assertEquals(90f, t.rotation)
        assertEquals("ROUND", t.shape)
    }

    @Test
    fun hitChair_insideOutsideAndSlop() {
        val layout = layoutOf(state)   // 의자 (0.5, 1), 반지름 0.2

        assertEquals(layout.chairs[0], hitChair(layout, 0.6f, 1.1f))
        assertNull(hitChair(layout, 0.8f, 1f))
        assertEquals(layout.chairs[0], hitChair(layout, 0.8f, 1f, slop = 0.15f))
    }

    @Test
    fun rotateLayout_90_clockwiseAndNormalized() {
        // 4×3 바닥, 테이블 (1, 1) rotation 270 → 90° 돌리면 3×4 바닥, 테이블 (2, 1), rotation 0
        val table = TableItem("t-aaaa", "1번", 1f, 1f, rotation = 270f)
        val layout = Layout("f-aaaa", state.floorPolygon, emptyList(), listOf(table), emptyList())
        val r = rotateLayout(layout, 90f)

        assertEquals(0f, r.floor.minOf { it.first }, 1e-4f)
        assertEquals(0f, r.floor.minOf { it.second }, 1e-4f)
        assertEquals(3f, r.floor.maxOf { it.first }, 1e-4f)
        assertEquals(4f, r.floor.maxOf { it.second }, 1e-4f)
        assertEquals(2f, r.tables[0].x, 1e-4f)
        assertEquals(1f, r.tables[0].z, 1e-4f)
        assertEquals(0f, r.tables[0].rotation, 1e-4f)
    }

    @Test
    fun rotateLayout_fourTimes_returnsOriginal() {
        val layout = layoutOf(state)
        val r = (1..4).fold(layout) { acc, _ -> rotateLayout(acc, 90f) }

        layout.floor.zip(r.floor).forEach { (a, b) ->
            assertEquals(a.first, b.first, 1e-4f)
            assertEquals(a.second, b.second, 1e-4f)
        }
        layout.tables.zip(r.tables).forEach { (a, b) ->
            assertEquals(a.x, b.x, 1e-4f)
            assertEquals(a.z, b.z, 1e-4f)
            assertEquals(a.rotation, b.rotation, 1e-3f)
        }
    }
}
