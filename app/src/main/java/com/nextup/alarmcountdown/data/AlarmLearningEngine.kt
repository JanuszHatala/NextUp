package com.nextup.alarmcountdown.data

import android.content.Context
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.AlarmPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class AlarmLearningEngine(private val context: Context) {

    val databaseManager = AlarmDatabaseManager(context)

    /**
     * Called whenever a confirmed next alarm is observed from AlarmManager.
     * Operates strictly on Dispatchers.IO in < 5ms.
     */
    suspend fun onConfirmedAlarmObserved(triggerMillis: Long): Unit = withContext(Dispatchers.IO) {
        if (triggerMillis <= 0L) return@withContext

        // 1. Record the confirmed alarm
        databaseManager.recordConfirmedAlarm(triggerMillis)

        // 2. Perform lazy check on past patterns that should have triggered earlier but were skipped
        lazyCheckSkippedAlarms(triggerMillis)
    }

    /**
     * Lazily evaluates if any registered active pattern was scheduled before now
     * and was never observed/confirmed.
     * Does NOT use waking timers; executes only on natural system events.
     */
    suspend fun lazyCheckSkippedAlarms(referenceMillis: Long = System.currentTimeMillis()): Unit = withContext(Dispatchers.IO) {
        val activePatterns = databaseManager.getAllPatterns().filter { it.isActive }
        val cal = Calendar.getInstance()

        for (pattern in activePatterns) {
            // Check if this pattern's occurrence in the past 24-48 hours was missed
            // An alarm pattern was last seen > 8 days ago and has not been confirmed this week
            val daysSinceLastSeen = (referenceMillis - pattern.lastSeenMillis) / (1000L * 60 * 60 * 24)
            if (daysSinceLastSeen >= 8) {
                // One full weekly cycle elapsed without this alarm being seen -> auto-deactivate
                databaseManager.recordMissedAlarm(pattern)
            }
        }
    }

    /**
     * Retrieves upcoming active predicted alarms, excluding the currently confirmed nearest alarm
     * so that the widget and app do not duplicate the nearest alarm.
     */
    suspend fun getUpcomingPredictedAlarms(
        confirmedNextMillis: Long?,
        limit: Int = 5
    ): List<AlarmPattern> = withContext(Dispatchers.IO) {
        val sorted = databaseManager.getActivePatternsSorted()
        if (confirmedNextMillis == null || confirmedNextMillis <= 0L) {
            return@withContext sorted.take(limit)
        }

        val confirmedCal = Calendar.getInstance().apply { timeInMillis = confirmedNextMillis }
        val confirmedDay = confirmedCal.get(Calendar.DAY_OF_WEEK)
        val confirmedHour = confirmedCal.get(Calendar.HOUR_OF_DAY)
        val confirmedMinute = confirmedCal.get(Calendar.MINUTE)
        val confirmedPatternId = AlarmPattern.buildPatternId(confirmedDay, confirmedHour, confirmedMinute)

        sorted.filter { it.patternId != confirmedPatternId }.take(limit)
    }
}
