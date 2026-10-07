package com.nextup.alarmcountdown.data.model

import java.util.Calendar

data class AlarmEvent(
    val id: Long = 0,
    val alarmHour: Int,
    val alarmMinute: Int,
    val scheduledEpochMillis: Long,
    val eventType: String, // CONFIRMED_SEEN, TRIGGERED, MISSED_OR_DEACTIVATED, MANUALLY_SET
    val recordedEpochMillis: Long = System.currentTimeMillis(),
    val dayOfWeek: Int, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val dayOfMonth: Int,
    val month: Int, // 1..12
    val year: Int,
    val season: String, // SPRING, SUMMER, AUTUMN, WINTER
    val isWeekend: Boolean
) {
    companion object {
        fun fromTriggerTime(triggerMillis: Long, eventType: String): AlarmEvent {
            val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH) + 1 // 1-indexed
            val year = cal.get(Calendar.YEAR)

            val season = when (month) {
                3, 4, 5 -> "SPRING"
                6, 7, 8 -> "SUMMER"
                9, 10, 11 -> "AUTUMN"
                else -> "WINTER"
            }
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

            return AlarmEvent(
                alarmHour = hour,
                alarmMinute = minute,
                scheduledEpochMillis = triggerMillis,
                eventType = eventType,
                recordedEpochMillis = System.currentTimeMillis(),
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth,
                month = month,
                year = year,
                season = season,
                isWeekend = isWeekend
            )
        }
    }
}
