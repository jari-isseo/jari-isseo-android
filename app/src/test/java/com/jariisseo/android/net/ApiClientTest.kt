package com.jariisseo.android.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** #11: 토큰 헤더·401 처리·에러 파싱 (가짜 서버) */
class ApiClientTest {
    private class FakeTokens(var value: String?) : TokenSource {
        override fun token() = value
        override fun clear() { value = null }
    }

    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokens
    private var loggedOut = 0
    private lateinit var api: JariApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = FakeTokens("tok-123")
        api = ApiClient.create(server.url("/").toString(), tokens, { loggedOut++ })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun ownerApi_sendsBearerToken_andParsesStore() = runTest {
        server.enqueue(json(200, """{"publicId":"k3x9q2mf","name":"카페 문","open":true,"shareUrl":"http://x/s/k3x9q2mf","hasLayout":false,"extra":1}"""))

        val store = apiCall { api.myStore() }

        assertEquals("카페 문", store.name)
        val req = server.takeRequest()
        assertEquals("/api/owner/store", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
    }

    @Test
    fun authApi_hasNoToken() = runTest {
        server.enqueue(json(200, """{"accessToken":"new","expiresIn":2592000}"""))

        val res = apiCall { api.login(LoginRequest("cafe_moon", "password1")) }

        assertEquals("new", res.accessToken)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun ownerApi_401_clearsTokenAndLogsOut() = runTest {
        server.enqueue(json(401, """{"code":"UNAUTHORIZED","message":"로그인이 필요해요"}"""))

        try {
            apiCall { api.myStore() }
            fail()
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("UNAUTHORIZED", e.code)
            assertEquals("로그인이 필요해요", e.message)
        }
        assertNull(tokens.value)
        assertEquals(1, loggedOut)
    }

    @Test
    fun loginFailure_401_keepsTokenAndDoesNotLogOut() = runTest {
        server.enqueue(json(401, """{"code":"INVALID_CREDENTIALS","message":"아이디 또는 비밀번호가 맞지 않아요"}"""))

        try {
            apiCall { api.login(LoginRequest("cafe_moon", "wrong")) }
            fail()
        } catch (e: ApiException) {
            assertEquals("INVALID_CREDENTIALS", e.code)
        }
        assertEquals("tok-123", tokens.value)
        assertEquals(0, loggedOut)
    }

    @Test
    fun nonJsonError_fallsBackToGenericMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))

        try {
            apiCall { api.myStore() }
            fail()
        } catch (e: ApiException) {
            assertEquals(502, e.status)
            assertEquals("HTTP_502", e.code)
            assertEquals("잠시 후 다시 시도해 주세요", e.message)
        }
        assertEquals(0, loggedOut)
    }

    @Test
    fun connectionFailure_isNetworkError() = runTest {
        server.shutdown()

        try {
            apiCall { api.myStore() }
            fail()
        } catch (e: ApiException) {
            assertEquals(NETWORK_ERROR, e.code)
            assertEquals(0, e.status)
        }
        assertFalse(tokens.value == null)
        assertTrue(loggedOut == 0)
    }
}
