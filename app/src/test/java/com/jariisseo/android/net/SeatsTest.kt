package com.jariisseo.android.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #63: 홈 빈자리 수 계산 + 공개 스냅샷 파싱 */
class SeatsTest {
    private fun table(id: String) = TableDto(id, id, 1.0, 1.0)
    private fun floor(id: String, vararg tables: String) =
        FloorDto(id, id, 0, FloorShapeDto(listOf(listOf(0.0, 0.0), listOf(4.0, 0.0), listOf(4.0, 4.0))), tables = tables.map(::table))
    private fun occupied(id: String, on: Boolean = true) = StatusSnapshot.Item(id, on)

    private val layout = LayoutDto(floors = listOf(floor("f-1", "t-1", "t-2", "t-3"), floor("f-2", "t-4", "t-5")))

    @Test
    fun noLayout_isNull() {
        assertNull(seatCountOf(PublicSnapshot(layout = null)))
    }

    @Test
    fun noStatuses_allEmpty_acrossFloors() {
        assertEquals(SeatCount(empty = 5, total = 5), seatCountOf(PublicSnapshot(layout)))
    }

    @Test
    fun occupiedTables_areNotEmpty() {
        val snapshot = PublicSnapshot(layout, listOf(occupied("t-1"), occupied("t-4"), occupied("t-2", on = false)))
        assertEquals(SeatCount(empty = 3, total = 5), seatCountOf(snapshot))
    }

    @Test
    fun statusOfUnknownTable_isIgnored() {
        val snapshot = PublicSnapshot(layout, listOf(occupied("t-9")))
        assertEquals(SeatCount(empty = 5, total = 5), seatCountOf(snapshot))
    }

    @Test
    fun publicSnapshot_parsesServerFormat() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            val api = ApiClient.create(server.url("/").toString(), object : TokenSource {
                override fun token(): String? = null
                override fun clearIf(token: String?) = true
            }, {})
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    """
                    {"store":{"publicId":"s-abcd","name":"자리있어 데모점","open":true},
                     "layout":{"version":3,"unit":"m","floors":[
                       {"id":"f-1","name":"1층","order":0,"floor":{"polygon":[[0,0],[4,0],[4,4]]},"walls":[],
                        "tables":[{"id":"t-1","name":"1번","x":1,"z":1,"width":0.8,"depth":0.8,"rotation":0,"shape":"RECT"},
                                  {"id":"t-2","name":"2번","x":2,"z":1,"width":0.8,"depth":0.8,"rotation":0,"shape":"RECT"}],"chairs":[]}]},
                     "statuses":[{"tableId":"t-1","occupied":true,"updatedAt":"2026-11-16T10:00:00Z"}],
                     "lastUpdatedAt":"2026-11-16T10:00:00Z"}
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    """{"store":{"publicId":"s-abcd","name":"새 매장","open":false},"layout":null,"statuses":[],"lastUpdatedAt":null}""",
                ),
            )

            assertEquals(SeatCount(empty = 1, total = 2), seatCountOf(api.publicSnapshot("s-abcd")))
            assertEquals("/api/public/stores/s-abcd", server.takeRequest().path)
            assertNull(seatCountOf(api.publicSnapshot("s-abcd")))
        } finally {
            server.shutdown()
        }
    }
}
