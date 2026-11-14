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

        assertNull(repo.load())
    }

    @Test
    fun load_returnsCurrentFloor() = runTest {
        server.enqueue(json(200, serverJson))

        val layout = repo.load()!!

        assertEquals("f-aaaa", layout.floorId)
        assertEquals(listOf("t-1111"), layout.tables.map { it.id })
    }

    @Test
    fun prepareAndSave_putsAllFloors() = runTest {
        server.enqueue(json(200, serverJson))   // prepare: 최신 서버 배치
        server.enqueue(json(200, serverJson))   // save 응답
        val edited = Layout(
            "f-aaaa", listOf(0f to 0f, 4f to 0f, 4f to 3f), emptyList(),
            listOf(TableItem("t-1111", "1번", 2f, 1f)), emptyList(),
        )

        val pending = repo.prepare(edited)
        repo.save(pending, edited.floorId)

        server.takeRequest()   // GET
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/owner/store/layout", put.path)
        val body = put.body.readUtf8()
        assertTrue(body.contains("\"f-bbbb\"") && body.contains("\"t-9999\""))   // 다른 층도 함께
        assertTrue(body.contains("\"x\":2.0"))
    }

    @Test
    fun save_invalidLayout_throwsServerMessage() = runTest {
        server.enqueue(json(400, """{"code":"INVALID_LAYOUT","message":"1층: 테이블 이름은 1~20자여야 해요"}"""))
        val pending = withFloor(null, Layout("f-aaaa", emptyList(), emptyList(), emptyList(), emptyList()))

        try {
            repo.save(pending, "f-aaaa")
            fail()
        } catch (e: ApiException) {
            assertEquals("INVALID_LAYOUT", e.code)
            assertEquals("1층: 테이블 이름은 1~20자여야 해요", e.displayMessage())
        }
    }
}
