package com.nextup.alarmcountdown

import com.nextup.alarmcountdown.data.model.AlarmEvent
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.RoutineStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmPatternTest {

    @Test
    fun testPatternIdGeneration() {
        val id = AlarmPattern.buildPatternId(Calendar.MONDAY, 7, 30)
        assertEquals("P_2_0730", id)
    }

    @Test
    fun testAlarmEventFromTriggerTime() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 8, 15, 0)
        }
        val event = AlarmEvent.fromTriggerTime(cal.timeInMillis, "CONFIRMED_SEEN")

        assertEquals(8, event.alarmHour)
        assertEquals(15, event.alarmMinute)
        assertEquals(10, event.month)
        assertEquals(2026, event.year)
        assertEquals("AUTUMN", event.season)
        assertEquals("CONFIRMED_SEEN", event.eventType)
    }

    @Test
    fun testNextOccurrenceCalculation() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis

        // Alarm for Thursday at 07:00
        val pattern = AlarmPattern(
            patternId = "P_5_0700",
            hour = 7,
            minute = 0,
            dayOfWeek = Calendar.THURSDAY
        )

        val nextTime = pattern.getNextOccurrenceMillis(now)
        assertTrue(nextTime > now)

        val targetCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(Calendar.THURSDAY, targetCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, targetCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, targetCal.get(Calendar.MINUTE))
    }

    @Test
    fun testRoutineStatisticsFormatting() {
        val stats = RoutineStatistics(
            statId = "SEASONAL_2026-AUTUMN_ALL",
            periodType = "SEASONAL",
            periodKey = "2026-AUTUMN",
            dayCategory = "ALL",
            avgWakeTimeMinutes = 420.0, // 07:00
            earliestWakeMinutes = 390,  // 06:30
            latestWakeMinutes = 480,    // 08:00
            totalConfirmed = 10,
            totalMissed = 2,
            reliabilityRate = 10.0 / 12.0
        )

        assertEquals("07:00", stats.avgWakeFormatted)
        assertEquals("06:30", stats.earliestWakeFormatted)
        assertEquals("08:00", stats.latestWakeFormatted)
    }

    @Test
    fun testChronologicalRoutineSortingStartingFromActiveAlarm() {
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val activeAlarmTime = baseCal.timeInMillis

        val patWedMorning = AlarmPattern(
            patternId = AlarmPattern.buildPatternId(Calendar.WEDNESDAY, 8, 30),
            hour = 8,
            minute = 30,
            dayOfWeek = Calendar.WEDNESDAY
        )
        val patWedCurrent = AlarmPattern(
            patternId = AlarmPattern.buildPatternId(Calendar.WEDNESDAY, 20, 45),
            hour = 20,
            minute = 45,
            dayOfWeek = Calendar.WEDNESDAY
        )
        val patThuEarly = AlarmPattern(
            patternId = AlarmPattern.buildPatternId(Calendar.THURSDAY, 5, 0),
            hour = 5,
            minute = 0,
            dayOfWeek = Calendar.THURSDAY
        )
        val patThuLate = AlarmPattern(
            patternId = AlarmPattern.buildPatternId(Calendar.THURSDAY, 5, 30),
            hour = 5,
            minute = 30,
            dayOfWeek = Calendar.THURSDAY
        )

        val patterns = listOf(patWedMorning, patWedCurrent, patThuEarly, patThuLate)

        // Filter out current active alarm pattern
        val filtered = patterns.filter { it.patternId != patWedCurrent.patternId }

        // Sort chronologically starting from activeAlarmTime (20:45)
        val sorted = filtered.sortedBy { it.getNextOccurrenceMillis(activeAlarmTime) }

        assertEquals(3, sorted.size)
        // 1st should be Thu 05:00
        assertEquals(patThuEarly.patternId, sorted[0].patternId)
        // 2nd should be Thu 05:30
        assertEquals(patThuLate.patternId, sorted[1].patternId)
        // 3rd should be Wed 08:30 (next week)
        assertEquals(patWedMorning.patternId, sorted[2].patternId)
    }
}
