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
}
