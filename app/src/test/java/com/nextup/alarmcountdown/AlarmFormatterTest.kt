package com.nextup.alarmcountdown

import com.nextup.alarmcountdown.util.AlarmFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmFormatterTest {

    private val baseNow = 1_000_000_000L

    @Test
    fun testNullOrZeroOrPastAlarm() {
        assertEquals("No alarm set", AlarmFormatter.formatRemaining(null, baseNow))
        assertEquals("No alarm set", AlarmFormatter.formatRemaining(0L, baseNow))
        assertEquals("No alarm set", AlarmFormatter.formatRemaining(-100L, baseNow))
        // Alarm in past by more than 1 minute
        assertEquals("No alarm set", AlarmFormatter.formatRemaining(baseNow - 120_000L, baseNow))
    }

    @Test
    fun testLessThanOneMinute() {
        // 30 seconds remaining
        assertEquals("< 1m", AlarmFormatter.formatRemaining(baseNow + 30_000L, baseNow))
        // 59 seconds remaining
        assertEquals("< 1m", AlarmFormatter.formatRemaining(baseNow + 59_999L, baseNow))
        // Just triggered within last 30s
        assertEquals("< 1m", AlarmFormatter.formatRemaining(baseNow - 10_000L, baseNow))
    }

    @Test
    fun testOnlyMinutesRemaining_NoDaysNoHours() {
        // 1 minute
        assertEquals("1m", AlarmFormatter.formatRemaining(baseNow + 60_000L, baseNow))
        // 45 minutes
        assertEquals("45m", AlarmFormatter.formatRemaining(baseNow + 45 * 60_000L, baseNow))
        // 59 minutes
        assertEquals("59m", AlarmFormatter.formatRemaining(baseNow + 59 * 60_000L, baseNow))
    }

    @Test
    fun testHoursAndMinutesRemaining_NoDays() {
        // 1 hour 0 minutes -> days omitted, hours shown, minutes shown
        assertEquals("1h 0m", AlarmFormatter.formatRemaining(baseNow + 60 * 60_000L, baseNow))
        // 2 hours 15 minutes
        assertEquals("2h 15m", AlarmFormatter.formatRemaining(baseNow + (2 * 60 + 15) * 60_000L, baseNow))
        // 23 hours 59 minutes
        assertEquals("23h 59m", AlarmFormatter.formatRemaining(baseNow + (23 * 60 + 59) * 60_000L, baseNow))
    }

    @Test
    fun testDaysHoursAndMinutesRemaining() {
        // 1 day 2 hours 30 minutes
        val oneDayTwoHoursThirtyMins = (24 * 60 + 2 * 60 + 30) * 60_000L
        assertEquals("1d 2h 30m", AlarmFormatter.formatRemaining(baseNow + oneDayTwoHoursThirtyMins, baseNow))

        // 3 days 0 hours 15 minutes -> hours is 0, so hours should be omitted!
        val threeDaysFifteenMins = (3 * 24 * 60 + 15) * 60_000L
        assertEquals("3d 15m", AlarmFormatter.formatRemaining(baseNow + threeDaysFifteenMins, baseNow))

        // 5 days 4 hours 0 minutes
        val fiveDaysFourHours = (5 * 24 * 60 + 4 * 60) * 60_000L
        assertEquals("5d 4h 0m", AlarmFormatter.formatRemaining(baseNow + fiveDaysFourHours, baseNow))
    }

    @Test
    fun testShortTargetDateTime() {
        assertEquals("", AlarmFormatter.formatShortTargetDateTime(null))
        assertEquals("", AlarmFormatter.formatShortTargetDateTime(0L))
        val now = System.currentTimeMillis()
        val shortFormat = AlarmFormatter.formatShortTargetDateTime(now + 3600_000L)
        assert(shortFormat.isNotEmpty())
    }

    @Test
    fun testFormatRemainingConcise() {
        assertEquals("No alarm set", AlarmFormatter.formatRemainingConcise(null, baseNow))
        assertEquals("No alarm set", AlarmFormatter.formatRemainingConcise(0L, baseNow))
        assertEquals("< 1m", AlarmFormatter.formatRemainingConcise(baseNow + 30_000L, baseNow))

        // Minutes only
        assertEquals("45m", AlarmFormatter.formatRemainingConcise(baseNow + 45 * 60_000L, baseNow))

        // Hours only
        assertEquals("3h", AlarmFormatter.formatRemainingConcise(baseNow + 3 * 60 * 60_000L, baseNow))

        // Hours and minutes
        assertEquals("9h 42m", AlarmFormatter.formatRemainingConcise(baseNow + (9 * 60 + 42) * 60_000L, baseNow))

        // Days and hours: minutes MUST be omitted to conserve horizontal space in widgets
        val sixDaysSeventeenHoursTwentyFourMins = (6 * 24 * 60 + 17 * 60 + 24) * 60_000L
        assertEquals("6d 17h", AlarmFormatter.formatRemainingConcise(baseNow + sixDaysSeventeenHoursTwentyFourMins, baseNow))

        // Days only (hours is 0)
        val threeDaysZeroHoursTwentyMins = (3 * 24 * 60 + 20) * 60_000L
        assertEquals("3d", AlarmFormatter.formatRemainingConcise(baseNow + threeDaysZeroHoursTwentyMins, baseNow))
    }

    @Test
    fun testFormatRoutineLine() {
        val sixDaysSeventeenHours = (6 * 24 * 60 + 17 * 60 + 24) * 60_000L
        val formatted = AlarmFormatter.formatRoutineLine("Thu", "05:00", baseNow + sixDaysSeventeenHours, baseNow)
        assertEquals("Thu 05:00 • in 6d 17h", formatted)

        val nineHoursFortyTwoMins = (9 * 60 + 42) * 60_000L
        val formattedToday = AlarmFormatter.formatRoutineLine("Fri", "05:00", baseNow + nineHoursFortyTwoMins, baseNow)
        assertEquals("Fri 05:00 • in 9h 42m", formattedToday)

        val pastTrigger = baseNow - 120_000L
        val formattedPast = AlarmFormatter.formatRoutineLine("Sat", "08:00", pastTrigger, baseNow)
        assertEquals("Sat 08:00", formattedPast)
    }
}
