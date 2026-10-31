package com.zhusixing.medication

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** 隔离接口夹具，不访问毕设业务库；验证真实Activity到HTTP的闭环。 */
@RunWith(AndroidJUnit4::class)
class PhaseOneFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }
    private fun reply(data: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"code":200,"data":$data}""")
    private fun task(id: Long, action: String? = null): String = """{"id":$id,"medicineName":"演示药品$id","dosagePerTime":0.5,"dosageUnit":"片","scheduledAt":"2026-10-30T08:00:00","expireAt":"2026-10-30T10:00:00","status":"${if(action == null) "WAITING" else "CONFIRMED"}","confirmed":${action != null},"action":${action?.let { "\"$it\"" } ?: "null"}}"""

    @Test fun wrongPasswordThenLoginConfirmBothActionsAndLogout() {
        server.enqueue(MockResponse().setBody("""{"code":401,"message":"账号或密码错误"}"""))
        server.enqueue(reply("""{"token":"fixture-token","user":{"id":1,"account":"demo-user","nickname":"演示用户"}}"""))
        server.enqueue(reply("[${task(11)},${task(12)}]"))
        // 故意与所选动作相反，验证以服务端已有结果为准。
        server.enqueue(reply(task(11, "SKIPPED")))
        server.enqueue(reply(task(12, "TAKEN")))
        compose.onNodeWithTag("serverAddress").performScrollTo().performTextReplacement(server.url("/").toString())
        compose.onNodeWithTag("account").performScrollTo().performTextReplacement("demo-user")
        compose.onNodeWithTag("password").performScrollTo().performTextReplacement("wrong")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("login").performScrollTo().assertIsDisplayed().performClick()
        waitForText("账号或密码错误")
        compose.onNodeWithTag("password").performScrollTo().performTextReplacement("demo-password")
        compose.onNodeWithTag("password").performImeAction()
        waitForText("演示药品11")
        compose.onNodeWithTag("todayList").performScrollToNode(hasTestTag("taken-11"))
        capture("phase1-tasks")
        compose.onNodeWithTag("taken-11").performScrollTo().performClick()
        waitForText("已记录：跳过")
        compose.onNodeWithTag("todayList").performScrollToNode(hasTestTag("skipped-12"))
        compose.onNodeWithTag("skipped-12").performScrollTo().performClick()
        waitForText("已记录：已服")
        val requests = (1..5).map { server.takeRequest(3, TimeUnit.SECONDS) ?: error("缺少HTTP请求$it") }
        assertEquals(listOf("/api/auth/login", "/api/auth/login", "/api/reminders/today", "/api/reminders/11/confirm", "/api/reminders/12/confirm"), requests.map { it.path })
        assertEquals("Bearer fixture-token", requests[2].getHeader("Authorization"))
        val first = JsonParser.parseString(requests[3].body.readUtf8()).asJsonObject
        val second = JsonParser.parseString(requests[4].body.readUtf8()).asJsonObject
        assertEquals("TAKEN", first["action"].asString)
        assertEquals("SKIPPED", second["action"].asString)
        assertNotEquals(first["clientRequestId"].asString, second["clientRequestId"].asString)
        compose.onNodeWithTag("todayList").performScrollToIndex(0)
        compose.onNodeWithTag("logout").performClick()
        compose.onNodeWithTag("login").assertExists()
        compose.onAllNodesWithText("演示药品11").assertCountEquals(0)
        capture("phase1-login")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            val diagnostics = "等待$text，已接收HTTP请求数=${server.requestCount}\n" +
                compose.onRoot(useUnmergedTree = true).printToString()
            throw AssertionError(diagnostics, error)
        }
    }
}
