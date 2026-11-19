package com.zhusixing.medication.data

import okhttp3.HttpUrl

/** 不可变的单次请求上下文，前台与后台请求不能改写彼此的服务器或令牌。 */
data class SessionHolder(val endpoint: HttpUrl, val token: String?)
