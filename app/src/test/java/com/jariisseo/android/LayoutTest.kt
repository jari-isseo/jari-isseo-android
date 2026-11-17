package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun moveTable_changesOnlyThatTable_andCarriesItsChairs() {
        val layout = layoutOf(state)   // 의자 (0.5, 1)은 1번 테이블 (1, 1) 왼쪽 변에 붙어 있음
        val first = layout.tables[0]
        val moved = moveTable(layout, first.id, 2.5f, 2.5f)

        assertEquals(2.5f, moved.tables[0].x)
        assertEquals(2.5f, moved.tables[0].z)
        assertEquals(layout.tables[1], moved.tables[1])
        assertEquals(2f, moved.chairs[0].x, 1e-4f)
        assertEquals(2.5f, moved.chairs[0].z, 1e-4f)
        assertEquals(layout.chairs, moveTable(layout, layout.tables[1].id, 3.5f, 1f).chairs)   // 2번은 의자 없음
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

    @Test
    fun chairSpots_rect_splitsOnLongSides() {
        val t = TableItem("t-1", "1번", 2f, 2f, width = 1.2f, depth = 0.6f)   // 긴 변은 z = 2 ± 0.3

        val four = chairSpots(t, 4)
        assertSpots(listOf(1.7f to 2.5f, 2.3f to 2.5f, 1.7f to 1.5f, 2.3f to 1.5f), four)
        val three = chairSpots(t, 3)   // 홀수면 앞(+z) 변에 1개 더
        assertSpots(listOf(1.7f to 2.5f, 2.3f to 2.5f, 2f to 1.5f), three)
        assertTrue(chairSpots(t, 0).isEmpty())
    }

    @Test
    fun chairSpots_followRotationAndRound() {
        val rotated = TableItem("t-1", "1번", 2f, 2f, width = 1.2f, depth = 0.6f, rotation = 90f)
        assertSpots(listOf(1.5f to 2f), chairSpots(rotated, 1))   // 앞(+z) 변이 시계 방향 90° → -x 쪽

        val round = TableItem("t-1", "1번", 2f, 2f, shape = "ROUND")   // 지름 0.8 → 중심에서 0.6
        assertSpots(listOf(2f to 2.6f, 2.6f to 2f, 2f to 1.4f, 1.4f to 2f), chairSpots(round, 4))
    }

    @Test
    fun setChairCount_replacesOnlyThatTablesChairs() {
        val a = TableItem("t-a", "1번", 1f, 1f)
        val b = TableItem("t-b", "2번", 3f, 1f)
        val other = ChairItem("c-b", 3f, 1.6f)   // 2번 테이블 의자
        val layout = Layout("f-1", emptyList(), emptyList(), listOf(a, b), listOf(other))

        val four = setChairCount(layout, a.id, 4)
        assertEquals(5, four.chairs.size)
        assertEquals(4, chairsOf(four, a).size)
        assertEquals(listOf(other), chairsOf(four, b))
        val ids = four.chairs.map { it.id } + four.tables.map { it.id } + four.floorId
        assertEquals(ids.size, ids.toSet().size)

        val two = setChairCount(four, a.id, 2)
        assertEquals(chairsOf(four, a).take(2).map { it.id }.toSet(), chairsOf(two, a).map { it.id }.toSet())   // id 재사용
        assertEquals(listOf(other), chairsOf(setChairCount(two, a.id, 0), b))
        assertTrue(chairsOf(setChairCount(two, a.id, 0), a).isEmpty())
    }

    @Test
    fun updateTable_replacesChairsOnlyWhenShapeChanges() {
        val t = TableItem("t-1", "1번", 2f, 2f, width = 1.2f, depth = 0.6f)
        val dragged = setChairCount(Layout("f-1", emptyList(), emptyList(), listOf(t), emptyList()), t.id, 2)
            .let { moveChair(it, it.chairs[0].id, 1.9f, 2.55f) }   // 사용자가 조금 옮김

        assertEquals(dragged.chairs, updateTable(dragged, t.copy(name = "창가")).chairs)   // 이름만 → 그대로
        val rotated = updateTable(dragged, t.copy(rotation = 90f))
        assertSpots(chairSpots(rotated.tables[0], 2), rotated.chairs.map { it.x to it.z })
    }

    @Test
    fun removeTable_removesItsChairsOnly() {
        val a = TableItem("t-a", "1번", 1f, 1f)
        val b = TableItem("t-b", "2번", 3f, 1f)
        val layout = Layout("f-1", emptyList(), emptyList(), listOf(a, b), listOf(ChairItem("c-a", 1f, 1.6f), ChairItem("c-b", 3f, 1.6f)))

        assertEquals(listOf("c-b"), removeTable(layout, a.id).chairs.map { it.id })
    }

    @Test
    fun chairsOf_roundTableWithUnequalSize_keepsItsChairs() {
        val t = TableItem("t-1", "1번", 2f, 2f, width = 1.2f, depth = 0.3f, shape = "ROUND")
        val empty = Layout("f-1", emptyList(), emptyList(), listOf(t), emptyList())

        assertEquals(1, chairsOf(setChairCount(empty, t.id, 1), t).size)
        val twice = setChairCount(setChairCount(empty, t.id, 1), t.id, 2)
        assertEquals(2, twice.chairs.size)   // 다시 늘려도 쌓이지 않음
        assertEquals(2, chairsOf(twice, t).size)
    }

    @Test
    fun chairsOf_longTableEndChair_notStolenByCloserCenter() {
        val a = TableItem("t-a", "1번", 2f, 2f, width = 3f, depth = 0.8f)
        val b = TableItem("t-b", "2번", 3.2f, 3.3f)
        val layout = setChairCount(Layout("f-1", emptyList(), emptyList(), listOf(a, b), emptyList()), a.id, 3)

        assertTrue(layout.chairs.any { abs(it.x - 2.75f) < 1e-4f && abs(it.z - 2.6f) < 1e-4f })   // 중심은 B가 더 가까운 의자
        assertEquals(3, chairsOf(layout, a).size)
        assertTrue(chairsOf(layout, b).isEmpty())
    }

    private fun assertSpots(expected: List<Pair<Float, Float>>, actual: List<Pair<Float, Float>>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.first, a.first, 1e-4f)
            assertEquals(e.second, a.second, 1e-4f)
        }
    }
}
