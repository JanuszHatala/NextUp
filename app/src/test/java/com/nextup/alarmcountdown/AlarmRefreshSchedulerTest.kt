package com.nextup.alarmcountdown

import com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRefreshSchedulerTest {

    @Test
    fun testNoAlarmsReturnsNull() {
        val now = 1000000L
        val result = AlarmRefreshScheduler.calculateNextRefreshTime(
            now = now,
            triggerTimeMillis = null,
            earliestRoutineMillis = null
        )
        assertNull(result)
    }

    @Test
    fun testAlarmInThePastReturnsNullWhenNoRoutine() {
        val now = 1000000L
        val result = AlarmRefreshScheduler.calculateNextRefreshTime(
            now = now,
            triggerTimeMillis = now - 5000L,
            earliestRoutineMillis = null
        )
        assertNull(result)
    }

    @Test
    fun testAlarmFarInFutureSchedulesNextMinute() {
        // e.g. now is 13:24:42.000 -> 80442000 ms into day
        val now = 80442000L
        val alarmTime = now + 7 * 3600_000L // 7 hours later

        val nextMinuteExpected = (now / 60_000L + 1) * 60_000L + 500L
        val result = AlarmRefreshScheduler.calculateNextRefreshTime(
            now = now,
            triggerTimeMillis = alarmTime,
            earliestRoutineMillis = null
        )
        assertEquals(nextMinuteExpected, result)
        assertEquals(80460500L, result) // 13:25:00.500
    }

    @Test
    fun testAlarmRingsWithinCurrentMinuteSchedulesRightAfterRinging() {
        val now = 60_000L * 10 + 40_000L // 10m 40s
        val alarmTime = now + 10_000L     // 10m 50s (rings before minute 11)

        val result = AlarmRefreshScheduler.calculateNextRefreshTime(
            now = now,
            triggerTimeMillis = alarmTime,
            earliestRoutineMillis = null
        )
        assertEquals(alarmTime + 500L, result)
    }

    @Test
    fun testRoutineAlarmUsedWhenNoConfirmedAlarm() {
        val now = 60_000L * 10
        val routineTime = now + 3600_000L // 1 hour later

        val nextMinuteExpected = (now / 60_000L + 1) * 60_000L + 500L
        val result = AlarmRefreshScheduler.calculateNextRefreshTime(
            now = now,
            triggerTimeMillis = null,
            earliestRoutineMillis = routineTime,
            isInteractive = true
        )
        assertEquals(nextMinuteExpected, result)
    }

    @Test
    fun testRingRefreshCalculatesExactAlarmBoundary() {
        val now = 80442000L
        val alarmTime = now + 7 * 3600_000L // 7 hours later

        val ringResult = AlarmRefreshScheduler.calculateRingRefreshTime(
            now = now,
            triggerTimeMillis = alarmTime,
            earliestRoutineMillis = null
        )
        // Exact ring wakeup scheduled for trigger time + 500ms
        assertEquals(alarmTime + 500L, ringResult)

        // Minute refresh scheduled for next minute (via non-wakeup RTC)
        val minuteExpected = (now / 60_000L + 1) * 60_000L + 500L
        val minuteResult = AlarmRefreshScheduler.calculateNextMinuteRefreshTime(
            now = now,
            triggerTimeMillis = alarmTime,
            earliestRoutineMillis = null
        )
        assertEquals(minuteExpected, minuteResult)
    }
}
