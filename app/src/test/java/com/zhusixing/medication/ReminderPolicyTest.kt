package com.zhusixing.medication

import com.zhusixing.medication.data.RemindTask
import com.zhusixing.medication.data.Session
import com.zhusixing.medication.data.UserProfile
import com.zhusixing.medication.reminders.ReminderPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-11-19T00:00:00Z")
    private val task = RemindTask(id = 9007199254740993L, scheduledAt = "2026-11-19T08:10:00",
        expireAt = "2026-11-19T08:40:00", status = "WAITING", confirmed = false)

    @Test fun schedulesOnlyFutureActionableTasksWithValidDeadline() {
        val tasks = listOf(task, task.copy(id=2, status="CONFIRMED"), task.copy(id=3, confirmed=true),
            task.copy(id=4, status="CANCELLED"), task.copy(id=5, status="EXPIRED"),
            task.copy(id=6, scheduledAt="2026-11-19T07:59:00"), task.copy(id=7, expireAt=null),
            task.copy(id=8, scheduledAt="bad"), task.copy(id=9, expireAt="2026-11-19T08:09:00"))
        assertEquals(listOf(task.id), ReminderPolicy.entries(tasks, now, zone).map { it.id })
    }
    @Test fun dueWindowRejectsEarlyAndExpiredDelivery() {
        val entry = ReminderPolicy.entries(listOf(task), now, zone).single()
        assertFalse(ReminderPolicy.isDue(entry, now, zone))
        assertTrue(ReminderPolicy.isDue(entry, now.plusSeconds(601), zone))
        assertFalse(ReminderPolicy.isDue(entry, now.plusSeconds(2400), zone))
    }
    @Test fun sessionKeysSeparateAccountsServersAndRenewedTokens() {
        val session=Session("http://localhost/", "secret", UserProfile(1,"a"))
        val key=ReminderPolicy.sessionKey(session)
        assertEquals(key, ReminderPolicy.sessionKey(session.copy()))
        assertNotEquals(key, ReminderPolicy.sessionKey(session.copy(token="new")))
        assertNotEquals(key, ReminderPolicy.sessionKey(session.copy(user=UserProfile(2,"b"))))
        assertNotEquals(key, ReminderPolicy.sessionKey(session.copy(baseUrl="http://another/")))
        assertFalse(key.contains("secret"))
    }
}
