package com.jariisseo.android.net

import okhttp3.Interceptor
import okhttp3.Response

/** 토큰 읽기·지우기 (실제는 TokenStore, 테스트는 가짜). OkHttp 스레드에서 불림 */
interface TokenSource {
    fun token(): String?
    fun clear()
}

/**
 * #11: 사장님 API(/api/owner 아래)에만 Authorization: Bearer 토큰을 붙이고, 그 응답이 401이면 토큰을 지우고 onUnauthorized.
 * 서버도 owner 경로에서만 토큰을 읽음. 로그인 실패(/api/auth/login의 401 INVALID_CREDENTIALS)는 로그아웃이 아님
 */
class AuthInterceptor(private val tokens: TokenSource, private val onUnauthorized: () -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (path != "/api/owner" && !path.startsWith("/api/owner/")) return chain.proceed(request)

        val token = tokens.token()
        val response = chain.proceed(
            if (token == null) request else request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
        if (response.code == 401) {
            tokens.clear()
            onUnauthorized()
        }
        return response
    }
}
