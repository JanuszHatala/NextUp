package com.nextup.alarmcountdown.data.model

import java.util.Calendar

data class GroupedAlarmPattern(
    val hour: Int,
    val minute: Int,
    val activeDays: Set<Int>, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val allPatterns: List<AlarmPattern> = emptyList()
) {
    val timeFormatted: String
        get() = String.format("%02d:%02d", hour, minute)

    val isActive: Boolean
        get() = activeDays.isNotEmpty()

    /**
     * Formats the active days:
     * - "Every day" if all 7 days are active (Mon-Sun)
     * - "Weekdays" if Monday to Friday are active
     * - "Weekend" if Saturday and Sunday are active
     * - Comma-separated abbreviated days (e.g. "Thu, Fri", "Mon, Wed, Fri") ordered Monday -> Sunday.
     */
    val daysLabel: String
        get() {
            if (activeDays.isEmpty()) return "None"
            val weekdays = setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
            val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
            val allDays = weekdays + weekend

            return when (activeDays) {
                allDays -> "Every day"
                weekdays -> "Weekdays"
                weekend -> "Weekend"
                else -> {
                    val orderedDays = listOf(
                        Calendar.MONDAY,
                        Calendar.TUESDAY,
                        Calendar.WEDNESDAY,
                        Calendar.THURSDAY,
                        Calendar.FRIDAY,
                        Calendar.SATURDAY,
                        Calendar.SUNDAY
                    )
                    orderedDays.filter { it in activeDays }
                        .joinToString(", ") { dayOfWeekToShortName(it) }
                }
            }
        }

    /**
     * Calculates the nearest upcoming occurrence timestamp among active days, starting from [fromMillis].
     * If [confirmedNextAlarmMillis] is provided, any routine alarm occurrence prior to that confirmed alarm
     * is recognized as deactivated/skipped for the current cycle and advanced to the next occurrence.
     * If [excludeDayOfWeek] is specified, that day is excluded so that the subsequent day's occurrence is found.
     */
    fun getNextOccurrenceMillis(
        fromMillis: Long = System.currentTimeMillis(),
        confirmedNextAlarmMillis: Long? = null,
        excludeDayOfWeek: Int? = null
    ): Long? {
        val eligibleDays = if (excludeDayOfWeek != null) {
            val withoutExcluded = activeDays - excludeDayOfWeek
            if (withoutExcluded.isNotEmpty()) withoutExcluded else activeDays
        } else {
            activeDays
        }

        if (eligibleDays.isEmpty()) return null

        var nearestTime: Long? = null
        for (day in eligibleDays) {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = fromMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (targetCal.get(Calendar.DAY_OF_WEEK) != day || targetCal.timeInMillis < fromMillis - 30_000L) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            var time = targetCal.timeInMillis

            // If a confirmed system alarm exists, no other alarm can occur prior to it in this cycle.
            // Any theoretical occurrence earlier than confirmedNextAlarmMillis was skipped/deactivated by the user.
            if (confirmedNextAlarmMillis != null && confirmedNextAlarmMillis > fromMillis) {
                val matchesConfirmed = Math.abs(time - confirmedNextAlarmMillis) < 90_000L
                if (!matchesConfirmed && time < confirmedNextAlarmMillis) {
                    while (time < confirmedNextAlarmMillis) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 7)
                        time = targetCal.timeInMillis
                    }
                }
            }

            if (nearestTime == null || time < nearestTime) {
                nearestTime = time
            }
        }
        return nearestTime
    }

    companion object {
        fun dayOfWeekToShortName(dayOfWeek: Int): String = when (dayOfWeek) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            else -> "?"
        }

        /**
         * Groups a list of [AlarmPattern] instances by unique time (hour, minute).
         */
        fun fromPatterns(patterns: List<AlarmPattern>): List<GroupedAlarmPattern> {
            val groupedByTime = patterns.groupBy { Pair(it.hour, it.minute) }
            return groupedByTime.map { (timePair, list) ->
                val (h, m) = timePair
                val activeDays = list.filter { it.isActive }.map { it.dayOfWeek }.toSet()
                GroupedAlarmPattern(
                    hour = h,
                    minute = m,
                    activeDays = activeDays,
                    allPatterns = list
                )
            }
        }
    }
}
