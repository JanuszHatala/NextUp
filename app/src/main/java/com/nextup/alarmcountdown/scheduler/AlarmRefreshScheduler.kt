package com.nextup.alarmcountdown.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import com.nextup.alarmcountdown.receiver.AlarmBroadcastReceiver
import com.nextup.alarmcountdown.util.NextUpLog

object AlarmRefreshScheduler {

    const val REFRESH_REQUEST_CODE = 2002

    /**
     * Calculates the exact next epoch millis when widgets and notifications should refresh.
     *
     * When isInteractive is false (screen off/idle overnight):
     * Refreshes only occur when an active alarm rings (triggerTime + 500ms).
     * No waking alarms are scheduled for hidden widgets while the user is asleep.
     *
     * When isInteractive is true (screen on / user using device):
     * Refreshes occur at minute transitions (e.g. XX:YY:00.500) while an alarm or routine is pending,
     * or right after an alarm rings (triggerTime + 500ms).
     *
     * Returns null if no active or predicted alarms exist.
     */
    fun calculateNextRefreshTime(
        now: Long,
        triggerTimeMillis: Long?,
        earliestRoutineMillis: Long? = null,
        isInteractive: Boolean = true
    ): Long? {
        val nextTargetTime = if (triggerTimeMillis != null && triggerTimeMillis > now) {
            triggerTimeMillis
        } else if (earliestRoutineMillis != null && earliestRoutineMillis > now) {
            earliestRoutineMillis
        } else {
            null
        } ?: return null

        if (!isInteractive) {
            // Screen is OFF: do NOT schedule recurring minute wakeups.
            // Only wake up when the actual alarm rings (or when earliest routine transitions if no confirmed alarm).
            return nextTargetTime + 500L
        }

        val nextMinuteMillis = (now / 60_000L + 1) * 60_000L + 500L
        return if (nextTargetTime in (now + 1)..nextMinuteMillis) {
            nextTargetTime + 500L
        } else {
            nextMinuteMillis
        }
    }

    /**
     * Schedules the next exact wakeup alarm so widgets and notifications
     * refresh on exact minute transitions (while screen is on) and alarm ring boundaries.
     */
    fun scheduleNextRefresh(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true

        val intent = Intent(appContext, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_REFRESH_WIDGET
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val targetTime = calculateNextRefreshTime(now, triggerTime, earliestRoutine, isInteractive)

        if (targetTime != null && targetTime > now) {
            NextUpLog.d(
                "RefreshScheduler",
                "scheduleNextRefresh: in ${(targetTime - now) / 1000}s (targetTime=$targetTime, now=$now, isInteractive=$isInteractive)"
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, targetTime, pi)
                }
            } catch (e: SecurityException) {
                NextUpLog.w("RefreshScheduler", "Exact alarm not allowed, fallback to set()", e)
                try {
                    am.set(AlarmManager.RTC_WAKEUP, targetTime, pi)
                } catch (ignored: Exception) {}
            } catch (e: Exception) {
                NextUpLog.e("RefreshScheduler", "Failed to schedule refresh alarm", e)
            }
        } else {
            NextUpLog.d("RefreshScheduler", "scheduleNextRefresh: no active alarms, canceling refresh alarm")
            try {
                am.cancel(pi)
            } catch (ignored: Exception) {}
        }
    }
}
