package com.zhusixing.medication.ui.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 展示层的格式化与枚举翻译。
 *
 * 集中在一个文件里，是因为同一个状态在列表页、详情页、消息正文里都得说同一句话。
 * 散落在各页面里改一处漏两处，用户会看到同一件事有两种说法。
 *
 * 后端返回的时间统一是 ISO 字符串（2026-11-19T08:30:00）。解析失败时不抛异常，
 * 退回原始文本——宁可难看，也不能因为一条脏数据让整页崩掉。
 */

private val DATE_TIME = DateTimeFormatter.ofPattern("MM月dd日 HH:mm")
private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_ONLY = DateTimeFormatter.ofPattern("MM月dd日")
private val FULL_DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日")

fun formatDateTime(raw: String?): String = parseDateTime(raw)?.format(DATE_TIME) ?: raw.orEmpty().ifBlank { "—" }

fun formatTime(raw: String?): String = parseDateTime(raw)?.format(TIME_ONLY) ?: raw.orEmpty().ifBlank { "—" }

fun formatDate(raw: String?): String {
    val text = raw?.takeIf { it.isNotBlank() } ?: return "—"
    return runCatching { LocalDate.parse(text.take(10)).format(DATE_ONLY) }
        .getOrElse { text }
}

fun formatFullDate(raw: String?): String {
    val text = raw?.takeIf { it.isNotBlank() } ?: return "—"
    return runCatching { LocalDate.parse(text.take(10)).format(FULL_DATE) }
        .getOrElse { text }
}

/** "HH:mm:ss" 或 "HH:mm" → "HH:mm"。 */
fun formatClock(raw: String?): String {
    val text = raw?.takeIf { it.isNotBlank() } ?: return "—"
    return runCatching { LocalTime.parse(text).format(TIME_ONLY) }.getOrElse { text.take(5) }
}

/** 后端可能返回带 T 的 ISO，也可能返回带空格的；两种都认。 */
private fun parseDateTime(raw: String?): LocalDateTime? {
    val text = raw?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { LocalDateTime.parse(text.replace(' ', 'T')) }.getOrNull()
}

/** 剂量是 BigDecimal，1.50 显示成 1.5，1.00 显示成 1。 */
fun formatDose(value: java.math.BigDecimal?): String? =
    value?.stripTrailingZeros()?.toPlainString()

fun mealRelationLabel(value: Int?): String? = when (value) {
    0 -> "不限餐前餐后"
    1 -> "餐前"
    2 -> "餐后"
    3 -> "晨起空腹"
    4 -> "睡前"
    else -> null
}

fun actionLabel(action: String?): String = when (action) {
    "TAKEN" -> "已服药"
    "DELAYED" -> "稍后补服"
    "SKIPPED" -> "已跳过"
    else -> action.orEmpty().ifBlank { "未记录" }
}

fun planStatusLabel(status: String?): String = when (status) {
    "ACTIVE" -> "进行中"
    "PAUSED" -> "已暂停"
    "FINISHED" -> "已结束"
    else -> status.orEmpty().ifBlank { "未知" }
}

fun relationStatusLabel(status: String?): String = when (status) {
    "PENDING" -> "待对方接受"
    "ACTIVE" -> "已绑定"
    "UNBOUND" -> "已解除"
    else -> status.orEmpty().ifBlank { "未知" }
}

fun abnormalTypeLabel(type: String?): String = when (type) {
    "MISSED_DOSE" -> "漏服"
    "LONG_DELAY" -> "长时间未确认"
    else -> type.orEmpty().ifBlank { "异常" }
}

fun abnormalStatusLabel(status: String?): String = when (status) {
    "OPEN" -> "待处理"
    "ACKNOWLEDGED" -> "已知晓"
    "CLOSED" -> "已关闭"
    else -> status.orEmpty().ifBlank { "未知" }
}

/** 完成率 0.0~1.0 → "83%"；后端可能给 0~100，做一次归一。 */
fun formatRate(value: Double): String {
    val normalized = if (value > 1.0) value / 100.0 else value
    return "${(normalized * 100).toInt()}%"
}
