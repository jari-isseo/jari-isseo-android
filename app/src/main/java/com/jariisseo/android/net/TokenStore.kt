package com.jariisseo.android.net

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// 파일: files/datastore/auth.preferences_pb (백업 제외: res/xml/backup_rules·data_extraction_rules)
private val Context.authDataStore by preferencesDataStore(name = "auth")
private val ACCESS_TOKEN = stringPreferencesKey("accessToken")
private val LOGIN_ID = stringPreferencesKey("loginId")   // #42: 가게 정보 화면 표시용 (서버 응답에 없음)

/** #11: 로그인 토큰 저장 (DataStore, 앱 전용 저장소) */
class TokenStore(context: Context) : TokenSource {
    private val store = context.applicationContext.authDataStore

    /** 토큰 (없으면 null). #12에서 앱 시작 시 로그인 여부 판단에 사용 */
    val tokenFlow: Flow<String?> = store.data.map { it[ACCESS_TOKEN] }

    /** #42: 로그인한 아이디 (이 기능 전에 로그인한 기기는 다시 로그인하기 전까지 null) */
    val loginIdFlow: Flow<String?> = store.data.map { it[LOGIN_ID] }

    suspend fun save(token: String, loginId: String) {
        store.edit {
            it[ACCESS_TOKEN] = token
            it[LOGIN_ID] = loginId
        }
    }

    /** #42: 로그아웃 */
    suspend fun delete() {
        store.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(LOGIN_ID)
        }
    }

    // AuthInterceptor용: OkHttp 백그라운드 스레드에서만 불리므로 blocking
    override fun token(): String? = runBlocking { tokenFlow.first() }

    override fun clearIf(token: String?): Boolean = runBlocking {
        var cleared = false
        store.edit {
            if (it[ACCESS_TOKEN] == token) {
                it.remove(ACCESS_TOKEN)
                it.remove(LOGIN_ID)
                cleared = true
            }
        }
        cleared
    }
}
