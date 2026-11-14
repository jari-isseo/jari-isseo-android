package com.jariisseo.android.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** #11: 서버 API (배치 저장은 #15, 상태 전환은 #18에서 추가). 실패는 apiCall로 감싸서 ApiException으로 */
interface JariApi {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): SignupResponse

    @GET("api/owner/store")
    suspend fun myStore(): MyStore

    /** #42: 보낸 필드만 수정. 응답은 GET과 같은 형식 */
    @PATCH("api/owner/store")
    suspend fun updateStore(@Body body: UpdateStoreRequest): MyStore

    /** #15: 저장한 적이 없으면 404 LAYOUT_NOT_FOUND */
    @GET("api/owner/store/layout")
    suspend fun getLayout(): LayoutDto

    /** #15: 항상 모든 층을 포함한 전체. 응답은 서버가 정리한 배치 + 새 version */
    @PUT("api/owner/store/layout")
    suspend fun saveLayout(@Body body: LayoutDto): LayoutDto

    /** #18: 없는 테이블이면 404 TABLE_NOT_FOUND */
    @PUT("api/owner/tables/{tableId}/status")
    suspend fun changeStatus(@Path("tableId") tableId: String, @Body body: UpdateStatusRequest): TableStatusResponse
}
