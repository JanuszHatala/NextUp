package com.nextup.alarmcountdown.data

import com.nextup.alarmcountdown.data.model.AlarmEvent
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import java.util.Calendar
import kotlin.math.roundToInt

enum class StatsTimeframe(val label: String, val days: Int?) {
    LAST_30_DAYS("30 Days", 30),
    ALL_TIME("All Time", null)
}

enum class TimeOfDayBucket(val label: String, val rangeLabel: String) {
    MORNING("Morning", "05:00-11:59"),
    AFTERNOON("Afternoon", "12:00-16:59"),
    EVENING("Evening", "17:00-21:59"),
    NIGHT("Night", "22:00-04:59")
}

data class DayOfWeekStat(
    val dayOfWeek: Int,
    val shortLabel: String,
    val confirmedCount: Int,
    val missedCount: Int
)

data class TimeOfDayStat(
    val bucket: TimeOfDayBucket,
    val count: Int,
    val fraction: Float
)

data class AlarmStatsSummary(
    val totalConfirmed: Int,
    val totalMissed: Int,
    val reliabilityPercent: Int?,
    val activeRoutineCount: Int,
    val inactivePatternCount: Int,
    val mostFrequentTime: String?,
    val mostFrequentCount: Int,
    val avgEarliestDailyAlarmFormatted: String?,
    val weekdayAvgEarliestFormatted: String?,
    val weekendAvgEarliestFormatted: String?,
    val dayOfWeekCounts: List<DayOfWeekStat>,
    val timeOfDayCounts: List<TimeOfDayStat>
)

object AlarmStatsCalculator {

    private const val EVENT_CONFIRMED = "CONFIRMED_SEEN"
    private const val EVENT_MISSED = "MISSED_OR_DEACTIVATED"
    private const val MILLIS_PER_DAY = 86_400_000L

    private val orderedDays = listOf(
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun"
    )

    fun compute(
        events: List<AlarmEvent>,
        groupedPatterns: List<GroupedAlarmPattern>,
        timeframe: StatsTimeframe,
        nowMillis: Long = System.currentTimeMillis()
    ): AlarmStatsSummary {
        val cutoffMillis = timeframe.days?.let { nowMillis - it.toLong() * MILLIS_PER_DAY }
        val filtered = if (cutoffMillis == null) {
            events
        } else {
            events.filter { event ->
                val ts = if (event.scheduledEpochMillis > 0L) event.scheduledEpochMillis else event.recordedEpochMillis
                ts >= cutoffMillis
            }
        }

        val confirmedEvents = filtered.filter { it.eventType == EVENT_CONFIRMED }
        val missedEvents = filtered.filter { it.eventType == EVENT_MISSED }

        val totalConfirmed = confirmedEvents.size
        val totalMissed = missedEvents.size
        val totalTracked = totalConfirmed + totalMissed
        val reliabilityPercent = if (totalTracked > 0) {
            ((totalConfirmed * 100.0) / totalTracked).roundToInt()
        } else {
            null
        }

        val activeRoutineCount = groupedPatterns.count { it.isActive }
        val inactivePatternCount = groupedPatterns.count { !it.isActive }

        // Most frequent confirmed alarm time, tie-breaking by earliest minute of day
        val topTimeEntry = confirmedEvents
            .groupBy { Pair(it.alarmHour, it.alarmMinute) }
            .entries
            .maxWithOrNull(
                compareBy<Map.Entry<Pair<Int, Int>, List<AlarmEvent>>> { it.value.size }
                    .thenByDescending { it.key.first * 60 + it.key.second }
            )
        val mostFrequentTime = topTimeEntry?.key?.let { (h, m) -> formatMinutes(h * 60 + m) }
        val mostFrequentCount = topTimeEntry?.value?.size ?: 0

        // Average earliest confirmed alarm per distinct calendar day
        val dailyEarliest = confirmedEvents
            .groupBy { Triple(it.year, it.month, it.dayOfMonth) }
            .values
            .mapNotNull { dayEvents ->
                val earliest = dayEvents.minByOrNull { it.alarmHour * 60 + it.alarmMinute }
                earliest?.let {
                    Pair(it.alarmHour * 60 + it.alarmMinute, it.isWeekend)
                }
            }

        val avgEarliestDailyAlarmFormatted = formatAverageMinutes(dailyEarliest.map { it.first })
        val weekdayAvgEarliestFormatted = formatAverageMinutes(dailyEarliest.filter { !it.second }.map { it.first })
        val weekendAvgEarliestFormatted = formatAverageMinutes(dailyEarliest.filter { it.second }.map { it.first })

        val confirmedByDay = confirmedEvents.groupingBy { it.dayOfWeek }.eachCount()
        val missedByDay = missedEvents.groupingBy { it.dayOfWeek }.eachCount()
        val dayOfWeekCounts = orderedDays.map { (day, label) ->
            DayOfWeekStat(
                dayOfWeek = day,
                shortLabel = label,
                confirmedCount = confirmedByDay[day] ?: 0,
                missedCount = missedByDay[day] ?: 0
            )
        }

        val bucketCounts = confirmedEvents.groupingBy { bucketForHour(it.alarmHour) }.eachCount()
        val timeOfDayCounts = TimeOfDayBucket.entries.map { bucket ->
            val count = bucketCounts[bucket] ?: 0
            val fraction = if (totalConfirmed > 0) count.toFloat() / totalConfirmed else 0f
            TimeOfDayStat(
                bucket = bucket,
                count = count,
                fraction = fraction
            )
        }

        return AlarmStatsSummary(
            totalConfirmed = totalConfirmed,
            totalMissed = totalMissed,
            reliabilityPercent = reliabilityPercent,
            activeRoutineCount = activeRoutineCount,
            inactivePatternCount = inactivePatternCount,
            mostFrequentTime = mostFrequentTime,
            mostFrequentCount = mostFrequentCount,
            avgEarliestDailyAlarmFormatted = avgEarliestDailyAlarmFormatted,
            weekdayAvgEarliestFormatted = weekdayAvgEarliestFormatted,
            weekendAvgEarliestFormatted = weekendAvgEarliestFormatted,
            dayOfWeekCounts = dayOfWeekCounts,
            timeOfDayCounts = timeOfDayCounts
        )
    }

    private fun bucketForHour(hour: Int): TimeOfDayBucket = when (hour) {
        in 5..11 -> TimeOfDayBucket.MORNING
        in 12..16 -> TimeOfDayBucket.AFTERNOON
        in 17..21 -> TimeOfDayBucket.EVENING
        else -> TimeOfDayBucket.NIGHT
    }

    private fun formatAverageMinutes(values: List<Int>): String? {
        if (values.isEmpty()) return null
        val avg = values.average().roundToInt()
        return formatMinutes(avg)
    }

    private fun formatMinutes(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 23 * 60 + 59)
        return String.format("%02d:%02d", clamped / 60, clamped % 60)
    }
}
