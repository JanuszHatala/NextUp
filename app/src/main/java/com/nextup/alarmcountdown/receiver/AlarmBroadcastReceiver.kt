package com.nextup.alarmcountdown.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

        @Volatile
        private var isSettlingInProgress = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        com.nextup.alarmcountdown.util.NextUpLog.i(
            "Receiver",
            "onReceive: action=$action, data=${intent.data}"
        )

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true

        when (action) {
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
                com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(context)
            }

            ACTION_REFRESH_WIDGET -> {
                if (!isInteractive) {
                    // Screen is OFF: Skip UI re-renders and notification posts to prevent waking NotificationManagerService.
                    // Only re-anchor the non-wakeup scheduler.
                    com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)
                    return
                }
                // Screen is ON: Refresh widgets and notifications, then schedule next minute.
                try {
                    com.nextup.alarmcountdown.notification.AlarmNotificationManager.updateNotification(appContext)
                } catch (e: Exception) {
                    com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Widget refresh notification update failed", e)
                }
                try {
                    NextUpWidgetProvider.updateAllWidgets(appContext)
                } catch (e: Exception) {
                    com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Widget refresh widget update failed", e)
                }
                com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)
            }

            Intent.ACTION_SCREEN_OFF -> {
                // Screen is turning off: Do not post notifications or redraw widgets.
                com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)
            }

            ACTION_REFRESH_NOTIFICATION,
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON -> {
                val isClockChange = action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ||
                        action == Intent.ACTION_BOOT_COMPLETED ||
                        action == Intent.ACTION_MY_PACKAGE_REPLACED

                val repo = AlarmRepository(appContext)
                val initialAlarm = repo.getNextAlarm()
                val initialTriggerTime = initialAlarm?.triggerTimeMillis

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

                // 2. Debounced background settling and learning updates (only for clock changes)
                if (isClockChange && !isSettlingInProgress) {
                    isSettlingInProgress = true
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Debounce settling delay: Google Clock operations take up to 100-150ms to settle state
                            delay(150)

                            val settledAlarm = repo.getNextAlarm()
                            val settledTriggerTime = settledAlarm?.triggerTimeMillis
                            if (settledTriggerTime != null) {
                                val engine = AlarmLearningEngine(appContext)
                                engine.onConfirmedAlarmObserved(settledTriggerTime)
                            }

                            // Only re-post if the settled alarm time actually differed from initial observation
                            if (settledTriggerTime != initialTriggerTime) {
                                try {
                                    com.nextup.alarmcountdown.notification.AlarmNotificationManager.updateNotification(appContext)
                                } catch (e: Exception) {
                                    com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Settled notification update failed", e)
                                }
                                try {
                                    NextUpWidgetProvider.updateAllWidgets(appContext)
                                } catch (e: Exception) {
                                    com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Settled widget update failed", e)
                                }
                                com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)
                            }
                        } catch (e: Exception) {
                            com.nextup.alarmcountdown.util.NextUpLog.e("Receiver", "Learning update error", e)
                        } finally {
                            isSettlingInProgress = false
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
