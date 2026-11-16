package com.jariisseo.android.net

import com.jariisseo.android.ChairItem
import com.jariisseo.android.Layout
import com.jariisseo.android.TableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** #15: 앱 배치 ↔ 서버 형식, 층 하나만 끼워 넣는 병합. #25: 층 단위 대상·층 관리 */
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
    fun withFloor_noServerLayout_singleFloorWithTargetName() {
        val pending = withFloor(null, layout, FloorTarget.New("1층"))

        assertEquals(1, pending.layout.floors.size)
        assertEquals("1층", pending.layout.floors[0].name)
        assertEquals(0, pending.layout.floors[0].order)
        assertEquals("f-aaaa", pending.floorId)
        assertFalse(pending.resetsStatus)
    }

    @Test
    fun withFloor_reEdit_replacesSameFloorOnly() {
        val other = floor("f-bbbb", 1, "t-9999")
        val server = LayoutDto(3, floors = listOf(floor("f-aaaa", 0, "t-1111").copy(name = "본관"), other))

        val pending = withFloor(server, layout, FloorTarget.Existing("f-aaaa", "본관"))

        assertFalse(pending.resetsStatus)
        assertEquals("본관", pending.layout.floors[0].name)
        assertEquals(listOf("t-1111", "t-2222"), pending.layout.floors[0].tables.map { it.id })
        assertSame(other, pending.layout.floors[1])
    }

    /** #25: 다시 스캔(새 floorId) → 고른 층만 교체, 그 층의 id·이름·순서 유지, 테이블이 있었으면 경고. 첫 층이 아니어도 */
    @Test
    fun withFloor_rescanChosenFloor_keepsIdNameOrder_andWarns() {
        val first = floor("f-old1", 0, "t-old1")
        val second = floor("f-bbbb", 1, "t-9999").copy(name = "2층 홀")
        val server = LayoutDto(3, floors = listOf(first, second))

        val pending = withFloor(server, layout, FloorTarget.Existing("f-bbbb", "2층 홀"))

        assertTrue(pending.resetsStatus)
        assertSame(first, pending.layout.floors[0])
        val replaced = pending.layout.floors[1]
        assertEquals("f-bbbb", replaced.id)
        assertEquals("2층 홀", replaced.name)
        assertEquals(1, replaced.order)
        assertEquals(listOf("t-1111", "t-2222"), replaced.tables.map { it.id })
        assertEquals("f-bbbb", pending.floorId)
    }

    @Test
    fun withFloor_rescanOverEmptyFloor_noWarning() {
        val server = LayoutDto(1, floors = listOf(floor("f-old1", 0, "t-old1").copy(tables = emptyList())))

        assertFalse(withFloor(server, layout, FloorTarget.Existing("f-old1", "1층")).resetsStatus)
    }

    /** #25: 새 층 → 맨 뒤 순서로 추가, 다른 층은 그대로 */
    @Test
    fun withFloor_new_appendsAfterLastOrder() {
        val a = floor("f-old1", 0, "t-old1")
        val b = floor("f-bbbb", 5, "t-9999")
        val server = LayoutDto(3, floors = listOf(a, b))

        val pending = withFloor(server, layout, FloorTarget.New(" 루프탑 "))

        assertEquals(listOf("f-old1", "f-bbbb", "f-aaaa"), pending.layout.floors.map { it.id })
        assertSame(a, pending.layout.floors[0])
        assertEquals("루프탑", pending.layout.floors[2].name)
        assertEquals(6, pending.layout.floors[2].order)
        assertFalse(pending.resetsStatus)
    }

    /** #25: 보정하던 층이 그새 다른 기기에서 삭제됨 → 맨 뒤에 그 id로 추가 */
    @Test
    fun withFloor_existingDeletedMeanwhile_appends() {
        val server = LayoutDto(3, floors = listOf(floor("f-old1", 0, "t-old1")))

        val pending = withFloor(server, layout, FloorTarget.Existing("f-gone", "2층"))

        assertEquals(listOf("f-old1", "f-gone"), pending.layout.floors.map { it.id })
        assertEquals("2층", pending.layout.floors[1].name)
        assertEquals(1, pending.layout.floors[1].order)
        assertFalse(pending.resetsStatus)
    }

    /** #25: 서버는 테이블 id를 모든 층에서 유일하게 요구 → 다른 층과 겹치는 id·층 id만 새로 */
    @Test
    fun withFloor_new_renamesIdsTakenByOtherFloors() {
        val server = LayoutDto(3, floors = listOf(floor("f-aaaa", 0, "t-1111")))

        val pending = withFloor(server, layout, FloorTarget.New("2층"))

        val added = pending.layout.floors[1]
        assertTrue(added.id.startsWith("f-") && added.id != "f-aaaa")
        assertEquals(added.id, pending.floorId)
        assertTrue(added.tables[0].id.startsWith("t-") && added.tables[0].id != "t-1111")
        assertEquals("t-2222", added.tables[1].id)   // 겹치지 않으면 그대로
        assertEquals("c-1111", added.chairs[0].id)
        val all = pending.layout.floors.flatMap { f -> f.tables.map { it.id } }
        assertEquals(all.size, all.toSet().size)
    }

    private val three = LayoutDto(
        3,
        floors = listOf(floor("f-c", 2, "t-c"), floor("f-a", 0, "t-a"), floor("f-b", 1, "t-b")),
    )

    @Test
    fun renameFloor_trimsAndKeepsOthers() {
        val renamed = three.renameFloor("f-b", "  테라스 ")

        assertEquals(listOf("3층", "1층", "테라스"), renamed.floors.map { it.name })
        assertEquals(three.floors.map { it.tables }, renamed.floors.map { it.tables })
    }

    @Test
    fun moveFloor_swapsOrderWithNeighbor_andStopsAtEnds() {
        fun ordered(l: LayoutDto) = l.floors.sortedBy { it.order }.map { it.id }

        assertEquals(listOf("f-b", "f-a", "f-c"), ordered(three.moveFloor("f-b", up = true)))
        assertEquals(listOf("f-a", "f-c", "f-b"), ordered(three.moveFloor("f-b", up = false)))
        assertEquals(three, three.moveFloor("f-a", up = true))     // 맨 위
        assertEquals(three, three.moveFloor("f-c", up = false))    // 맨 아래
        assertEquals(three, three.moveFloor("f-x", up = true))     // 없는 층
    }

    @Test
    fun removeFloor_dropsOnlyThatFloor_butNotTheLast() {
        assertEquals(listOf("f-c", "f-a"), three.removeFloor("f-b").floors.map { it.id })
        try {
            LayoutDto(1, floors = listOf(floor("f-a", 0, "t-a"))).removeFloor("f-a")
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun floorNameError_matchesServerRules() {
        assertEquals("층 이름을 입력해 주세요", floorNameError("  "))
        assertNull(floorNameError(" 2층 "))
        assertNull(floorNameError("가".repeat(20)))
        assertEquals("층 이름은 20자까지예요", floorNameError("가".repeat(21)))
    }

    @Test
    fun nextFloorName_smallestUnused() {
        assertEquals("1층", nextFloorName(emptyList()))
        assertEquals("2층", nextFloorName(listOf("1층", "루프탑")))
        assertEquals("2층", nextFloorName(listOf("1층", "3층")))
        assertEquals("3층", nextFloorName(listOf(" 1층", "2층 ")))
    }
}
