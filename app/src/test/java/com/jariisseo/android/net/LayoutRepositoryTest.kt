package com.jariisseo.android.net

import com.jariisseo.android.Layout
import com.jariisseo.android.TableItem
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** #15: 배치 불러오기(404 = 없음)·저장(모든 층 PUT)·검증 실패 */
class LayoutRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: LayoutRepository

    private val serverJson = """
        {"version":2,"unit":"m","floors":[
          {"id":"f-bbbb","name":"2층","order":1,"floor":{"polygon":[[0,0],[1,0],[1,1]]},"walls":[],
           "tables":[{"id":"t-9999","name":"9번","x":0.5,"z":0.5,"width":0.8,"depth":0.8,"rotation":0,"shape":"RECT"}],"chairs":[]},
          {"id":"f-aaaa","name":"1층","order":0,"floor":{"polygon":[[0,0],[4,0],[4,3]]},"walls":[{"id":"w1","from":[0,0],"to":[4,0]}],
           "tables":[{"id":"t-1111","name":"1번","x":1,"z":1,"width":0.8,"depth":0.8,"rotation":0,"shape":"RECT"}],"chairs":[]}
        ]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = ApiClient.create(server.url("/").toString(), object : TokenSource {
            override fun token() = "tok"
            override fun clearIf(token: String?) = true
        }, {})
        repo = LayoutRepository(api)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun load_notFound_isNull() = runTest {
        server.enqueue(json(404, """{"code":"LAYOUT_NOT_FOUND","message":"아직 저장된 배치가 없어요"}"""))

        assertNull(repo.load("f-aaaa"))
    }

    /** #25: 고른 층 (순서가 첫 층이 아니어도) */
    @Test
    fun load_returnsThatFloor() = runTest {
        server.enqueue(json(200, serverJson))

        val layout = repo.load("f-bbbb")!!

        assertEquals("f-bbbb", layout.floorId)
        assertEquals(listOf("t-9999"), layout.tables.map { it.id })
    }

    /** #25: 층 목록은 order 순, 배치가 없으면 빈 목록 */
    @Test
    fun floors_sortedByOrder_orEmpty() = runTest {
        server.enqueue(json(200, serverJson))
        server.enqueue(json(404, """{"code":"LAYOUT_NOT_FOUND","message":"아직 저장된 배치가 없어요"}"""))

        assertEquals(listOf("1층", "2층"), repo.floors().map { it.name })
        assertEquals(emptyList<FloorDto>(), repo.floors())
    }

    /** #25: 새 층 저장 → PUT에 기존 2개 층 + 새 층, 반환은 서버에 생긴 그 층 */
    @Test
    fun save_newFloor_appendsThirdFloor() = runTest {
        val newFloor = Layout("f-cccc", listOf(0f to 0f, 2f to 0f, 2f to 2f), emptyList(), listOf(TableItem("t-3333", "1번", 1f, 1f)), emptyList())
        server.enqueue(json(200, serverJson))
        server.enqueue(
            json(
                200,
                serverJson.replace(
                    "\n]}",
                    """,{"id":"f-cccc","name":"루프탑","order":2,"floor":{"polygon":[[0,0],[2,0],[2,2]]},"walls":[],"tables":[{"id":"t-3333","name":"1번","x":1,"z":1}],"chairs":[]}
]}""",
                ),
            ),
        )

        val saved = repo.save(newFloor, FloorTarget.New("루프탑"))

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"f-aaaa\"") && body.contains("\"f-bbbb\"") && body.contains("\"f-cccc\""))
        assertTrue(body.contains("\"name\":\"루프탑\"") && body.contains("\"order\":2"))
        assertEquals("f-cccc", saved.floorId)
    }

    @Test
    fun remove_putsWithoutThatFloor() = runTest {
        server.enqueue(json(200, serverJson))
        server.enqueue(json(200, serverJson))

        repo.remove("f-bbbb")

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"f-aaaa\"") && !body.contains("\"f-bbbb\""))
    }

    @Test
    fun move_swapsOrders() = runTest {
        server.enqueue(json(200, serverJson))
        server.enqueue(json(200, serverJson))

        repo.move("f-bbbb", up = true)

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        // f-bbbb(2층)가 order 0, f-aaaa(1층)가 order 1
        assertTrue(body.contains("\"id\":\"f-bbbb\",\"name\":\"2층\",\"order\":0"))
        assertTrue(body.contains("\"id\":\"f-aaaa\",\"name\":\"1층\",\"order\":1"))
    }

    @Test
    fun remove_lastFloor_throwsApiException_withoutPut() = runTest {
        server.enqueue(json(200, """{"version":1,"unit":"m","floors":[{"id":"f-aaaa","name":"1층","order":0,"floor":{"polygon":[[0,0],[1,0],[1,1]]}}]}"""))

        try {
            repo.remove("f-aaaa")
            fail()
        } catch (e: ApiException) {
            assertEquals("LAST_FLOOR", e.code)
        }
        assertEquals(1, server.requestCount)
    }

    private val edited = Layout(
        "f-aaaa", listOf(0f to 0f, 4f to 0f, 4f to 3f), emptyList(),
        listOf(TableItem("t-1111", "1번", 2f, 1f)), emptyList(),
    )

    @Test
    fun save_putsAllFloors() = runTest {
        server.enqueue(json(200, serverJson))   // 저장 직전 최신 서버 배치
        server.enqueue(json(200, serverJson))   // PUT 응답

        repo.save(edited, FloorTarget.Existing("f-aaaa", "1층"))

        server.takeRequest()   // GET
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/owner/store/layout", put.path)
        val body = put.body.readUtf8()
        assertTrue(body.contains("\"f-bbbb\"") && body.contains("\"t-9999\""))   // 다른 층도 함께
        assertTrue(body.contains("\"x\":2.0"))
    }

    @Test
    fun save_refetchesBeforePut_keepsOtherFloorChangedMeanwhile() = runTest {
        // 확인 창이 떠 있는 동안 다른 층(f-bbbb)에 테이블 t-8888이 추가됨
        server.enqueue(json(200, serverJson))                                        // resetsStatus 확인
        server.enqueue(json(200, serverJson.replace("\"t-9999\"", "\"t-8888\"")))   // save 직전 GET
        server.enqueue(json(200, serverJson))                                        // PUT 응답

        repo.resetsStatus(edited, FloorTarget.Existing("f-aaaa", "1층"))
        repo.save(edited, FloorTarget.Existing("f-aaaa", "1층"))

        server.takeRequest()
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"t-8888\"") && !body.contains("\"t-9999\""))
    }

    @Test
    fun save_invalidLayout_throwsServerMessage() = runTest {
        server.enqueue(json(404, """{"code":"LAYOUT_NOT_FOUND","message":"아직 저장된 배치가 없어요"}"""))
        server.enqueue(json(400, """{"code":"INVALID_LAYOUT","message":"1층: 테이블 이름은 1~20자여야 해요"}"""))

        try {
            repo.save(Layout("f-aaaa", emptyList(), emptyList(), emptyList(), emptyList()), FloorTarget.New("1층"))
            fail()
        } catch (e: ApiException) {
            assertEquals("INVALID_LAYOUT", e.code)
            assertEquals("1층: 테이블 이름은 1~20자여야 해요", e.displayMessage())
        }
    }
}
