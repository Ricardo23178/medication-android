package com.zhusixing.medication

import com.google.gson.JsonParser
import com.zhusixing.medication.data.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpMedicationRepositoryTest {
    private lateinit var server: MockWebServer
    // 与 AppModule 用同一条拦截器链，避免测试跑的和线上跑的不是同一套网络配置。
    private val repository = HttpMedicationRepository(
        NetworkFactory.retrofit(NetworkFactory.okHttpClient(debugLogging = false)),
    )
    private val user = UserProfile(id = 7, account = "test-user")
    private fun session() = Session(server.url("/").toString(), "test-token", user)
    private val task = """{"id":9007199254740993,"medicineName":"虚构药品","dosagePerTime":0.125,"dosageUnit":"片","scheduledAt":"2026-10-29T23:50:00","status":"TRIGGERED","confirmed":false}"""
    private fun reply(data: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"code":200,"data":$data}""")

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test fun loginUsesContractAndDoesNotRoundLongIds() = runTest {
        server.enqueue(reply("""{"token":"abc","user":{"id":9007199254740993,"account":"alice","nickname":null}}"""))
        val result = repository.login(server.url("/").toString(), "alice", "space pass ")
        assertEquals("abc", result.token)
        assertEquals(9007199254740993L, result.user.id)
        assertEquals("alice", result.user.displayName)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("alice", body["account"].asString)
        assertEquals("space pass ", body["password"].asString)
    }

    @Test fun todayKeepsServerOrderCrossDayAndDecimalDose() = runTest {
        server.enqueue(reply("[$task,${task.replace("9007199254740993", "2")}]") )
        val result = repository.today(session())
        assertEquals(listOf(9007199254740993L, 2L), result.map { it.id })
        assertEquals("0.125", result[0].dosagePerTime?.toPlainString())
        assertEquals("2026-10-29T23:50:00", result[0].scheduledAt)
        val request = server.takeRequest()
        assertEquals("/api/reminders/today", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test fun confirmSendsIntentIdAndAcceptsAuthoritativeResult() = runTest {
        server.enqueue(reply(task.replace("TRIGGERED", "CONFIRMED").replace("false", "true").dropLast(1) + ""","action":"SKIPPED"}"""))
        val result = repository.confirm(session(), 9007199254740993L, "TAKEN", "intent-1")
        assertEquals("SKIPPED", result.action)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/reminders/9007199254740993/confirm", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("TAKEN", body["action"].asString)
        assertEquals("intent-1", body["clientRequestId"].asString)
    }

    @Test fun business401AndHttp401BothExpireSession() = runTest {
        for (response in listOf(
            MockResponse().setBody("""{"code":401,"message":"会话失效"}"""),
            MockResponse().setResponseCode(401),
        )) {
            server.enqueue(response)
            assertTrue(failure { repository.today(session()) }.sessionExpired)
        }
    }

    @Test fun missingTokenOrMissingTaskDataCannotBecomeSuccess() = runTest {
        server.enqueue(reply("""{"user":{"id":1,"account":"alice"}}"""))
        failure { repository.login(server.url("/").toString(), "alice", "pw") }
        server.enqueue(reply("null"))
        assertTrue(failure { repository.confirm(session(), 1, "TAKEN", "id") }.outcomeUnknown)
    }

    @Test fun confirmServerFailureIsUncertainButBusinessRejectionIsNot() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(failure { repository.confirm(session(), 1, "TAKEN", "id") }.outcomeUnknown)
        server.enqueue(MockResponse().setBody("""{"code":400,"message":"任务已超时"}"""))
        val failure = failure { repository.confirm(session(), 1, "TAKEN", "id") }
        assertFalse(failure.outcomeUnknown)
        assertEquals("任务已超时", failure.message)
    }

    @Test fun invalidAddressAndUnknownActionAreRejectedWithoutRequest() = runTest {
        for (url in listOf("", "file:///etc", "http://user:pass@localhost/", "http://localhost/api/", "http://localhost/?token=x", "http://localhost/#x")) {
            failure { repository.login(url, "a", "pw") }
        }
        failure { repository.confirm(session(), 1, "DELAYED", "id") }
        assertEquals(0, server.requestCount)
    }

    @Test fun mismatchedTaskResponseIsNotAcceptedAsConfirmation() = runTest {        val validButWrongId = task.replace("TRIGGERED", "CONFIRMED").replace("false", "true").dropLast(1) + """,\"action\":\"TAKEN\"}"""
        server.enqueue(reply(validButWrongId))
        assertTrue(failure { repository.confirm(session(), 1, "TAKEN", "id") }.outcomeUnknown)
    }

    @Test fun oneRetrofitServesDifferentHostsAfterSessionChanges() = runTest {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(reply("[]"))
            other.enqueue(reply("[]"))
            repository.today(session())
            repository.today(session().copy(baseUrl = other.url("/").toString()))
            assertEquals(1, server.requestCount)
            assertEquals(1, other.requestCount)
            assertEquals("/api/reminders/today", other.takeRequest().path)
        } finally {
            other.shutdown()
        }
    }

    @Test fun disconnectedWriteIsUncertainAndDoesNotSilentlyRetry() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertTrue(failure { repository.confirm(session(), 1, "TAKEN", "original-id") }.outcomeUnknown)
        assertEquals(1, server.requestCount)
    }

    @Test fun missingConfirmedFlagNeverEnablesAnAction() {
        assertFalse(RemindTask(id = 1, status = "WAITING").canConfirm)
        assertFalse(RemindTask(id = 1, status = "UNKNOWN", confirmed = false).canConfirm)
        assertFalse(RemindTask(id = 1, status = "CANCELLED", confirmed = false).canConfirm)
        assertTrue(RemindTask(id = 1, status = "TRIGGERED", confirmed = false, action = "DELAYED").canConfirm)
    }

    @Test fun concurrentSessionsNeverShareDestinationOrToken() = runTest {
        val other = MockWebServer()
        other.start()
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val client = NetworkFactory.okHttpClient(debugLogging = false).newBuilder().apply {
            interceptors().add(0, okhttp3.Interceptor { chain ->
                if (calls.getAndIncrement() == 0) {
                    started.countDown()
                    check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                }
                chain.proceed(chain.request())
            })
        }.build()
        val concurrent = HttpMedicationRepository(NetworkFactory.retrofit(client))
        server.enqueue(reply("[$task]"))
        other.enqueue(reply("[$task]"))
        other.enqueue(reply("[$task]"))
        try {
            val first = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                concurrent.today(session())
            }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            concurrent.today(Session(other.url("/").toString(), "other-token", user))
            release.countDown()
            first.await()
            val original = server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull("首个请求被另一个会话改写了地址", original)
            assertEquals("Bearer test-token", original!!.getHeader("Authorization"))
            assertEquals("Bearer other-token", other.takeRequest().getHeader("Authorization"))
        } finally {
            release.countDown()
            other.shutdown()
        }
    }

    private suspend fun failure(block: suspend () -> Unit): RepositoryException {
        try { block() } catch (error: RepositoryException) { return error }
        throw AssertionError("Expected RepositoryException")
    }
}
