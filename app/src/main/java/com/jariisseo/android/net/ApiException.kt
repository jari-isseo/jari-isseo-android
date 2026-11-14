package com.jariisseo.android.net

import retrofit2.HttpException
import java.io.IOException

/**
 * #11: API 실패. code = 서버 에러 코드(API 정의서 7장) 또는 NETWORK, message = 화면에 그대로 보여줄 한국어 문장.
 * status = HTTP 상태 (연결 실패면 0)
 */
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)

const val NETWORK_ERROR = "NETWORK"

/** API 호출을 감싸서 실패를 ApiException 하나로 (HTTP 에러는 { code, message } 파싱) */
suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        throw e.toApiException()
    } catch (e: IOException) {
        throw ApiException(0, NETWORK_ERROR, "서버에 연결할 수 없어요")
    }

private fun HttpException.toApiException(): ApiException {
    val body = runCatching {
        response()?.errorBody()?.string()?.let { ApiClient.json.decodeFromString<ErrorBody>(it) }
    }.getOrNull()
    // 서버가 아닌 곳(프록시 등)에서 온 에러는 형식이 다를 수 있음
    return ApiException(code(), body?.code ?: "HTTP_${code()}", body?.message ?: "잠시 후 다시 시도해 주세요")
}
