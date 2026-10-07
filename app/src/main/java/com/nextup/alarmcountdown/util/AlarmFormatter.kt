package com.nextup.alarmcountdown.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmFormatter {

    const val NO_ALARM_TEXT = "No alarm set"
    const val LESS_THAN_ONE_MINUTE_TEXT = "< 1m"

    /**
     * Formats the remaining time until the alarm trigger.
     * Rules:
     * - Count in days, hours, minutes.
     * - If days is 0, do not show days.
     * - If hours is 0, do not show hours.
     * - If under 1 minute, show "< 1m".
     * - If no alarm is scheduled, show "No alarm set".
     */
    fun formatRemaining(triggerTimeMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        if (triggerTimeMillis == null || triggerTimeMillis <= 0L) {
            return NO_ALARM_TEXT
        }

        val remainingMillis = triggerTimeMillis - nowMillis

        // If it's in the past
        if (remainingMillis <= 0L) {
            return if (remainingMillis >= -60_000L) {
                LESS_THAN_ONE_MINUTE_TEXT
            } else {
                NO_ALARM_TEXT
            }
        }

        val totalMinutes = remainingMillis / 60_000L
        if (totalMinutes == 0L) {
            return LESS_THAN_ONE_MINUTE_TEXT
        }

        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val parts = mutableListOf<String>()
        if (days > 0) {
            parts.add("${days}d")
        }
        if (hours > 0) {
            parts.add("${hours}h")
        }
        if (minutes > 0 || (days == 0L && hours == 0L) || (hours > 0L && minutes == 0L)) {
            parts.add("${minutes}m")
        }

        return if (parts.isEmpty()) {
            LESS_THAN_ONE_MINUTE_TEXT
        } else {
            parts.joinToString(" ")
        }
    }

    /**
     * Formats the scheduled alarm target timestamp into human-readable date and time.
     * e.g., "Today at 07:00", "Tomorrow at 07:00", or "Sun, Oct 4 at 07:00".
     */
    fun formatTargetDateTime(triggerTimeMillis: Long?): String {
        if (triggerTimeMillis == null || triggerTimeMillis <= 0L) {
            return "No scheduled alarm"
        }

        val targetCal = Calendar.getInstance().apply { timeInMillis = triggerTimeMillis }
        val nowCal = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormat.format(Date(triggerTimeMillis))

        val isToday = targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = targetCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

        return when {
            isToday -> "Today at $timeString"
            isTomorrow -> "Tomorrow at $timeString"
            else -> {
                val fullFormat = SimpleDateFormat("EEE, MMM d 'at' HH:mm", Locale.getDefault())
                fullFormat.format(Date(triggerTimeMillis))
            }
        }
    }

    /**
     * Concise format for compact widget layouts.
     * e.g., "Today 07:00", "Tom 07:00", or "Sun 07:00".
     */
    fun formatShortTargetDateTime(triggerTimeMillis: Long?): String {
        if (triggerTimeMillis == null || triggerTimeMillis <= 0L) {
            return ""
        }

        val targetCal = Calendar.getInstance().apply { timeInMillis = triggerTimeMillis }
        val nowCal = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormat.format(Date(triggerTimeMillis))

        val isToday = targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        val tomorrowCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = targetCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

        return when {
            isToday -> "Today $timeString"
            isTomorrow -> "Tom $timeString"
            else -> {
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                "${dayFormat.format(Date(triggerTimeMillis))} $timeString"
            }
        }
    }
}
