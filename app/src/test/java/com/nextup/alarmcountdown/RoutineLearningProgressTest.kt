package com.nextup.alarmcountdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class RoutineLearningProgressTest {

    private fun computeDays(startEpoch: Long, nowEpoch: Long): Pair<Int, Boolean> {
        val startDay = Instant.ofEpochMilli(startEpoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val today = Instant.ofEpochMilli(nowEpoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val daysElapsed = ChronoUnit.DAYS.between(startDay, today).coerceAtLeast(0L)
        val dayNumber = (daysElapsed + 1).coerceIn(1L, 7L).toInt()
        val isLearningPeriod = daysElapsed in 0 until 7
        return Pair(dayNumber, isLearningPeriod)
    }

    @Test
    fun testSameDayIsDay1() {
        val start = System.currentTimeMillis()
        val now = start + 3600_000L // 1 hour later same day
        val (day, isLearning) = computeDays(start, now)
        assertEquals(1, day)
        assertTrue(isLearning)
    }

    @Test
    fun testNextDayMorningAdvancesEvenUnder24Hours() {
        // Started Monday at 23:00, checked Tuesday at 07:00 (8 hours later)
        val zone = ZoneId.systemDefault()
        val mondayNight = LocalDate.of(2026, 10, 12).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val tuesdayMorning = LocalDate.of(2026, 10, 13).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()

        val (day, isLearning) = computeDays(mondayNight, tuesdayMorning)
        assertEquals(2, day)
        assertTrue(isLearning)
    }

    @Test
    fun testStartedWithPastRecordedAlarms3DaysAgo() {
        // Earliest alarm in DB was 3 calendar days ago
        val zone = ZoneId.systemDefault()
        val threeDaysAgo = LocalDate.of(2026, 10, 14).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 10, 17).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        val (day, isLearning) = computeDays(threeDaysAgo, today)
        assertEquals(4, day) // Day 4 of 7
        assertTrue(isLearning)
    }

    @Test
    fun testDay7IsLastActiveLearningDay() {
        // Started 6 days ago -> Day 7 of 7
        val zone = ZoneId.systemDefault()
        val sixDaysAgo = LocalDate.of(2026, 10, 11).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 10, 17).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()

        val (day, isLearning) = computeDays(sixDaysAgo, today)
        assertEquals(7, day)
        assertTrue(isLearning)
    }

    @Test
    fun testAfter7DaysLearningIsCompleted() {
        // Started 10 days ago (like our DB with alarms from Oct 7 to Oct 17)
        val zone = ZoneId.systemDefault()
        val tenDaysAgo = LocalDate.of(2026, 10, 7).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 10, 17).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        val (day, isLearning) = computeDays(tenDaysAgo, today)
        assertEquals(7, day)
        assertFalse(isLearning) // Routine learning complete, reference locked
    }
}
