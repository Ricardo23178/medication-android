package com.zhusixing.medication.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络组件的组装配方。
 *
 * 单独抽出来是为了让单元测试用同一套拦截器链，而不是在测试里另拼一个客户端——
 * 否则测试跑的和线上跑的不是同一条链路，测过了也说明不了问题。
 */
object NetworkFactory {

    fun okHttpClient(debugLogging: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            // 写请求由用户重试，不能在不知情时自动重发。
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(BaseUrlInterceptor())
            .addInterceptor(AuthInterceptor())
            .apply {
                // BASIC 只记录方法、URL 与状态码，不输出请求头和请求体；密码与令牌不会进日志。
                if (debugLogging) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

    fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        // 占位地址，真实 host 由 BaseUrlInterceptor 按当前会话改写。
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
