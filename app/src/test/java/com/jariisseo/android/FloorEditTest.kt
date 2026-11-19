package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorEditTest {
    private val square = listOf(0f to 0f, 4f to 0f, 4f to 4f, 0f to 4f)
    // ㄱ자: 오른쪽 아래(2~4, 2~4)가 빈 모서리
    private val lShape = listOf(0f to 0f, 4f to 0f, 4f to 2f, 2f to 2f, 2f to 4f, 0f to 4f)

    private fun layout(floor: List<Pair<Float, Float>>, tables: List<TableItem> = emptyList()) =
        Layout("f-1", floor, CornerOutline.edges(floor), tables, emptyList())

    @Test
    fun move_updatesPointAndWalls() {
        val moved = moveFloorPoint(layout(square), 2, 5f, 5f)
        assertEquals(5f to 5f, moved.floor[2])
        assertEquals(CornerOutline.edges(moved.floor), moved.walls)
    }

    @Test
    fun add_insertsEdgeMidpoint() {
        val added = addFloorPoint(layout(square), 1)   // 변 (4,0) → (4,4)
        assertEquals(5, added.floor.size)
        assertEquals(4f to 2f, added.floor[2])
        assertEquals(5, added.walls.size)
        // 마지막 변 (0,4) → (0,0)
        assertEquals(0f to 2f, addFloorPoint(layout(square), 3).floor[4])
    }

    @Test
    fun remove_keepsAtLeastThree() {
        val three = removeFloorPoint(layout(square), 0)
        assertEquals(listOf(4f to 0f, 4f to 4f, 0f to 4f), three.floor)
        assertEquals(3, three.walls.size)
        assertEquals(three, removeFloorPoint(three, 0))
    }

    @Test
    fun lShape_canBeMadeFromSquare() {
        // 사각형 → 변 가운데 2개 추가 → 모서리 점을 안쪽으로
        var l = addFloorPoint(layout(square), 1)   // (4,2) 추가 → 꼭짓점 5개
        l = addFloorPoint(l, 3)                    // 변 (4,4)→(0,4) 가운데 (2,4) 추가
        l = moveFloorPoint(l, 3, 2f, 2f)           // (4,4) → (2,2)
        assertEquals(lShape, l.floor)
        assertNull(floorError(l))
    }

    @Test
    fun isSimple_matchesServerRules() {
        assertTrue(FloorPolygon.isSimple(square))
        assertTrue(FloorPolygon.isSimple(lShape))
        assertFalse(FloorPolygon.isSimple(listOf(0f to 0f, 4f to 4f, 4f to 0f, 0f to 4f)))           // 8자
        assertFalse(FloorPolygon.isSimple(listOf(0f to 0f, 4f to 0f, 2f to 0f, 2f to 4f)))           // 점이 변에 닿음(되돌아감)
        assertFalse(FloorPolygon.isSimple(listOf(0f to 0f, 4f to 0f, 4f to 0f, 4f to 4f)))           // 같은 점 연속
        assertFalse(FloorPolygon.isSimple(listOf(0f to 0f, 2f to 0f, 4f to 0f)))                     // 일직선
        assertFalse(FloorPolygon.isSimple(listOf(0f to 0f, 4f to 0f, 4f to 4f, 4f to 2f, 0f to 4f))) // 꼭짓점이 변 위
        assertFalse(FloorPolygon.isSimple(listOf(0f to 0f, 4f to 0f, 4f to 0.004f, 0f to 4f)))       // cm 반올림하면 같은 점
    }

    @Test
    fun floorError_onlyForInvalidFloor() {
        assertNull(floorError(layout(square)))
        assertNotNull(floorError(moveFloorPoint(layout(square), 1, 0f, 4.5f)))   // 꼬임
    }

    @Test
    fun floorError_checksNormalizedFloor() {
        // 원래 좌표로는 통과하지만, 0 이상으로 옮긴 뒤 cm 반올림하면 (0, 2)가 두 번 연속 → 서버가 거부
        val l = layout(listOf(0.004f to 0f, 4f to 0f, 4f to 4f, 0.006f to 4f, 0.006f to 2f, 0.004f to 2f))
        assertNull(floorError(l))
        assertNotNull(floorError(normalizeOrigin(l)))
    }

    @Test
    fun tablesOutsideFloor_findsEmptyCorner() {
        val inside = TableItem("t-in", "1번", 1f, 1f)
        val corner = TableItem("t-out", "2번", 3f, 3f)
        assertEquals(setOf("t-out"), tablesOutsideFloor(layout(lShape, listOf(inside, corner))))
        assertEquals(emptySet<String>(), tablesOutsideFloor(layout(square, listOf(inside, corner))))
    }

    @Test
    fun hit_findsNearestPointAndEdgeMid() {
        val l = layout(square)
        assertEquals(1, hitFloorPoint(l, 3.9f, 0.1f, 0.3f))
        assertNull(hitFloorPoint(l, 2f, 2f, 0.3f))
        assertEquals(1, hitFloorEdgeMid(l, 4.1f, 2f, 0.3f))
    }

    @Test
    fun normalizeOrigin_shiftsEverythingToNonNegative() {
        val l = moveFloorPoint(layout(square, listOf(TableItem("t-1", "1번", 1f, 1f))), 0, -1f, -2f)
        val n = normalizeOrigin(l)
        assertEquals(0f to 0f, n.floor[0])
        assertEquals(5f to 2f, n.floor[1])
        assertEquals(2f, n.tables[0].x)
        assertEquals(3f, n.tables[0].z)
        assertTrue(n.walls.all { (a, b) -> a.first >= 0 && a.second >= 0 && b.first >= 0 && b.second >= 0 })
    }
}
