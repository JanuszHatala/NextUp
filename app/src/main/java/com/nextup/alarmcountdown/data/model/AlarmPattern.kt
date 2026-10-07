package com.nextup.alarmcountdown.data.model

import java.util.Calendar

data class AlarmPattern(
    val patternId: String, // e.g. "P_2_0700" (DayOfWeek_HHmm)
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val isActive: Boolean = true,
    val firstSeenMillis: Long = System.currentTimeMillis(),
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val confirmedCount: Int = 1,
    val missedCount: Int = 0,
    val consecutiveWeeks: Int = 1,
    val confidenceScore: Float = 1.0f
) {
    val timeFormatted: String
        get() = String.format("%02d:%02d", hour, minute)

    val dayNameShort: String
        get() = when (dayOfWeek) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "?"
        }

    /**
     * Calculates the next upcoming occurrence timestamp of this pattern from `fromMillis`.
     */
    fun getNextOccurrenceMillis(fromMillis: Long = System.currentTimeMillis()): Long {
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Advance days until day of week matches and is in the future
        while (targetCal.get(Calendar.DAY_OF_WEEK) != dayOfWeek || targetCal.timeInMillis <= fromMillis) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return targetCal.timeInMillis
    }

    companion object {
        fun buildPatternId(dayOfWeek: Int, hour: Int, minute: Int): String {
            return String.format("P_%d_%02d%02d", dayOfWeek, hour, minute)
        }
    }
}
