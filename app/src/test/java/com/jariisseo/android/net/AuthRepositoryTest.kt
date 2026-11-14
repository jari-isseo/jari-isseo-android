package com.jariisseo.android.net

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** #12: 가입·로그인 성공 시에만 토큰 저장, 에러 문구 */
class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private var saved: String? = null
    private lateinit var auth: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = ApiClient.create(server.url("/").toString(), object : TokenSource {
            override fun token(): String? = null
            override fun clearIf(token: String?) = true
        }, {})
        auth = AuthRepository(api) { saved = it }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private suspend fun failure(block: suspend () -> Unit): ApiException =
        try {
            block()
            fail()
            throw AssertionError()
        } catch (e: ApiException) {
            e
        }

    @Test
    fun login_success_savesToken_andTrimsId() = runTest {
        server.enqueue(json(200, """{"accessToken":"tok-login","expiresIn":2592000}"""))

        auth.login("  cafe_moon ", "password1")

        assertEquals("tok-login", saved)
        assertEquals("""{"loginId":"cafe_moon","password":"password1"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun login_wrongPassword_doesNotSave() = runTest {
        server.enqueue(json(401, """{"code":"INVALID_CREDENTIALS","message":"아이디 또는 비밀번호가 맞지 않아요"}"""))

        val e = failure { auth.login("cafe_moon", "wrong") }

        assertEquals("아이디 또는 비밀번호가 맞지 않아요", e.displayMessage())
        assertNull(saved)
    }

    @Test
    fun signup_success_savesSignupToken() = runTest {
        server.enqueue(json(201, """{"accessToken":"tok-signup","store":{"publicId":"k3x9q2mf","name":"카페 문"}}"""))

        auth.signup("cafe_moon", "password1", "카페 문")

        assertEquals("tok-signup", saved)
    }

    @Test
    fun signup_takenId_doesNotSave() = runTest {
        server.enqueue(json(409, """{"code":"LOGIN_ID_TAKEN","message":"이미 사용 중인 아이디예요"}"""))

        val e = failure { auth.signup("cafe_moon", "password1", "카페 문") }

        assertEquals("이미 사용 중인 아이디예요", e.displayMessage())
        assertNull(saved)
    }

    @Test
    fun displayMessage_stripsFieldNameOnlyForValidationErrors() {
        assertEquals(
            "아이디는 영문 소문자·숫자·_ 4~20자예요",
            ApiException(400, "INVALID_REQUEST", "loginId: 아이디는 영문 소문자·숫자·_ 4~20자예요").displayMessage(),
        )
        assertEquals("요청 값이 올바르지 않아요", ApiException(400, "INVALID_REQUEST", "요청 값이 올바르지 않아요").displayMessage())
        assertEquals("로그인이 필요해요", ApiException(401, "UNAUTHORIZED", "로그인이 필요해요").displayMessage())
    }
}
