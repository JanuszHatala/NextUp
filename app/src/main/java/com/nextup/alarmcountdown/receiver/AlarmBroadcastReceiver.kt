package com.nextup.alarmcountdown.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.AlarmLearningEngine
import com.nextup.alarmcountdown.widget.NextUpWidgetProvider

class AlarmBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.nextup.alarmcountdown.ACTION_REFRESH_WIDGET"
        const val ACTION_REFRESH_NOTIFICATION = "com.nextup.alarmcountdown.ACTION_REFRESH_NOTIFICATION"
        const val ACTION_TOGGLE_WIDGET_ALIGNMENT = "com.nextup.alarmcountdown.ACTION_TOGGLE_WIDGET_ALIGNMENT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_WIDGET_ALIGNMENT -> {
                val prefs = com.nextup.alarmcountdown.data.NextUpPreferences.getInstance(context)
                val current = prefs.widgetAlignment
                val nextAlignment = if (current == com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_CENTER) {
                    com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_LEFT
                } else {
                    com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_CENTER
                }
                prefs.widgetAlignment = nextAlignment
                NextUpWidgetProvider.updateAllWidgets(context)
            }
            ACTION_REFRESH_WIDGET,
            ACTION_REFRESH_NOTIFICATION,
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_TIME_TICK -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = AlarmRepository(context)
                        val nextAlarm = repo.getNextAlarm()
                        if (nextAlarm?.triggerTimeMillis != null) {
                            val engine = AlarmLearningEngine(context)
                            engine.onConfirmedAlarmObserved(nextAlarm.triggerTimeMillis)
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        NextUpWidgetProvider.updateAllWidgets(context)
                        com.nextup.alarmcountdown.notification.AlarmNotificationManager.updateNotification(context)
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
