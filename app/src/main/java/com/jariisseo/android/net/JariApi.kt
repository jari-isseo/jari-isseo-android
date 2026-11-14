package com.jariisseo.android.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** #11: 서버 API (배치 저장은 #15, 상태 전환은 #18에서 추가). 실패는 apiCall로 감싸서 ApiException으로 */
interface JariApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): SignupResponse

    @GET("api/owner/store")
    suspend fun myStore(): MyStore
}
