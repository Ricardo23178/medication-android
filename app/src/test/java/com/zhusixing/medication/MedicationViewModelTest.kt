package com.zhusixing.medication

import com.zhusixing.medication.data.InMemorySessionStore
import com.zhusixing.medication.data.MedicationRepository
import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.data.RepositoryException
import com.zhusixing.medication.data.Session
import com.zhusixing.medication.data.UserProfile
import com.zhusixing.medication.ui.MedicationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val session = Session("http://localhost:8080/", "token-a", UserProfile(1, "alice", "小朱"))
    private val waiting = RemindTask(id = 9, medicineName = "演示药品", status = "WAITING", confirmed = false)

    @Test fun loginLoadsTasksWithoutReorderingOrDroppingEarlierDates() = runTest {
        val earlier = waiting.copy(id = 8, scheduledAt = "2026-10-29T23:50:00")
        val fake = FakeRepository(session).apply { load = { listOf(earlier, waiting) } }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        assertTrue(vm.state.value.loading)
        advanceUntilIdle()
        assertEquals(session, vm.state.value.session)
        assertEquals(listOf(earlier, waiting), vm.state.value.tasks)
        assertEquals(session, fake.loadedSession)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test fun wrongPasswordStaysOnLoginAndShowsBusinessError() = runTest {
        val fake = FakeRepository(session).apply {
            signIn = { _, _, _ -> throw RepositoryException("账号或密码错误", sessionExpired = true) }
        }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "wrong")
        advanceUntilIdle()
        assertNull(vm.state.value.session)
        assertEquals("账号或密码错误", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun blankCredentialsDoNotSendRequest() = runTest {
        val fake = FakeRepository(session)
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "  ", "password")
        advanceUntilIdle()
        assertEquals(0, fake.loginCount)
        assertNotNull(vm.state.value.error)
    }

    @Test fun emptyTasksAreSuccessfulEmptyState() = runTest {
        val vm = MedicationViewModel(FakeRepository(session))
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        assertEquals(session, vm.state.value.session)
        assertTrue(vm.state.value.tasks.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun loadFailureKeepsSessionAndRefreshCanRecover() = runTest {
        val fake = FakeRepository(session).apply { load = { throw RepositoryException("无法连接后端") } }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        assertEquals(session, vm.state.value.session)
        assertEquals("无法连接后端", vm.state.value.error)
        fake.load = { listOf(waiting) }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf(waiting), vm.state.value.tasks)
        assertNull(vm.state.value.error)
    }

    @Test fun repeatedTapOnlySubmitsOnceAndDisplaysReturnedAction() = runTest {
        val gate = CompletableDeferred<RemindTask>()
        val fake = FakeRepository(session).apply {
            load = { listOf(waiting) }
            submit = { _, _, _, _ -> gate.await() }
        }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        vm.confirm(waiting.id, "TAKEN")
        vm.confirm(waiting.id, "SKIPPED")
        runCurrent()
        assertEquals(waiting.id, vm.state.value.busyTaskId)
        assertEquals(1, fake.confirmations.size)
        val returned = waiting.copy(status = "CONFIRMED", confirmed = true, action = "SKIPPED")
        gate.complete(returned)
        advanceUntilIdle()
        assertEquals(returned, vm.state.value.tasks.single())
        assertTrue(vm.state.value.feedback.orEmpty().contains("跳过"))
        assertNull(vm.state.value.busyTaskId)
        vm.confirm(waiting.id, "TAKEN")
        advanceUntilIdle()
        assertEquals(1, fake.confirmations.size)
    }

    @Test fun terminalAndUnknownTasksCannotBeConfirmed() = runTest {
        val tasks = listOf(
            waiting.copy(id = 1, status = "EXPIRED"),
            waiting.copy(id = 2, status = "CANCELLED"),
            waiting.copy(id = 3, status = "NEW_STATUS"),
            waiting.copy(id = 4, confirmed = true),
        )
        val fake = FakeRepository(session).apply { load = { tasks } }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        tasks.forEach { vm.confirm(it.id, "TAKEN") }
        vm.confirm(500, "TAKEN")
        advanceUntilIdle()
        assertTrue(fake.confirmations.isEmpty())
    }

    @Test fun unsupportedActionDoesNotSendRequest() = runTest {
        val fake = FakeRepository(session).apply { load = { listOf(waiting) } }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        vm.confirm(waiting.id, "DELAYED")
        advanceUntilIdle()
        assertTrue(fake.confirmations.isEmpty())
    }

    @Test fun uncertainFailureRetriesSameIntentAndBlocksConflictingAction() = runTest {
        val fake = FakeRepository(session).apply {
            load = { listOf(waiting) }
            submit = { _, _, _, _ -> throw RepositoryException("网络中断，结果待核对", outcomeUnknown = true) }
        }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        vm.confirm(waiting.id, "TAKEN")
        advanceUntilIdle()
        val pending = vm.state.value.pendingConfirmation
        assertNotNull(pending)
        assertTrue(pending!!.requestId.isNotBlank())
        assertEquals("TAKEN", pending.action)
        vm.confirm(waiting.id, "SKIPPED")
        advanceUntilIdle()
        assertEquals(1, fake.confirmations.size)
        fake.submit = { _, _, _, _ -> waiting.copy(status = "CONFIRMED", confirmed = true, action = "TAKEN") }
        vm.retryConfirmation()
        advanceUntilIdle()
        assertEquals(2, fake.confirmations.size)
        assertEquals(fake.confirmations[0], fake.confirmations[1])
        assertNull(vm.state.value.pendingConfirmation)
        assertNull(vm.state.value.error)
        assertEquals("TAKEN", vm.state.value.tasks.single().action)
    }

    @Test fun refreshRetainsUncertainIntentWhilePendingAndClearsItWhenResolved() = runTest {
        val fake = FakeRepository(session).apply {
            load = { listOf(waiting) }
            submit = { _, _, _, _ -> throw RepositoryException("结果待核对", outcomeUnknown = true) }
        }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        vm.confirm(waiting.id, "TAKEN")
        advanceUntilIdle()
        val pending = vm.state.value.pendingConfirmation
        assertNotNull(pending)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(pending, vm.state.value.pendingConfirmation)
        fake.load = { listOf(waiting.copy(status = "CONFIRMED", confirmed = true, action = "SKIPPED")) }
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.pendingConfirmation)
        assertEquals("SKIPPED", vm.state.value.tasks.single().action)
    }

    @Test fun definitiveBusinessFailureDoesNotPretendConfirmationSucceeded() = runTest {
        val fake = FakeRepository(session).apply {
            load = { listOf(waiting) }
            submit = { _, _, _, _ -> throw RepositoryException("任务已超时，请刷新") }
        }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        vm.confirm(waiting.id, "TAKEN")
        advanceUntilIdle()
        assertEquals(listOf(waiting), vm.state.value.tasks)
        assertEquals("任务已超时，请刷新", vm.state.value.error)
        assertNull(vm.state.value.feedback)
        assertNull(vm.state.value.pendingConfirmation)
    }

    @Test fun expiredSessionClearsTasksAndReturnsToLogin() = runTest {
        val fake = FakeRepository(session).apply { load = { listOf(waiting) } }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        fake.load = { throw RepositoryException("登录已失效，请重新登录", sessionExpired = true) }
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.tasks.isEmpty())
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun logoutRejectsLateResponseEvenIfDependencyIgnoresCancellation() = runTest {
        val gate = CompletableDeferred<List<RemindTask>>()
        val fake = FakeRepository(session).apply { load = { withContext(NonCancellable) { gate.await() } } }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        runCurrent()
        assertEquals(session, vm.state.value.session)
        vm.logout()
        gate.complete(listOf(waiting))
        advanceUntilIdle()
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.tasks.isEmpty())
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.feedback)
    }

    @Test fun oldUnauthorizedResponseDoesNotClearNewLogin() = runTest {
        val gate = CompletableDeferred<Unit>()
        val next = session.copy(token = "token-b", user = UserProfile(2, "bob", "新账号"))
        val fake = FakeRepository(session).apply {
            load = {
                if (it == session) withContext(NonCancellable) {
                    gate.await()
                    throw RepositoryException("旧请求登录失效", sessionExpired = true)
                } else listOf(waiting.copy(id = 88))
            }
        }
        val vm = MedicationViewModel(fake)
        vm.login(session.baseUrl, "alice", "password")
        runCurrent()
        vm.logout()
        fake.signIn = { _, _, _ -> next }
        vm.login(next.baseUrl, "bob", "password")
        runCurrent()
        assertEquals(next, vm.state.value.session)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(next, vm.state.value.session)
        assertEquals(88L, vm.state.value.tasks.single().id)
        assertNull(vm.state.value.error)
    }

    @Test fun savedSessionIsRestoredOnColdStart() = runTest {
        val store = InMemorySessionStore(session)
        val fake = FakeRepository(session).apply { load = { listOf(waiting) } }
        val vm = MedicationViewModel(fake, store)
        assertTrue(vm.state.value.restoring)
        advanceUntilIdle()
        assertEquals(session, vm.state.value.session)
        assertEquals(listOf(waiting), vm.state.value.tasks)
        assertFalse(vm.state.value.restoring)
        assertFalse(vm.state.value.loading)
    }

    @Test fun expiredSavedSessionFallsBackToLoginAndClearsStorage() = runTest {
        val store = InMemorySessionStore(session)
        val fake = FakeRepository(session).apply {
            load = { throw RepositoryException("登录已失效，请重新登录", sessionExpired = true) }
        }
        val vm = MedicationViewModel(fake, store)
        advanceUntilIdle()
        assertNull(vm.state.value.session)
        assertEquals("登录已失效，请重新登录", vm.state.value.error)
        assertFalse(vm.state.value.restoring)
        assertNull(store.load())
    }

    @Test fun networkFailureOnColdStartKeepsSavedSession() = runTest {
        val store = InMemorySessionStore(session)
        val fake = FakeRepository(session).apply {
            load = { throw RepositoryException("无法连接后端，请检查地址、网络和服务是否启动") }
        }
        val vm = MedicationViewModel(fake, store)
        advanceUntilIdle()
        // 后端没开只是暂时不可用，不是令牌失效：会话必须留着，否则用户要重敲地址和账号。
        assertEquals(session, vm.state.value.session)
        assertEquals(session, store.load())
        assertEquals("无法连接后端，请检查地址、网络和服务是否启动", vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.restoring)
    }

    @Test fun successfulLoginPersistsSessionWithoutPassword() = runTest {
        val store = InMemorySessionStore()
        val vm = MedicationViewModel(FakeRepository(session), store)
        vm.login(session.baseUrl, "alice", "password")
        advanceUntilIdle()
        assertEquals(session, store.load())
    }

    @Test fun logoutClearsPersistedSession() = runTest {
        val store = InMemorySessionStore(session)
        val vm = MedicationViewModel(FakeRepository(session), store)
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()
        assertNull(vm.state.value.session)
        assertNull(store.load())
    }

    private class FakeRepository(private val initialSession: Session) : MedicationRepository {
        var loginCount = 0
        var loadedSession: Session? = null
        val confirmations = mutableListOf<Submission>()
        var signIn: suspend (String, String, String) -> Session = { _, _, _ -> initialSession }
        var load: suspend (Session) -> List<RemindTask> = { emptyList() }
        var submit: suspend (Session, Long, String, String) -> RemindTask = { _, id, action, _ ->
            RemindTask(id = id, status = "CONFIRMED", confirmed = true, action = action)
        }
        override suspend fun login(baseUrl: String, account: String, password: String): Session {
            loginCount++
            return signIn(baseUrl, account, password)
        }
        override suspend fun today(session: Session): List<RemindTask> {
            loadedSession = session
            return load(session)
        }
        override suspend fun confirm(session: Session, taskId: Long, action: String, requestId: String): RemindTask {
            confirmations += Submission(session, taskId, action, requestId)
            return submit(session, taskId, action, requestId)
        }
    }

    private data class Submission(val session: Session, val taskId: Long, val action: String, val requestId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
