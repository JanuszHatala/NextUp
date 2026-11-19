package com.nextup.alarmcountdown

import com.nextup.alarmcountdown.data.AlarmStatsCalculator
import com.nextup.alarmcountdown.data.StatsTimeframe
import com.nextup.alarmcountdown.data.TimeOfDayBucket
import com.nextup.alarmcountdown.data.model.AlarmEvent
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AlarmStatsCalculatorTest {

    private fun event(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        eventType: String = "CONFIRMED_SEEN",
        offsetDaysFromNow: Int = -1,
        nowMillis: Long = 1_700_000_000_000L
    ): AlarmEvent {
        val ts = nowMillis + offsetDaysFromNow * 86_400_000L
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        return AlarmEvent(
            id = 0,
            alarmHour = hour,
            alarmMinute = minute,
            scheduledEpochMillis = ts,
            eventType = eventType,
            recordedEpochMillis = ts,
            dayOfWeek = dayOfWeek,
            dayOfMonth = dayOfMonth,
            month = month,
            year = year,
            season = "AUTUMN",
            isWeekend = isWeekend
        )
    }

    @Test
    fun testEmptyEventsReturnsSafeDefaults() {
        val summary = AlarmStatsCalculator.compute(
            events = emptyList(),
            groupedPatterns = emptyList(),
            timeframe = StatsTimeframe.ALL_TIME
        )

        assertEquals(0, summary.totalConfirmed)
        assertEquals(0, summary.totalMissed)
        assertNull(summary.reliabilityPercent)
        assertNull(summary.mostFrequentTime)
        assertNull(summary.avgEarliestDailyAlarmFormatted)
        assertEquals(7, summary.dayOfWeekCounts.size)
        assertEquals(Calendar.MONDAY, summary.dayOfWeekCounts.first().dayOfWeek)
        assertEquals(Calendar.SUNDAY, summary.dayOfWeekCounts.last().dayOfWeek)
        assertEquals(4, summary.timeOfDayCounts.size)
    }

    @Test
    fun testTimeOfDayBucketingAndMidnightWrap() {
        val now = 1_700_000_000_000L
        val events = listOf(
            event(2026, 11, 10, Calendar.TUESDAY, 7, 0, nowMillis = now),   // MORNING
            event(2026, 11, 11, Calendar.WEDNESDAY, 14, 0, nowMillis = now), // AFTERNOON
            event(2026, 11, 12, Calendar.THURSDAY, 20, 45, nowMillis = now), // EVENING
            event(2026, 11, 13, Calendar.FRIDAY, 22, 30, nowMillis = now),   // NIGHT
            event(2026, 11, 14, Calendar.SATURDAY, 3, 30, nowMillis = now)   // NIGHT
        )

        val summary = AlarmStatsCalculator.compute(events, emptyList(), StatsTimeframe.ALL_TIME, now)
        val byBucket = summary.timeOfDayCounts.associateBy { it.bucket }

        assertEquals(1, byBucket[TimeOfDayBucket.MORNING]?.count)
        assertEquals(1, byBucket[TimeOfDayBucket.AFTERNOON]?.count)
        assertEquals(1, byBucket[TimeOfDayBucket.EVENING]?.count)
        assertEquals(2, byBucket[TimeOfDayBucket.NIGHT]?.count)
        assertEquals(0.4f, byBucket[TimeOfDayBucket.NIGHT]?.fraction ?: 0f, 0.001f)
    }

    @Test
    fun testEarliestDailyAlarmIgnoresEveningAlarmsOnSameDay() {
        val now = 1_700_000_000_000L
        // Day 1 (Mon): 06:00 + 20:45 -> earliest is 06:00 (360)
        // Day 2 (Tue): 06:20 + 20:45 -> earliest is 06:20 (380) -> Weekday avg = 06:10
        // Day 3 (Sat): 08:00 + 20:45 -> earliest is 08:00 (480) -> Weekend avg = 08:00
        // Overall avg = (360 + 380 + 480) / 3 = 406.67 -> 407 -> 06:47
        val events = listOf(
            event(2026, 11, 16, Calendar.MONDAY, 6, 0, nowMillis = now),
            event(2026, 11, 16, Calendar.MONDAY, 20, 45, nowMillis = now),
            event(2026, 11, 17, Calendar.TUESDAY, 6, 20, nowMillis = now),
            event(2026, 11, 17, Calendar.TUESDAY, 20, 45, nowMillis = now),
            event(2026, 11, 14, Calendar.SATURDAY, 8, 0, nowMillis = now),
            event(2026, 11, 14, Calendar.SATURDAY, 20, 45, nowMillis = now)
        )

        val summary = AlarmStatsCalculator.compute(events, emptyList(), StatsTimeframe.ALL_TIME, now)

        assertEquals("06:10", summary.weekdayAvgEarliestFormatted)
        assertEquals("08:00", summary.weekendAvgEarliestFormatted)
        assertEquals("06:47", summary.avgEarliestDailyAlarmFormatted)
        assertEquals("20:45", summary.mostFrequentTime)
        assertEquals(3, summary.mostFrequentCount)
    }

    @Test
    fun testTimeframeFilteringReliabilityAndDayCounts() {
        val now = 1_700_000_000_000L
        val events = listOf(
            event(2026, 11, 18, Calendar.WEDNESDAY, 7, 0, "CONFIRMED_SEEN", offsetDaysFromNow = -1, nowMillis = now),
            event(2026, 11, 17, Calendar.TUESDAY, 7, 0, "CONFIRMED_SEEN", offsetDaysFromNow = -2, nowMillis = now),
            event(2026, 11, 16, Calendar.MONDAY, 7, 0, "MISSED_OR_DEACTIVATED", offsetDaysFromNow = -3, nowMillis = now),
            // Outside 30-day window (-45 days)
            event(2026, 10, 1, Calendar.THURSDAY, 5, 0, "CONFIRMED_SEEN", offsetDaysFromNow = -45, nowMillis = now)
        )
        val groups = listOf(
            GroupedAlarmPattern(
                hour = 7,
                minute = 0,
                activeDays = setOf(Calendar.TUESDAY, Calendar.WEDNESDAY),
                allPatterns = listOf(AlarmPattern("P_3_0700", 7, 0, Calendar.TUESDAY, isActive = true))
            ),
            GroupedAlarmPattern(
                hour = 3,
                minute = 30,
                activeDays = emptySet(),
                allPatterns = listOf(AlarmPattern("P_4_0330", 3, 30, Calendar.WEDNESDAY, isActive = false))
            )
        )

        val last30 = AlarmStatsCalculator.compute(events, groups, StatsTimeframe.LAST_30_DAYS, now)
        assertEquals(2, last30.totalConfirmed)
        assertEquals(1, last30.totalMissed)
        assertEquals(67, last30.reliabilityPercent)
        assertEquals(1, last30.activeRoutineCount)
        assertEquals(1, last30.inactivePatternCount)
        assertEquals(1, last30.dayOfWeekCounts.first { it.dayOfWeek == Calendar.MONDAY }.missedCount)
        assertEquals(1, last30.dayOfWeekCounts.first { it.dayOfWeek == Calendar.TUESDAY }.confirmedCount)

        val allTime = AlarmStatsCalculator.compute(events, groups, StatsTimeframe.ALL_TIME, now)
        assertEquals(3, allTime.totalConfirmed)
        assertEquals(1, allTime.totalMissed)
        assertEquals(75, allTime.reliabilityPercent)
    }
}
