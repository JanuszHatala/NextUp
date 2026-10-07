package com.nextup.alarmcountdown.data

import android.app.PendingIntent

data class AlarmModel(
    val triggerTimeMillis: Long,
    val showIntent: PendingIntent? = null
)
