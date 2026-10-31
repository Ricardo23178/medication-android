package com.zhusixing.medication.data

import java.math.BigDecimal

data class ApiEnvelope<T>(val code: Int? = null, val message: String? = null, val data: T? = null)
data class LoginRequest(val account: String, val password: String)
data class UserProfile(val id: Long = 0, val account: String? = null, val nickname: String? = null) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: account.orEmpty()
}
data class LoginData(val token: String? = null, val user: UserProfile? = null)
data class Session(val baseUrl: String, val token: String, val user: UserProfile)
data class ConfirmRequest(val action: String, val clientRequestId: String)

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
