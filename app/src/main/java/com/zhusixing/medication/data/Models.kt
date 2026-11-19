package com.zhusixing.medication.data

import java.math.BigDecimal

data class ApiEnvelope<T>(val code: Int? = null, val message: String? = null, val data: T? = null)
data class LoginRequest(val account: String, val password: String)
data class UserProfile(
    val id: Long = 0,
    val account: String? = null,
    val nickname: String? = null,
    /** OWNER / FAMILY / ADMIN。决定「家属」入口是否展示管理员触发按钮。 */
    val role: String? = null,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: account.orEmpty()
    val isAdmin: Boolean get() = role == "ADMIN"
}
data class LoginData(val token: String? = null, val user: UserProfile? = null)
data class Session(val baseUrl: String, val token: String, val user: UserProfile)

/**
 * 确认请求。
 *
 * clientRequestId 是幂等的关键：同一次用户意图重试时必须复用同一个值，
 * 后端凭它认出「这是刚才那笔」并返回首次结果，而不是写第二条记录。
 * DELAYED 才需要 delayMinutes（1~120），SKIPPED 才需要 skipReason。
 */
data class ConfirmRequest(
    val action: String,
    val clientRequestId: String,
    val delayMinutes: Int? = null,
    val skipReason: String? = null,
)

data class RemindTask(
    val id: Long = 0,
    val planId: Long? = null,
    val planVersion: Int? = null,
    val medicineName: String? = null,
    val dosagePerTime: BigDecimal? = null,
    val dosageUnit: String? = null,
    val mealRelation: Int? = null,
    val scheduledAt: String? = null,
    val expireAt: String? = null,
    val status: String? = null,
    val confirmed: Boolean? = null,
    val action: String? = null,
) {
    val canConfirm: Boolean
        get() = id > 0 && confirmed == false && status in setOf("WAITING", "TRIGGERED")
    val statusLabel: String
        get() = when (status) {
            "CANCELLED" -> "已取消"
            "EXPIRED" -> "超时未确认"
            "CONFIRMED" -> when (action) {
                "TAKEN" -> "已记录：已服"
                "SKIPPED" -> "已记录：跳过"
                else -> "已结束"
            }
            "WAITING", "TRIGGERED" -> if (action == "DELAYED") "稍后待确认" else "待确认"
            else -> "未知状态，请刷新"
        }
}
