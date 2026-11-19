package com.jariisseo.android.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/** #16: 데모 로그인 → 샘플 배치 전체 PUT → 상태 초기화 */
class DemoRepositoryTest {
    private lateinit var server: MockWebServer
    private var savedId: String? = null
    private lateinit var demo: DemoRepository

    // 단위 테스트는 app 모듈 폴더에서 실행됨
    private val sampleJson = File("src/main/assets/${Demo.LAYOUT_ASSET}").readText()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = ApiClient.create(server.url("/").toString(), object : TokenSource {
            override fun token() = "tok"
            override fun clearIf(token: String?) = true
        }, {})
        demo = DemoRepository(AuthRepository(api) { _, id -> savedId = id }, LayoutRepository(api))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun sampleLayout_parses() {
        val layout = Demo.parseLayout(sampleJson)
        assertEquals(listOf("1층", "2층"), layout.floors.map { it.name })
        assertEquals(11, layout.floors.sumOf { it.tables.size })
        assertEquals(6, layout.floors.first().toLayout().floor.size)   // ㄱ자 바닥
    }

    @Test
    fun start_logsInThenReplacesLayoutThenResetsStatuses() = runTest {
        server.enqueue(json(200, """{"accessToken":"tok-demo","expiresIn":2592000}"""))
        server.enqueue(json(200, sampleJson))
        server.enqueue(json(200, """{"statuses":[]}"""))

        demo.start(sampleJson)

        val login = server.takeRequest()
        assertEquals("/api/auth/login", login.path)
        assertTrue(login.body.readUtf8().contains("\"loginId\":\"${Demo.LOGIN_ID}\""))
        assertEquals(Demo.LOGIN_ID, savedId)

        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/owner/store/layout", put.path)
        val sent = ApiClient.json.decodeFromString(LayoutDto.serializer(), put.body.readUtf8())
        assertEquals(Demo.parseLayout(sampleJson).floors, sent.floors)

        val reset = server.takeRequest()
        assertEquals("POST", reset.method)
        assertEquals("/api/owner/tables/status/reset", reset.path)
    }

    @Test
    fun start_loginFails_doesNotTouchLayout() = runTest {
        server.enqueue(json(401, """{"code":"INVALID_CREDENTIALS","message":"아이디 또는 비밀번호가 맞지 않아요"}"""))

        try {
            demo.start(sampleJson)
            fail()
        } catch (e: ApiException) {
            assertEquals("INVALID_CREDENTIALS", e.code)
        }
        assertEquals(1, server.requestCount)
    }
}
