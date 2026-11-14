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

/** #42: PATCH /api/owner/store — 이름만 (영업 상태 open은 #23) */
@Serializable
data class UpdateStoreRequest(val name: String)

/** 서버 검증과 같은 기준 (앞뒤 공백 제거 후 1~50자). 문제없으면 null */
fun storeNameError(name: String): String? = when {
    name.isBlank() -> "매장 이름을 입력해 주세요"
    name.trim().length > 50 -> "매장 이름은 50자까지예요"
    else -> null
}

/** #18: PUT /api/owner/tables/{tableId}/status — 토글이 아니라 바꿀 값 */
@Serializable
data class UpdateStatusRequest(val occupied: Boolean)

/** #18: 200 (source·updatedAt은 쓰지 않아 생략) */
@Serializable
data class TableStatusResponse(val tableId: String, val occupied: Boolean)

/** 모든 에러 응답: { "code": "TABLE_NOT_FOUND", "message": "테이블을 찾을 수 없어요" } */
@Serializable
data class ErrorBody(val code: String, val message: String)
