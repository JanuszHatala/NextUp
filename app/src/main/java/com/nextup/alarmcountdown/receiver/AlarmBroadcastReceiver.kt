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
        const val EXTRA_TARGET_ALIGNMENT = "com.nextup.alarmcountdown.extra.TARGET_ALIGNMENT"
        private const val TOGGLE_DEBOUNCE_MILLIS = 250L

        @Volatile
        private var lastToggleTimeMillis = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        com.nextup.alarmcountdown.util.NextUpLog.i(
            "Receiver",
            "onReceive: action=${intent.action}, data=${intent.data}"
        )
        when (intent.action) {
            ACTION_TOGGLE_WIDGET_ALIGNMENT -> {
                val now = System.currentTimeMillis()
                if (now - lastToggleTimeMillis < TOGGLE_DEBOUNCE_MILLIS) {
                    com.nextup.alarmcountdown.util.NextUpLog.w("Receiver", "Debouncing toggle within ${now - lastToggleTimeMillis}ms")
                    return
                }
                lastToggleTimeMillis = now

                val prefs = com.nextup.alarmcountdown.data.NextUpPreferences.getInstance(context)
                prefs.syncFromDisk()
                val current = prefs.widgetAlignment
                val nextAlignment = if (current == com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_CENTER) {
                    com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_LEFT
                } else {
                    com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_CENTER
                }
                com.nextup.alarmcountdown.util.NextUpLog.i("Receiver", "Toggling widgetAlignment: $current -> $nextAlignment")
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
            Intent.ACTION_SCREEN_OFF -> {
                val action = intent.action
                val appContext = context.applicationContext
                val shouldUpdateLearning = action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ||
                        action == Intent.ACTION_BOOT_COMPLETED ||
                        action == Intent.ACTION_MY_PACKAGE_REPLACED

                // 1. Immediate sync notification and widget update (< 5ms)
                try {
                    com.nextup.alarmcountdown.notification.AlarmNotificationManager.updateNotification(appContext)
                } catch (e: Exception) {
                    com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Immediate notification update failed", e)
                }
                try {
                    NextUpWidgetProvider.updateAllWidgets(appContext)
                } catch (e: Exception) {
                    com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Immediate widget update failed", e)
                }
                com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)

                // 2. Background async work for settling and learning updates (only for clock changes)
                if (shouldUpdateLearning) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Debounce settling delay: Google Clock operations (cancel old -> schedule new)
                            // take up to 100-150ms to settle state in AlarmManagerService
                            kotlinx.coroutines.delay(150)

                            val repo = AlarmRepository(appContext)
                            val nextAlarm = repo.getNextAlarm()
                            if (nextAlarm?.triggerTimeMillis != null) {
                                val engine = AlarmLearningEngine(appContext)
                                engine.onConfirmedAlarmObserved(nextAlarm.triggerTimeMillis)
                            }
                        } catch (e: Exception) {
                            com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Learning update error", e)
                        } finally {
                            try {
                                com.nextup.alarmcountdown.notification.AlarmNotificationManager.updateNotification(appContext)
                            } catch (e: Exception) {
                                com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Notification update error in finally", e)
                            }
                            try {
                                NextUpWidgetProvider.updateAllWidgets(appContext)
                            } catch (e: Exception) {
                                com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Widget update error in finally", e)
                            }
                            com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
