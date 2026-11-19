package com.zhusixing.medication.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class BaseUrlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = request.tag(SessionHolder::class.java)?.endpoint
            ?: throw IOException("缺少请求会话")
        val url = request.url.newBuilder().scheme(target.scheme).host(target.host).port(target.port).build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}

internal class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = request.tag(SessionHolder::class.java)?.token
        val builder = request.newBuilder().removeHeader("Authorization")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return chain.proceed(builder.build())
    }
}
