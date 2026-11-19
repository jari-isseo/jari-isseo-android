package com.jariisseo.android.net

/**
 * #12: 가입·로그인. 성공하면 받은 토큰을 저장 (가입도 응답 토큰으로 바로 로그인 상태).
 * 실패는 ApiException 그대로 (토큰 저장 안 함). #42: 아이디도 같이 저장 (가게 정보 화면 표시용)
 */
class AuthRepository(private val api: JariApi, private val saveToken: suspend (token: String, loginId: String) -> Unit) {
    suspend fun login(loginId: String, password: String) {
        val id = loginId.trim()
        val res = apiCall { api.login(LoginRequest(id, password)) }
        saveToken(res.accessToken, id)
    }

    suspend fun signup(loginId: String, password: String, storeName: String) {
        val id = loginId.trim()
        val res = apiCall { api.signup(SignupRequest(id, password, storeName)) }
        saveToken(res.accessToken, id)
    }
}

/**
 * 화면에 보여줄 문구. 서버 검증 에러는 "loginId: 아이디는 …"처럼 필드 이름이 앞에 붙어 와서 그 부분만 뗌.
 * 나머지는 서버 message 그대로
 */
fun ApiException.displayMessage(): String =
    if (code == "INVALID_REQUEST" && ": " in message) message.substringAfter(": ") else message
