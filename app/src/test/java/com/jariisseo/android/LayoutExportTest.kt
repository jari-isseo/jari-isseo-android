package com.jariisseo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LayoutExportTest {
    /** 정해진 값을 차례로 돌려주는 Random (ID 중복 재추첨 확인용) */
    private class FixedRandom(private val values: IntArray) : Random() {
        private var i = 0
        override fun nextBits(bitCount: Int): Int = error("unused")
        override fun nextInt(until: Int): Int = values[i++ % values.size]
    }

    private val state = ArUiState(
        floorPolygon = listOf(0f to 0f, 4f to 0f, 4f to 3f, 0f to 3f),
        walls = listOf((0f to 0f) to (4f to 0f), (0f to 0f) to (0f to 3f)),
        mapItems = listOf(
            MapItem("테이블", 1.234f, 1.5f, 5, confirmed = true),
            MapItem("테이블", 3f, 1.5f, 5, confirmed = true),
            MapItem("테이블", 2f, 2f, 1, confirmed = false),
            MapItem("의자", 0.5f, 1.5f, 5, confirmed = true),
            MapItem("의자", 3.5f, 1.5f, 1, confirmed = false),
            MapItem("소파", 2f, 2.5f, 5, confirmed = true),
        ),
    )

    private fun ids(json: String, prefix: String) =
        Regex("\"id\": \"($prefix-[0-9a-z]{4})\"").findAll(json).map { it.groupValues[1] }.toList()

    @Test
    fun sampleShape_confirmedTablesAndChairsOnly() {
        val json = toLayoutJson(state)

        assertTrue(json.contains("\"version\": 3"))
        assertTrue(json.contains("\"unit\": \"m\""))
        assertEquals(1, ids(json, "f").size)
        assertTrue(json.contains("\"name\": \"1층\", \"order\": 0"))
        assertTrue(json.contains("\"polygon\": [[0.0, 0.0], [4.0, 0.0], [4.0, 3.0], [0.0, 3.0]]"))
        assertTrue(json.contains("{\"id\": \"w1\", \"from\": [0.0, 0.0], \"to\": [4.0, 0.0]}"))
        assertTrue(json.contains("{\"id\": \"w2\", \"from\": [0.0, 0.0], \"to\": [0.0, 3.0]}"))
        assertEquals(2, ids(json, "t").size)
        assertEquals(1, ids(json, "c").size)
        assertFalse(json.contains("소파"))
        // 이름은 1번부터, 좌표는 cm 반올림, 크기는 0.8 RECT 고정
        assertTrue(json.contains("\"name\": \"1번\", \"x\": 1.23, \"z\": 1.5, \"width\": 0.8, \"depth\": 0.8, \"rotation\": 0, \"shape\": \"RECT\""))
        assertTrue(json.contains("\"name\": \"2번\", \"x\": 3.0"))
    }

    @Test
    fun duplicateRandomId_isRedrawn() {
        // f-0000, t-0000, t-0000(중복) → t-1111
        val random = FixedRandom(intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1))
        val twoTables = state.copy(mapItems = state.mapItems.filter { it.label == "테이블" && it.confirmed })

        assertEquals(listOf("t-0000", "t-1111"), ids(toLayoutJson(twoTables, random), "t"))
    }

    @Test
    fun emptyScan_hasEmptyArrays() {
        val json = toLayoutJson(ArUiState())

        assertTrue(json.contains("\"polygon\": []"))
        assertTrue(json.contains("\"walls\": []"))
        assertTrue(json.contains("\"tables\": []"))
        assertTrue(json.contains("\"chairs\": []"))
    }
}
