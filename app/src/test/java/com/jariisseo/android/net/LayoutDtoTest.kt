package com.jariisseo.android.net

import com.jariisseo.android.ChairItem
import com.jariisseo.android.Layout
import com.jariisseo.android.TableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** #15: 앱 배치 ↔ 서버 형식, 층 하나만 끼워 넣는 병합 */
class LayoutDtoTest {
    private val layout = Layout(
        floorId = "f-aaaa",
        floor = listOf(0f to 0f, 4f to 0f, 4f to 3f, 0f to 3f),
        walls = listOf((0f to 0f) to (4f to 0f)),
        tables = listOf(TableItem("t-1111", "1번", 1.234f, 1f, rotation = 90f), TableItem("t-2222", "2번", 3f, 1f, shape = "ROUND")),
        chairs = listOf(ChairItem("c-1111", 0.5f, 1f)),
    )

    private fun floor(id: String, order: Int, tableId: String) = FloorDto(
        id, "${order + 1}층", order, FloorShapeDto(listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0))),
        tables = listOf(TableDto(tableId, "1번", 0.5, 0.5)),
    )

    @Test
    fun roundTrip_keepsIdsAndValues_roundedToCm() {
        val back = layout.toFloorDto("1층", 0).toLayout()

        assertEquals(layout.floorId, back.floorId)
        assertEquals(layout.floor, back.floor)
        assertEquals(layout.walls, back.walls)
        assertEquals(listOf("t-1111", "t-2222"), back.tables.map { it.id })
        assertEquals(1.23f, back.tables[0].x)
        assertEquals(90f, back.tables[0].rotation)
        assertEquals("ROUND", back.tables[1].shape)
        assertEquals(layout.chairs, back.chairs)
    }

    @Test
    fun rotation_roundsAndWrapsBelow360() {
        val dto = layout.copy(tables = listOf(TableItem("t-1111", "1번", 1f, 1f, rotation = 359.6f))).toFloorDto("1층", 0)

        assertEquals(0.0, dto.tables[0].rotation, 0.0)
    }

    @Test
    fun withFloor_noServerLayout_singleFirstFloor() {
        val pending = withFloor(null, layout)

        assertEquals(1, pending.layout.floors.size)
        assertEquals("1층", pending.layout.floors[0].name)
        assertEquals(0, pending.layout.floors[0].order)
        assertFalse(pending.resetsStatus)
    }

    @Test
    fun withFloor_reEdit_replacesSameFloorOnly() {
        val other = floor("f-bbbb", 1, "t-9999")
        val server = LayoutDto(3, floors = listOf(floor("f-aaaa", 0, "t-1111").copy(name = "본관"), other))

        val pending = withFloor(server, layout)

        assertFalse(pending.resetsStatus)
        assertEquals("본관", pending.layout.floors[0].name)
        assertEquals(listOf("t-1111", "t-2222"), pending.layout.floors[0].tables.map { it.id })
        assertSame(other, pending.layout.floors[1])
    }

    @Test
    fun withFloor_rescan_replacesFirstFloor_keepsNameOrder_andWarns() {
        // 다시 스캔해서 floorId가 새로 생김 → order 최소인 층(f-old1)을 교체
        val other = floor("f-bbbb", 1, "t-9999")
        val first = floor("f-old1", 0, "t-old1").copy(name = "1층 홀")
        val server = LayoutDto(3, floors = listOf(other, first))

        val pending = withFloor(server, layout)

        assertTrue(pending.resetsStatus)
        val replaced = pending.layout.floors[1]
        assertEquals("f-aaaa", replaced.id)
        assertEquals("1층 홀", replaced.name)
        assertEquals(0, replaced.order)
        assertEquals(listOf("t-9999"), pending.layout.floors[0].tables.map { it.id })
    }

    @Test
    fun withFloor_rescanOverEmptyFloor_noWarning() {
        val server = LayoutDto(1, floors = listOf(floor("f-old1", 0, "t-old1").copy(tables = emptyList())))

        assertFalse(withFloor(server, layout).resetsStatus)
    }
}
