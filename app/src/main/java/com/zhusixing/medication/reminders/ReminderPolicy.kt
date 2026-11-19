package com.zhusixing.medication.reminders

import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.data.Session
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// 只缓存调度所需字段，通知和本地索引不包含药名、剂量。
data class ReminderEntry(val id: Long, val scheduledAt: String, val expireAt: String)
data class ReminderSnapshot(val sessionKey: String, val entries: List<ReminderEntry>)

object ReminderPolicy {
    fun sessionKey(session: Session): String = MessageDigest.getInstance("SHA-256")
        .digest("${session.baseUrl}\n${session.user.id}\n${session.token}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun instant(text: String, zone: ZoneId): Instant? =
        runCatching { LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull()

    fun entries(tasks: List<RemindTask>, now: Instant, zone: ZoneId): List<ReminderEntry> =
        tasks.mapNotNull { task ->
            if (!task.canConfirm) return@mapNotNull null
            val start = task.scheduledAt?.let { instant(it, zone) } ?: return@mapNotNull null
            val end = task.expireAt?.let { instant(it, zone) } ?: return@mapNotNull null
            if (start <= now || end <= start) return@mapNotNull null
            ReminderEntry(task.id, task.scheduledAt, task.expireAt)
        }.distinctBy { it.id }

    fun isDue(entry: ReminderEntry, now: Instant, zone: ZoneId): Boolean {
        val start = instant(entry.scheduledAt, zone) ?: return false
        val end = instant(entry.expireAt, zone) ?: return false
        return now >= start && now < end
    }
}
