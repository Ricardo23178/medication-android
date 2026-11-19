package com.zhusixing.medication.data

import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import java.io.IOException

/**
 * 协议层的唯一判断入口。
 *
 * 后端的响应有两层状态，任何一层都不能省：
 *   - HTTP 状态码：401 / 500 / 404 由运输层给出。
 *   - 业务 code：统一壳 `{code, message, data}` 里的 code，400 表示业务规则拒绝。
 *
 * 只在 HTTP 层判成功的实现，会把「计划状态不可暂停」这类业务拒绝当成成功，
 * 界面于是显示操作完成——这是最难排查的一类假成功。所以这里两层都判。
 *
 * 另一个关键概念是 [outcomeSensitive]：写操作（确认服药、暂停计划）如果超时或 5xx，
 * 服务端可能已经把数据改掉了，客户端却不知道。这种情况必须标记为「结果未知」，
 * 让上层保留用户原意图、用同一个请求标识重试，而不是显示失败后让用户重新点一遍。
 * 读操作则没有这个问题，失败就是失败。
 */
internal object ApiCall {

    /** 要求 data 非空。查列表、查详情、写后回查都走这里。 */
    suspend fun <T : Any> requireData(
        outcomeSensitive: Boolean = false,
        block: suspend () -> Response<ApiEnvelope<T>>,
    ): T = envelope(outcomeSensitive, block).data
        ?: throw RepositoryException(
            if (outcomeSensitive) "服务返回的提交结果不完整，请核对后重试" else "服务返回的数据不完整，请刷新",
            outcomeUnknown = outcomeSensitive,
        )

    /** 不要求 data。删除、标记已读这类只关心成败的接口走这里。 */
    suspend fun ignoreData(
        outcomeSensitive: Boolean = false,
        block: suspend () -> Response<ApiEnvelope<Any?>>,
    ) {
        envelope(outcomeSensitive, block)
    }

    private suspend fun <T> envelope(
        outcomeSensitive: Boolean,
        block: suspend () -> Response<ApiEnvelope<T>>,
    ): ApiEnvelope<T> {
        try {
            val response = block()

            if (response.code() == 401) {
                throw RepositoryException("登录已失效，请重新登录", sessionExpired = true)
            }
            if (!response.isSuccessful) {
                throw RepositoryException(
                    "请求失败（HTTP ${response.code()}），请稍后重试",
                    outcomeUnknown = outcomeSensitive && response.code() >= 500,
                )
            }
            val body = response.body() ?: throw RepositoryException(
                if (outcomeSensitive) "服务端未返回提交结果，请核对后再操作" else "服务端返回为空，请稍后重试",
                outcomeUnknown = outcomeSensitive,
            )

            val message = body.message?.takeIf { it.isNotBlank() } ?: "请求未完成，请刷新后重试"
            if (body.code == 401) {
                throw RepositoryException(message, sessionExpired = true)
            }
            if (body.code != 200) {
                // code 为 null 表示响应体结构不对，与 5xx 同等对待：服务端状态不明。
                throw RepositoryException(
                    message,
                    outcomeUnknown = outcomeSensitive && (body.code == null || body.code >= 500),
                )
            }
            return body
        } catch (cancelled: CancellationException) {
            // 协程取消不是业务失败，必须原样抛出，否则会吞掉取消信号。
            throw cancelled
        } catch (known: RepositoryException) {
            throw known
        } catch (io: IOException) {
            throw RepositoryException(
                if (outcomeSensitive) "网络中断，提交结果待核对，请重试原操作或刷新" else "无法连接后端，请检查地址、网络和服务是否启动",
                outcomeUnknown = outcomeSensitive,
            )
        } catch (parse: JsonParseException) {
            throw RepositoryException("服务端返回的数据无法解析，请检查后端版本", outcomeUnknown = outcomeSensitive)
        } catch (other: Exception) {
            throw RepositoryException("请求未完成，请稍后重试", outcomeUnknown = outcomeSensitive)
        }
    }

    /**
     * 校验并规范化服务器根地址。
     *
     * 只接受「协议 + 主机 + 端口」的根地址。带路径、查询串、用户名密码的输入直接拒绝：
     * 拦截器会用这个地址改写每个请求的 host，一旦带路径就会把 api/xxx 拼到用户填的路径后面，
     * 得到 404 而看不出原因。
     */
    fun address(value: String): HttpUrl {
        val url = value.trim().toHttpUrlOrNull()
        if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.encodedPath != "/" || url.query != null || url.fragment != null
        ) {
            throw RepositoryException("请输入后端根地址，例如 http://10.0.2.2:8080/")
        }
        return url
    }

    /** 当前会话对应的请求上下文。 */
    fun context(session: Session): SessionHolder = SessionHolder(address(session.baseUrl), session.token)
}
