package com.zhusixing.medication

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zhusixing.medication.data.DataStoreSessionStore
import com.zhusixing.medication.data.Session
import com.zhusixing.medication.data.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionStoreTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val directory = Files.createTempDirectory("session-store").toFile()
    private val session = Session("http://10.0.2.2:8080/", "token-1", UserProfile(3, "alice", "小朱"))

    private fun dataStore() = PreferenceDataStoreFactory.create(scope = scope) {
        File(directory, "session.preferences_pb")
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun sessionRoundTripsThroughPreferences() = runTest {
        val store = dataStore()
        DataStoreSessionStore(store).save(session)
        assertEquals(session, DataStoreSessionStore(store).load())
    }

    @Test
    fun clearRemovesEveryFieldSoNextStartNeedsLogin() = runTest {
        val store = dataStore()
        val sessionStore = DataStoreSessionStore(store)
        sessionStore.save(session)
        sessionStore.clear()
        assertNull(sessionStore.load())
    }

    @Test
    fun halfWrittenSessionIsTreatedAsAbsent() = runTest {
        val store = dataStore()
        // 只有地址没有令牌的残缺数据不能让界面以为已登录。
        store.edit { it[stringPreferencesKey("session.base_url")] = "http://10.0.2.2:8080/" }
        assertNull(DataStoreSessionStore(store).load())
    }
}
