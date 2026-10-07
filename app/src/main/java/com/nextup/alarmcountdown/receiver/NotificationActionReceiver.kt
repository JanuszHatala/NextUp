package com.nextup.alarmcountdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.NextUpPreferences
import com.nextup.alarmcountdown.notification.AlarmNotificationManager
import com.nextup.alarmcountdown.widget.NextUpWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS_ALARM = "com.nextup.alarmcountdown.ACTION_DISMISS_ALARM"
        const val ACTION_DISMISS_NOTIFICATION = "com.nextup.alarmcountdown.ACTION_DISMISS_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS_NOTIFICATION -> {
                val prefs = NextUpPreferences.getInstance(context)
                prefs.isNotificationEnabled = false
                AlarmNotificationManager.cancelNotification(context)
            }

            ACTION_DISMISS_ALARM -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = AlarmRepository(context)
                        val currentAlarm = repository.getNextAlarm()

                        // Send dismiss intent to Google Clock
                        val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.next")
                            if (currentAlarm?.triggerTimeMillis != null) {
                                val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentAlarm.triggerTimeMillis }
                                putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
                                putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
                            }
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }

                        try {
                            context.startActivity(dismissIntent)
                        } catch (e: Exception) {
                            // If direct skip_ui dismiss intent isn't handled without UI, try standard dismiss
                            val fallbackIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.next")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(fallbackIntent)
                            } catch (ignored: Exception) {
                            }
                        }

                        // Poll briefly to detect the new nearest active alarm once Google Clock updates
                        // Wait a short duration (e.g. 500ms, then 1200ms) for Clock app to update AlarmManager
                        for (i in 1..4) {
                            delay(400)
                            val newAlarm = repository.getNextAlarm()
                            if (newAlarm?.triggerTimeMillis != currentAlarm?.triggerTimeMillis) {
                                break
                            }
                        }
                    } catch (ignored: Exception) {
                    } finally {
                        AlarmNotificationManager.updateNotification(context)
                        NextUpWidgetProvider.updateAllWidgets(context)
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
