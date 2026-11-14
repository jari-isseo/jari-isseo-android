package com.jariisseo.android.net

import android.content.Context
import com.jariisseo.android.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** #11: Retrofit + OkHttp 만들기 */
object ApiClient {
    /** 서버가 필드를 추가해도 앱이 깨지지 않게 모르는 키는 무시 */
    val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String, tokens: TokenSource, onUnauthorized: () -> Unit, log: Boolean = false): JariApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens, onUnauthorized))
            .apply {
                // 주소·상태 코드만 (헤더·본문에는 토큰·비밀번호가 있어 남기지 않음)
                if (log) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JariApi::class.java)
    }
}

/**
 * #11: 앱 전체에서 하나. MainActivity.onCreate에서 init.
 * 서버 주소는 빌드 타입별 BuildConfig.BASE_URL (debug: local.properties devServerUrl / release: https)
 */
object Server {
    lateinit var tokens: TokenStore
        private set
    lateinit var api: JariApi
        private set

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 사장님 API가 401 (토큰 없음·만료) → 토큰은 이미 지워짐, 로그인 화면으로 */
    val loggedOut: SharedFlow<Unit> = _loggedOut

    fun init(context: Context) {
        if (::api.isInitialized) return
        tokens = TokenStore(context)
        api = ApiClient.create(BuildConfig.BASE_URL, tokens, { _loggedOut.tryEmit(Unit) }, log = BuildConfig.DEBUG)
    }
}
