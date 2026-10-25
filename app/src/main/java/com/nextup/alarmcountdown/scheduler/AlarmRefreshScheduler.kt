package com.nextup.alarmcountdown.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import com.nextup.alarmcountdown.receiver.AlarmBroadcastReceiver
import com.nextup.alarmcountdown.util.NextUpLog

object AlarmRefreshScheduler {

    const val REFRESH_REQUEST_CODE = 2002
    const val RING_REQUEST_CODE = 2003

    /**
     * Calculates the next minute boundary for widget countdown refreshes.
     * When targetTime is within the current minute, schedules right after targetTime (+ 500ms).
     * Returns null if no active or predicted alarms exist.
     */
    fun calculateNextMinuteRefreshTime(
        now: Long,
        triggerTimeMillis: Long?,
        earliestRoutineMillis: Long? = null
    ): Long? {
        val nextTargetTime = if (triggerTimeMillis != null && triggerTimeMillis > now) {
            triggerTimeMillis
        } else if (earliestRoutineMillis != null && earliestRoutineMillis > now) {
            earliestRoutineMillis
        } else {
            null
        } ?: return null

        val nextMinuteMillis = (now / 60_000L + 1) * 60_000L + 500L
        return if (nextTargetTime in (now + 1)..nextMinuteMillis) {
            nextTargetTime + 500L
        } else {
            nextMinuteMillis
        }
    }

    /**
     * Calculates the exact ring-time wakeup boundary (triggerTime + 500ms).
     * Returns null if no active or predicted alarms exist.
     */
    fun calculateRingRefreshTime(
        now: Long,
        triggerTimeMillis: Long?,
        earliestRoutineMillis: Long? = null
    ): Long? {
        val nextTargetTime = if (triggerTimeMillis != null && triggerTimeMillis > now) {
            triggerTimeMillis
        } else if (earliestRoutineMillis != null && earliestRoutineMillis > now) {
            earliestRoutineMillis
        } else {
            null
        } ?: return null

        return nextTargetTime + 500L
    }

    /**
     * Backward-compatible helper method.
     */
    fun calculateNextRefreshTime(
        now: Long,
        triggerTimeMillis: Long?,
        earliestRoutineMillis: Long? = null,
        isInteractive: Boolean = true
    ): Long? {
        return calculateNextMinuteRefreshTime(now, triggerTimeMillis, earliestRoutineMillis)
    }

    /**
     * Schedules alarms for widget and notification refreshes:
     * 1. REFRESH_REQUEST_CODE via non-wakeup AlarmManager.RTC:
     *    Refreshes countdown every minute while the device is awake. When screen is off and device
     *    is sleeping/in Doze, it never wakes the CPU (zero battery drain). When the device wakes up
     *    (user turns screen on, receives message, etc.), Android OS immediately delivers the deferred
     *    alarm, instantly updating widgets and notifications even if the app process was killed.
     * 2. RING_REQUEST_CODE via wakeup AlarmManager.RTC_WAKEUP:
     *    Wakes up the CPU exactly when an active alarm rings (triggerTime + 500ms), ensuring
     *    immediate transition to "Now" / routine.
     */
    fun scheduleNextRefresh(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = System.currentTimeMillis()
        val repo = AlarmRepository(appContext)
        val nextAlarm = repo.getNextAlarm()
        val triggerTime = nextAlarm?.triggerTimeMillis

        val earliestRoutine = if (triggerTime == null || triggerTime <= now) {
            val dbManager = AlarmDatabaseManager(appContext)
            val patterns = dbManager.getAllPatternsDirect().filter { it.isActive }
            GroupedAlarmPattern.fromPatterns(patterns)
                .mapNotNull { it.getNextOccurrenceMillis(now, null) }
                .minOrNull()
        } else {
            null
        }

        // 1. Minute countdown refresh (non-wakeup RTC)
        val refreshIntent = Intent(appContext, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_REFRESH_WIDGET
        }
        val refreshPi = PendingIntent.getBroadcast(
            appContext,
            REFRESH_REQUEST_CODE,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minuteTargetTime = calculateNextMinuteRefreshTime(now, triggerTime, earliestRoutine)
        if (minuteTargetTime != null && minuteTargetTime > now) {
            NextUpLog.d(
                "RefreshScheduler",
                "scheduleMinuteRefresh (RTC non-wakeup): in ${(minuteTargetTime - now) / 1000}s (targetTime=$minuteTargetTime, now=$now)"
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExact(AlarmManager.RTC, minuteTargetTime, refreshPi)
                } else {
                    am.set(AlarmManager.RTC, minuteTargetTime, refreshPi)
                }
            } catch (e: SecurityException) {
                NextUpLog.w("RefreshScheduler", "Exact RTC alarm not allowed, fallback to set()", e)
                try {
                    am.set(AlarmManager.RTC, minuteTargetTime, refreshPi)
                } catch (ignored: Exception) {}
            } catch (e: Exception) {
                NextUpLog.e("RefreshScheduler", "Failed to schedule minute refresh alarm", e)
            }
        } else {
            try {
                am.cancel(refreshPi)
            } catch (ignored: Exception) {}
        }

        // 2. Exact ring-time wakeup alarm (RTC_WAKEUP)
        val ringIntent = Intent(appContext, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_REFRESH_WIDGET
        }
        val ringPi = PendingIntent.getBroadcast(
            appContext,
            RING_REQUEST_CODE,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringTargetTime = calculateRingRefreshTime(now, triggerTime, earliestRoutine)
        if (ringTargetTime != null && ringTargetTime > now) {
            NextUpLog.d(
                "RefreshScheduler",
                "scheduleRingRefresh (RTC_WAKEUP): in ${(ringTargetTime - now) / 1000}s (ringTargetTime=$ringTargetTime, now=$now)"
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ringTargetTime, ringPi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, ringTargetTime, ringPi)
                }
            } catch (e: SecurityException) {
                NextUpLog.w("RefreshScheduler", "Exact ring wakeup not allowed, fallback to set()", e)
                try {
                    am.set(AlarmManager.RTC_WAKEUP, ringTargetTime, ringPi)
                } catch (ignored: Exception) {}
            } catch (e: Exception) {
                NextUpLog.e("RefreshScheduler", "Failed to schedule ring refresh alarm", e)
            }
        } else {
            try {
                am.cancel(ringPi)
            } catch (ignored: Exception) {}
        }
    }
}
