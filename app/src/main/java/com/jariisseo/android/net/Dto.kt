package com.jariisseo.android.net

import kotlinx.serialization.Serializable

// #11: 서버 요청·응답 (API 정의서 3·4·7장, server `auth/dto`·`store/dto`와 같은 모양)

@Serializable
data class LoginRequest(val loginId: String, val password: String)

/** 200: 토큰 유효기간 expiresIn(초) = 30일 */
@Serializable
data class LoginResponse(val accessToken: String, val expiresIn: Long)

@Serializable
data class SignupRequest(val loginId: String, val password: String, val storeName: String)

/** 201: 가입과 동시에 매장 생성, 토큰으로 바로 사장님 API 사용 가능 */
@Serializable
data class SignupResponse(val accessToken: String, val store: StoreSummary) {
    @Serializable
    data class StoreSummary(val publicId: String, val name: String)
}

/** GET /api/owner/store */
@Serializable
data class MyStore(
    val publicId: String,
    val name: String,
    val open: Boolean,
    val shareUrl: String,
    val hasLayout: Boolean,
)

/** 모든 에러 응답: { "code": "TABLE_NOT_FOUND", "message": "테이블을 찾을 수 없어요" } */
@Serializable
data class ErrorBody(val code: String, val message: String)
