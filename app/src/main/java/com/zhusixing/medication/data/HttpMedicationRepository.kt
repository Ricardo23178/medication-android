package com.zhusixing.medication.data

import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Tag
import java.io.IOException

private interface MedicationApi {
    @POST("api/auth/login")
    suspend fun login(@Tag context: SessionHolder, @Body request: LoginRequest): Response<ApiEnvelope<LoginData>>

    @GET("api/reminders/today")
    suspend fun today(@Tag context: SessionHolder): Response<ApiEnvelope<List<RemindTask?>>>

    @POST("api/reminders/{id}/confirm")
    suspend fun confirm(
        @Tag context: SessionHolder,
        @Path("id") taskId: Long,
        @Body request: ConfirmRequest,
    ): Response<ApiEnvelope<RemindTask>>
}

/**
 * REST 实现。
 *
 * 鉴权头与目标地址交给 OkHttp 拦截器统一处理（见 ApiInterceptors），
 * 这里只负责契约校验：HTTP 状态与响应体 code 都要判，数据不全不许当成功。
 * OkHttp / Retrofit 由依赖注入提供，是进程内单例。
 */
class HttpMedicationRepository(
    retrofit: Retrofit,
) : MedicationRepository {

    private val api = retrofit.create(MedicationApi::class.java)

    override suspend fun login(baseUrl: String, account: String, password: String): Session {
        val address = normalizeAddress(baseUrl)
        if (account.isBlank() || password.isBlank()) throw RepositoryException("请输入账号和密码")
        // 登录请求本身不带令牌；但地址必须先绑定，否则拦截器不知道发往哪台服务器。
        val data = request { api.login(SessionHolder(address, null), LoginRequest(account, password)) }
        val user = data.user
        val token = data.token
        if (token.isNullOrBlank() || user == null || user.id <= 0 || user.account.isNullOrBlank()) {
            throw RepositoryException("登录响应不完整，请检查后端服务")
        }
        return Session(address.toString(), token, user)
    }

    override suspend fun today(session: Session): List<RemindTask> {
        val tasks = request { api.today(SessionHolder(normalizeAddress(session.baseUrl), session.token)) }.map { it ?: invalidData() }
        if (tasks.any { !validTask(it) } || tasks.map { it.id }.distinct().size != tasks.size) invalidData()
        return tasks
    }

    override suspend fun confirm(session: Session, taskId: Long, action: String, requestId: String): RemindTask {
        if (taskId <= 0 || action !in setOf("TAKEN", "SKIPPED") || requestId.isBlank() || requestId.length > 64) {
            throw RepositoryException("确认参数无效，请刷新后重试")
        }
        val task = request(confirming = true) {
            api.confirm(SessionHolder(normalizeAddress(session.baseUrl), session.token), taskId, ConfirmRequest(action, requestId))
        }
        if (!validTask(task) || task.id != taskId || task.status != "CONFIRMED" ||
            task.confirmed != true || task.action !in setOf("TAKEN", "SKIPPED")) invalidData(confirming = true)
        return task
    }

    private fun normalizeAddress(value: String): HttpUrl {
        val url = value.trim().toHttpUrlOrNull()
        if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.encodedPath != "/" || url.query != null || url.fragment != null) {
            throw RepositoryException("请输入后端根地址，例如 http://10.0.2.2:8080/")
        }
        return url
    }

    private fun validTask(task: RemindTask) = task.id > 0 && !task.status.isNullOrBlank() && task.confirmed != null

    private fun invalidData(confirming: Boolean = false): Nothing = throw RepositoryException(
        if (confirming) "服务返回的确认结果不完整，请重试原操作或刷新核对" else "任务数据不完整，请刷新或检查后端",
        outcomeUnknown = confirming,
    )

    private suspend fun <T : Any> request(
        confirming: Boolean = false,
        block: suspend () -> Response<ApiEnvelope<T>>,
    ): T {
        try {
            val response = block()
            if (response.code() == 401) throw RepositoryException("登录已失效，请重新登录", sessionExpired = true)
            if (!response.isSuccessful) throw RepositoryException(
                "请求失败（HTTP ${response.code()}），请稍后重试",
                outcomeUnknown = confirming && response.code() >= 500,
            )
            val body = response.body() ?: invalidData(confirming)
            val message = body.message?.takeIf { it.isNotBlank() } ?: "请求未完成，请刷新后重试"
            if (body.code == 401) throw RepositoryException(message, sessionExpired = true)
            if (body.code != 200) throw RepositoryException(
                message,
                outcomeUnknown = confirming && (body.code == null || body.code >= 500),
            )
            return body.data ?: invalidData(confirming)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RepositoryException) {
            throw error
        } catch (error: IOException) {
            throw RepositoryException(
                if (confirming) "网络中断，提交结果待核对，请重试原操作或刷新" else "无法连接后端，请检查地址、网络和服务是否启动",
                outcomeUnknown = confirming,
            )
        } catch (error: JsonParseException) {
            invalidData(confirming)
        }
    }
}
