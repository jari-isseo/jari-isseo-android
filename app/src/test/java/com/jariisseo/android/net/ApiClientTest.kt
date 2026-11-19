package com.jariisseo.android.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** #11: 토큰 헤더·401 처리·에러 파싱 (가짜 서버) */
class ApiClientTest {
    private class FakeTokens(var value: String?, val failOnClear: Boolean = false) : TokenSource {
        override fun token() = value
        override fun clearIf(token: String?): Boolean {
            if (failOnClear) throw IOException("disk full")
            if (value != token) return false
            value = null
            return true
        }
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
    fun updateStore_patchesName_andParsesStore() = runTest {
        server.enqueue(json(200, """{"publicId":"k3x9q2mf","name":"카페 문 성수점","open":true,"shareUrl":"http://x/s/k3x9q2mf","hasLayout":true}"""))

        val store = apiCall { api.updateStore(UpdateStoreRequest("카페 문 성수점")) }

        assertEquals("카페 문 성수점", store.name)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/api/owner/store", req.path)
        assertEquals("""{"name":"카페 문 성수점"}""", req.body.readUtf8())
    }

    /** #23: 영업 상태만 보냄 (name은 null이라 JSON에 없음) */
    @Test
    fun updateStore_patchesOpenOnly() = runTest {
        server.enqueue(json(200, """{"publicId":"k3x9q2mf","name":"카페 문","open":false,"shareUrl":"http://x/s/k3x9q2mf","hasLayout":true}"""))

        val store = apiCall { api.updateStore(UpdateStoreRequest(open = false)) }

        assertEquals(false, store.open)
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/api/owner/store", req.path)
        assertEquals("""{"open":false}""", req.body.readUtf8())
    }

    /** #23: 전체 비우기 — 응답의 updatedAt·모르는 필드는 무시 */
    @Test
    fun resetStatuses_postsReset_andParsesSnapshot() = runTest {
        server.enqueue(
            json(
                200,
                """{"statuses":[{"tableId":"t-1","occupied":false,"updatedAt":"2026-11-16T09:00:00Z"},""" +
                    """{"tableId":"t-2","occupied":false,"updatedAt":"2026-11-16T09:00:00Z"}],"lastUpdatedAt":"2026-11-16T09:00:00Z","extra":1}""",
            ),
        )

        val res = apiCall { api.resetStatuses() }

        assertEquals(listOf(StatusSnapshot.Item("t-1", false), StatusSnapshot.Item("t-2", false)), res.statuses)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/owner/tables/status/reset", req.path)
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
    }

    @Test
    fun storeNameError_matchesServerRules() {
        assertEquals("매장 이름을 입력해 주세요", storeNameError(""))
        assertEquals("매장 이름을 입력해 주세요", storeNameError("   "))
        assertNull(storeNameError("  카페 문  "))
        assertNull(storeNameError("가".repeat(50)))
        assertNull(storeNameError(" " + "가".repeat(50) + " "))   // 앞뒤 공백은 떼고 보냄
        assertEquals("매장 이름은 50자까지예요", storeNameError("가".repeat(51)))
    }

    @Test
    fun changeStatus_putsOccupied_andParsesResponse() = runTest {
        server.enqueue(json(200, """{"tableId":"t-3f9a","occupied":true,"source":"MANUAL","updatedAt":"2026-11-12T11:30:00Z"}"""))

        val res = apiCall { api.changeStatus("t-3f9a", UpdateStatusRequest(true)) }

        assertEquals(TableStatusResponse("t-3f9a", true), res)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/owner/tables/t-3f9a/status", req.path)
        assertEquals("""{"occupied":true}""", req.body.readUtf8())
        assertEquals("Bearer tok-123", req.getHeader("Authorization"))
    }

    @Test
    fun changeStatus_404_isTableNotFound() = runTest {
        server.enqueue(json(404, """{"code":"TABLE_NOT_FOUND","message":"테이블을 찾을 수 없어요"}"""))

        try {
            apiCall { api.changeStatus("t-none", UpdateStatusRequest(false)) }
            fail()
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("TABLE_NOT_FOUND", e.code)
        }
        assertEquals(0, loggedOut)
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

    @Test
    fun successWithMissingFields_isInvalidResponse() = runTest {
        server.enqueue(json(200, "{}"))

        try {
            apiCall { api.myStore() }
            fail()
        } catch (e: ApiException) {
            assertEquals(INVALID_RESPONSE, e.code)
            assertEquals("잠시 후 다시 시도해 주세요", e.message)
        }
    }

    @Test
    fun ownerApi_401_tokenClearFails_stillLogsOutAndKeeps401() = runTest {
        tokens = FakeTokens("tok-123", failOnClear = true)
        api = ApiClient.create(server.url("/").toString(), tokens, { loggedOut++ })
        server.enqueue(json(401, """{"code":"UNAUTHORIZED","message":"로그인이 필요해요"}"""))

        try {
            apiCall { api.myStore() }
            fail()
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("UNAUTHORIZED", e.code)
        }
        assertEquals(1, loggedOut)
    }

    @Test
    fun ownerApi_late401_afterReLogin_keepsNewToken() = runTest {
        // 토큰 A로 보낸 요청이 401을 받기 전에 다시 로그인해서 B가 저장됨
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                tokens.value = "tok-new"
                return json(401, """{"code":"UNAUTHORIZED","message":"로그인이 필요해요"}""")
            }
        }

        try {
            apiCall { api.myStore() }
            fail()
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertEquals("tok-new", tokens.value)
        assertEquals(0, loggedOut)
    }
}
