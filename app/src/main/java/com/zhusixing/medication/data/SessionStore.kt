package com.zhusixing.medication.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 会话的本地持久化。
 *
 * 只存恢复登录所必需的字段：服务器地址、令牌、用户标识与显示名。
 * 密码不写入任何持久化位置——它只在登录请求的生命周期内存在。
 */
interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

/** 不做任何持久化的实现，用于预览与单元测试。 */
class InMemorySessionStore(private var session: Session? = null) : SessionStore {
    override suspend fun load(): Session? = session
    override suspend fun save(session: Session) { this.session = session }
    override suspend fun clear() { session = null }
}

class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore {

    override suspend fun load(): Session? {
        val preferences = dataStore.data.first()
        val baseUrl = preferences[BASE_URL]?.takeIf { it.isNotBlank() } ?: return null
        val token = preferences[TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        val userId = preferences[USER_ID] ?: return null
        return Session(
            baseUrl = baseUrl,
            token = token,
            user = UserProfile(
                id = userId,
                account = preferences[ACCOUNT],
                nickname = preferences[NICKNAME],
            ),
        )
    }

    override suspend fun save(session: Session) {
        dataStore.edit { preferences ->
            preferences[BASE_URL] = session.baseUrl
            preferences[TOKEN] = session.token
            preferences[USER_ID] = session.user.id
            preferences[ACCOUNT] = session.user.account.orEmpty()
            preferences[NICKNAME] = session.user.nickname.orEmpty()
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("session.base_url")
        val TOKEN = stringPreferencesKey("session.token")
        val USER_ID = longPreferencesKey("session.user_id")
        val ACCOUNT = stringPreferencesKey("session.account")
        val NICKNAME = stringPreferencesKey("session.nickname")
    }
}
