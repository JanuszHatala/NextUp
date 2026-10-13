package com.nextup.alarmcountdown

import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import com.nextup.alarmcountdown.util.AlarmFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GroupedAlarmPatternTest {

    @Test
    fun testDaysLabel_EveryDay() {
        val allDays = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
        val group = GroupedAlarmPattern(hour = 5, minute = 0, activeDays = allDays)
        assertEquals("Every day", group.daysLabel)
    }

    @Test
    fun testDaysLabel_Weekdays() {
        val weekdays = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
        val group = GroupedAlarmPattern(hour = 7, minute = 30, activeDays = weekdays)
        assertEquals("Weekdays", group.daysLabel)
    }

    @Test
    fun testDaysLabel_Weekend() {
        val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val group = GroupedAlarmPattern(hour = 9, minute = 0, activeDays = weekend)
        assertEquals("Weekend", group.daysLabel)
    }

    @Test
    fun testDaysLabel_CustomCombinations() {
        // Thu, Fri
        val thuFri = GroupedAlarmPattern(
            hour = 5,
            minute = 0,
            activeDays = setOf(Calendar.FRIDAY, Calendar.THURSDAY) // order shouldn't matter for input
        )
        assertEquals("Thu, Fri", thuFri.daysLabel)

        // Mon, Wed, Fri
        val mwf = GroupedAlarmPattern(
            hour = 6,
            minute = 15,
            activeDays = setOf(Calendar.FRIDAY, Calendar.MONDAY, Calendar.WEDNESDAY)
        )
        assertEquals("Mon, Wed, Fri", mwf.daysLabel)

        // Single day: Mon
        val monOnly = GroupedAlarmPattern(
            hour = 8,
            minute = 0,
            activeDays = setOf(Calendar.MONDAY)
        )
        assertEquals("Mon", monOnly.daysLabel)

        // Empty
        val emptyGroup = GroupedAlarmPattern(hour = 5, minute = 0, activeDays = emptySet())
        assertEquals("None", emptyGroup.daysLabel)
    }

    @Test
    fun testFromPatternsGrouping() {
        val patterns = listOf(
            AlarmPattern("P_5_0500", 5, 0, Calendar.THURSDAY, isActive = true),
            AlarmPattern("P_6_0500", 5, 0, Calendar.FRIDAY, isActive = true),
            AlarmPattern("P_2_0700", 7, 0, Calendar.MONDAY, isActive = true),
            AlarmPattern("P_5_0500_inactive", 5, 0, Calendar.SATURDAY, isActive = false)
        )

        val grouped = GroupedAlarmPattern.fromPatterns(patterns)
        assertEquals(2, grouped.size)

        val group5am = grouped.firstOrNull { it.hour == 5 && it.minute == 0 }
        assertNotNull(group5am)
        assertEquals(setOf(Calendar.THURSDAY, Calendar.FRIDAY), group5am?.activeDays)
        assertEquals("Thu, Fri", group5am?.daysLabel)

        val group7am = grouped.firstOrNull { it.hour == 7 && it.minute == 0 }
        assertNotNull(group7am)
        assertEquals(setOf(Calendar.MONDAY), group7am?.activeDays)
        assertEquals("Mon", group7am?.daysLabel)
    }

    @Test
    fun testNextOccurrenceAndExclusion() {
        // Thursday 12:00
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis

        // Alarm at 05:00 on Thursday and Friday
        val group = GroupedAlarmPattern(
            hour = 5,
            minute = 0,
            activeDays = setOf(Calendar.THURSDAY, Calendar.FRIDAY)
        )

        // Next occurrence without exclusion: since Thursday 05:00 is in the past for today (12:00),
        // next occurrence should be Friday 05:00.
        val nextOccurrence = group.getNextOccurrenceMillis(now)
        assertNotNull(nextOccurrence)
        val occurCal = Calendar.getInstance().apply { timeInMillis = nextOccurrence!! }
        assertEquals(Calendar.FRIDAY, occurCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(5, occurCal.get(Calendar.HOUR_OF_DAY))

        // When Friday is excluded (e.g., Friday alarm is currently active):
        // next occurrence should wrap around to next Thursday 05:00.
        val nextAfterExcludingFriday = group.getNextOccurrenceMillis(now, excludeDayOfWeek = Calendar.FRIDAY)
        assertNotNull(nextAfterExcludingFriday)
        val wrapCal = Calendar.getInstance().apply { timeInMillis = nextAfterExcludingFriday!! }
        assertEquals(Calendar.THURSDAY, wrapCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(5, wrapCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun testFormatGroupedRoutineLine() {
        val now = 1_000_000_000L
        val occurrence = now + (22 * 60 * 60_000L) // in 22h

        val formatted = AlarmFormatter.formatGroupedRoutineLine(
            daysLabel = "Weekdays",
            timeFormatted = "05:00",
            occurrenceMillis = occurrence,
            nowMillis = now
        )

        assertEquals("Weekdays 05:00 • in 22h", formatted)
    }

    @Test
    fun testChronologicalSortingByRemainingTimeStartingFromNearestAlarm() {
        // Today is Friday at 09:13:00
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 13)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = baseCal.timeInMillis

        // Alarm 1: 09:14 on Thu, Fri (in 1m)
        val group914 = GroupedAlarmPattern(9, 14, setOf(Calendar.THURSDAY, Calendar.FRIDAY))
        // Alarm 2: 20:45 on Thu, Fri (in 11h 32m)
        val group2045 = GroupedAlarmPattern(20, 45, setOf(Calendar.THURSDAY, Calendar.FRIDAY))
        // Alarm 3: 05:00 on Thu, Fri (next occurrence is next Thursday 05:00, in 5d 19h)
        val group0500 = GroupedAlarmPattern(5, 0, setOf(Calendar.THURSDAY, Calendar.FRIDAY))

        val list = listOf(group0500, group2045, group914)
        val sorted = list.mapNotNull { group ->
            val nextOccur = group.getNextOccurrenceMillis(now)
            if (nextOccur != null) Pair(group, nextOccur) else null
        }.sortedBy { it.second }

        // Must start from 09:14 (in 1m), then 20:45 (in 11h), then 05:00 (in 5d 19h)
        assertEquals(9, sorted[0].first.hour)
        assertEquals(14, sorted[0].first.minute)

        assertEquals(20, sorted[1].first.hour)
        assertEquals(45, sorted[1].first.minute)

        assertEquals(5, sorted[2].first.hour)
        assertEquals(0, sorted[2].first.minute)
    }
}
