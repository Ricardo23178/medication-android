package com.zhusixing.medication.data

import java.math.BigDecimal

/*
 * 业务模型：严格对齐后端的 DTO / VO 字段名。
 *
 * 两条约定，和 Models.kt 保持一致：
 *   1. 日期时间一律用 String 接收。后端 LocalDateTime 序列化成 "2026-11-19T08:30:00"，
 *      由 UI 层按展示需要解析；不在这里放 Gson TypeAdapter，避免解析器隐式抛异常。
 *   2. 字段全部给默认值。后端新增字段不会让反序列化失败，缺字段也不会抛 NPE。
 */

// ─────────────────────────── 身份 ───────────────────────────

/** GET /api/user/me 的返回。role 为 OWNER / FAMILY / ADMIN。 */
data class CurrentUser(
    val id: Long = 0,
    val role: String? = null,
)

/** POST /api/auth/register 的请体。四个必填项与后端 @Valid 校验一一对应。 */
data class RegisterRequest(
    val account: String,
    val password: String,
    val confirmPassword: String,
    val nickname: String,
    val phone: String? = null,
)

data class HealthInfo(
    val status: String? = null,
    val serverTime: String? = null,
    val dbVersion: String? = null,
    val sysUserCount: Int? = null,
)

// ─────────────────────────── 药品 ───────────────────────────

/** 后端直接返回 entity.Medicine，故字段与实体一致。 */
data class Medicine(
    val id: Long = 0,
    val userId: Long = 0,
    val name: String = "",
    val specification: String? = null,
    val dosage: String? = null,
    val unit: String? = null,
    val remark: String? = null,
    val imageUrl: String? = null,
    val enabled: Int? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
) {
    val isEnabled: Boolean get() = enabled == 1
}

data class MedicineRequest(
    val name: String,
    val specification: String? = null,
    val dosage: String? = null,
    val unit: String? = null,
    val remark: String? = null,
    val imageUrl: String? = null,
    val enabled: Int = 1,
)

// ─────────────────────────── 用药计划 ───────────────────────────

data class PlanTimeItem(
    val id: Long = 0,
    val planId: Long = 0,
    /** "HH:mm:ss" */
    val remindTime: String? = null,
    val sequenceNo: Int? = null,
    val enabled: Int? = null,
)

data class Plan(
    val id: Long = 0,
    val planVersion: Int? = null,
    val medicineId: Long = 0,
    val medicineName: String? = null,
    val dosagePerTime: BigDecimal? = null,
    val dosageUnit: String? = null,
    /** "yyyy-MM-dd" */
    val startDate: String? = null,
    val endDate: String? = null,
    val mealRelation: Int? = null,
    /** ACTIVE / PAUSED / FINISHED */
    val status: String? = null,
    val remark: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val times: List<PlanTimeItem>? = null,
) {
    val isActive: Boolean get() = status == "ACTIVE"
    val isPaused: Boolean get() = status == "PAUSED"
}

data class PlanRequest(
    val medicineId: Long,
    val dosagePerTime: BigDecimal? = null,
    val dosageUnit: String? = null,
    /** "yyyy-MM-dd" */
    val startDate: String,
    val endDate: String? = null,
    val mealRelation: Int? = null,
    val remark: String? = null,
    /** ["08:00", "12:30"]，后端接受 HH:mm 或 HH:mm:ss */
    val remindTimes: List<String> = emptyList(),
)

// ─────────────────────────── 服药记录 ───────────────────────────

data class MedicationRecord(
    val id: Long = 0,
    val taskId: Long = 0,
    val medicineName: String? = null,
    /** TAKEN / DELAYED / SKIPPED */
    val action: String? = null,
    val actionTime: String? = null,
    val delayMinutes: Int? = null,
    val skipReason: String? = null,
)

// ─────────────────────────── 站内消息 ───────────────────────────

data class NotificationItem(
    val id: Long = 0,
    val type: String? = null,
    val title: String? = null,
    val content: String? = null,
    val bizType: String? = null,
    val bizId: Long? = null,
    val readFlag: Int? = null,
    val createTime: String? = null,
    val familyEvent: Boolean = false,
) {
    val isRead: Boolean get() = readFlag == 1
}

/** 游标分页：拿 nextBeforeId 当下一页的 beforeId，hasMore 为 false 就到底了。 */
data class NotificationPage(
    val items: List<NotificationItem> = emptyList(),
    val unreadCount: Long = 0,
    val nextBeforeId: Long? = null,
    val hasMore: Boolean = false,
)

// ─────────────────────────── 家属协同 ───────────────────────────

data class FamilyRelation(
    val id: Long = 0,
    val ownerUserId: Long = 0,
    val ownerNickname: String? = null,
    val familyUserId: Long? = null,
    val familyNickname: String? = null,
    val relationType: String? = null,
    /** PENDING / ACTIVE / UNBOUND */
    val relationStatus: String? = null,
    val scope: String? = null,
    val inviteCode: String? = null,
    val inviteExpireAt: String? = null,
    val bindTime: String? = null,
    val unbindTime: String? = null,
    val createTime: String? = null,
) {
    val isPending: Boolean get() = relationStatus == "PENDING"
    val isActive: Boolean get() = relationStatus == "ACTIVE"
    /** 邀请码还有效：状态待接受且未过期。过期判断交给后端，这里只挡明显无效的。 */
    val hasInviteCode: Boolean get() = isPending && !inviteCode.isNullOrBlank()
}

data class InviteRequest(
    val relationType: String? = null,
    val scope: String? = null,
)

data class AcceptInviteRequest(
    val inviteCode: String,
)

data class AbnormalEvent(
    val id: Long = 0,
    val userId: Long = 0,
    val userNickname: String? = null,
    val taskId: Long? = null,
    /** MISSED_DOSE / LONG_DELAY 等 */
    val type: String? = null,
    /** OPEN / ACKNOWLEDGED / CLOSED */
    val status: String? = null,
    val occurredAt: String? = null,
    val medicineName: String? = null,
    val scheduledAt: String? = null,
    val acknowledgedAt: String? = null,
    val closedAt: String? = null,
    val createTime: String? = null,
) {
    val isOpen: Boolean get() = status == "OPEN"
    val isAcknowledged: Boolean get() = status == "ACKNOWLEDGED"
}

// ─────────────────────────── 统计 ───────────────────────────

data class DayStat(
    val date: String? = null,
    val shouldCount: Int = 0,
    val takenCount: Int = 0,
    val delayedCount: Int = 0,
    val skippedCount: Int = 0,
    val unconfirmedCount: Int = 0,
    val completionRate: Double = 0.0,
)

data class StatSummary(
    val totalShould: Int = 0,
    val totalTaken: Int = 0,
    val totalDelayed: Int = 0,
    val totalSkipped: Int = 0,
    val totalUnconfirmed: Int = 0,
    val completionRate: Double = 0.0,
)

data class CalendarStat(
    val startDate: String? = null,
    val endDate: String? = null,
    val days: List<DayStat> = emptyList(),
    val summary: StatSummary = StatSummary(),
)
