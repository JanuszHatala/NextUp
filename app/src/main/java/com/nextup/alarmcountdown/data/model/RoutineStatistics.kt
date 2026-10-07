package com.nextup.alarmcountdown.data.model

data class RoutineStatistics(
    val statId: String, // e.g. "WEEKLY_2026-W41_WEEKDAY", "SEASONAL_2026-AUTUMN_ALL"
    val periodType: String, // WEEKLY, MONTHLY, QUARTERLY, SEASONAL, ALL_TIME
    val periodKey: String, // e.g. "2026-W41", "2026-10", "2026-Q4", "2026-AUTUMN", "ALL"
    val dayCategory: String, // WEEKDAY, WEEKEND, ALL
    val avgWakeTimeMinutes: Double, // e.g. 420.5 = 07:00.5
    val earliestWakeMinutes: Int, // e.g. 360 = 06:00
    val latestWakeMinutes: Int, // e.g. 540 = 09:00
    val totalConfirmed: Int,
    val totalMissed: Int,
    val reliabilityRate: Double, // totalConfirmed / (totalConfirmed + totalMissed)
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val avgWakeFormatted: String
        get() {
            val totalMins = avgWakeTimeMinutes.toInt()
            val hours = totalMins / 60
            val minutes = totalMins % 60
            return String.format("%02d:%02d", hours, minutes)
        }

    val earliestWakeFormatted: String
        get() = String.format("%02d:%02d", earliestWakeMinutes / 60, earliestWakeMinutes % 60)

    val latestWakeFormatted: String
        get() = String.format("%02d:%02d", latestWakeMinutes / 60, latestWakeMinutes % 60)
}
