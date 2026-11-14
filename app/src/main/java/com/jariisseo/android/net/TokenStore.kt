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

/** #11: 로그인 토큰 저장 (DataStore, 앱 전용 저장소) */
class TokenStore(context: Context) : TokenSource {
    private val store = context.applicationContext.authDataStore

    /** 토큰 (없으면 null). #12에서 앱 시작 시 로그인 여부 판단에 사용 */
    val tokenFlow: Flow<String?> = store.data.map { it[ACCESS_TOKEN] }

    suspend fun save(token: String) {
        store.edit { it[ACCESS_TOKEN] = token }
    }

    suspend fun delete() {
        store.edit { it.remove(ACCESS_TOKEN) }
    }

    // AuthInterceptor용: OkHttp 백그라운드 스레드에서만 불리므로 blocking
    override fun token(): String? = runBlocking { tokenFlow.first() }

    override fun clear() = runBlocking { delete() }
}
