package com.nextup.alarmcountdown.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nextup.alarmcountdown.MainActivity
import com.nextup.alarmcountdown.R
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.NextUpPreferences
import com.nextup.alarmcountdown.receiver.AlarmBroadcastReceiver
import com.nextup.alarmcountdown.receiver.NotificationActionReceiver
import com.nextup.alarmcountdown.util.AlarmFormatter

object AlarmNotificationManager {

    const val CHANNEL_ID = "nearest_alarm_channel_v3"
    const val NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                notificationManager.deleteNotificationChannel("nearest_alarm_channel")
                notificationManager.deleteNotificationChannel("nearest_alarm_channel_v2")
            } catch (ignored: Exception) {}

            val name = "Nearest Alarm"
            val descriptionText = "Displays ongoing countdown and info for the nearest active alarm"
            // IMPORTANCE_DEFAULT ensures visibility in the status bar while setSound(null, null) prevents noise
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        cancelScheduledUpdate(context)
    }

    private fun cancelScheduledUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_REFRESH_NOTIFICATION
        }
        val pi = PendingIntent.getBroadcast(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.cancel(pi)
        } catch (ignored: Exception) {}
    }

    fun updateNotification(context: Context) {
        val appContext = context.applicationContext
        val prefs = NextUpPreferences.getInstance(appContext)
        prefs.syncFromDisk()
        if (!prefs.isNotificationEnabled) {
            cancelNotification(appContext)
            return
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(appContext)

        val repository = AlarmRepository(appContext)
        val nextAlarm = repository.getNextAlarm()
        val triggerTime = nextAlarm?.triggerTimeMillis
        val hasValidAlarm = triggerTime != null && triggerTime > System.currentTimeMillis()

        val countdownText = AlarmFormatter.formatRemaining(triggerTime)
        val targetDateTimeText = AlarmFormatter.formatTargetDateTime(triggerTime)

        // 1. Content Intent -> Open NextUp App
        val appIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            appContext,
            201,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: Dismiss Notification (Turns setting off and dismisses)
        val dismissNotifIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
        }
        val dismissNotifPendingIntent = PendingIntent.getBroadcast(
            appContext,
            202,
            dismissNotifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Status bar icon
        val statusIcon = StatusIconGenerator.createStatusIcon(
            appContext,
            triggerTime,
            prefs.isStatusBarInfoEnabled
        )

        val title = if (hasValidAlarm) {
            "Next Alarm in $countdownText"
        } else {
            "No active alarm"
        }

        val contentText = if (hasValidAlarm) {
            targetDateTimeText
        } else {
            "Tap to configure or set alarms"
        }

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(statusIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        // 3. Action: Dismiss Alarm (Direct Activity intent to Google Clock HandleApiCalls)
        if (hasValidAlarm && triggerTime != null) {
            val clockDismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                // Google Clock's internal parser expects "android.next"
                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.next")
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = triggerTime }
                putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
                putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val dismissAlarmPendingIntent = PendingIntent.getActivity(
                appContext,
                203,
                clockDismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_alarm,
                "Dismiss Alarm",
                dismissAlarmPendingIntent
            )
        }

        // Action: Dismiss Notification
        builder.addAction(
            0,
            "Dismiss Notification",
            dismissNotifPendingIntent
        )

        com.nextup.alarmcountdown.util.NextUpLog.i(
            "NotificationManager",
            "updateNotification: hasValidAlarm=$hasValidAlarm, triggerTime=$triggerTime, title='$title', contentText='$contentText'"
        )

        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        } catch (ignored: SecurityException) {
        }

        // Smart background scheduling: hourly when > 1h, per-minute when <= 1h, ring transition when passed
        scheduleSmartNotificationUpdate(appContext, triggerTime)
    }

    /**
     * Schedules exact AlarmManager wakeups matching icon and title transition boundaries.
     * Hourly transitions when > 1h away, per-minute transitions when <= 1h away.
     */
    private fun scheduleSmartNotificationUpdate(context: Context, triggerTimeMillis: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = AlarmBroadcastReceiver.ACTION_REFRESH_NOTIFICATION
        }
        val pi = PendingIntent.getBroadcast(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        if (triggerTimeMillis != null && triggerTimeMillis > now) {
            val remainingMillis = triggerTimeMillis - now
            val targetTime = when {
                remainingMillis > 3600_000L -> {
                    val remainder = remainingMillis % 3600_000L
                    now + (if (remainder == 0L) 3600_000L else remainder) + 1000L
                }
                remainingMillis > 60_000L -> {
                    val remainder = remainingMillis % 60_000L
                    now + (if (remainder == 0L) 60_000L else remainder) + 1000L
                }
                else -> triggerTimeMillis + 500L
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, targetTime, pi)
                } else {
                    am.set(AlarmManager.RTC, targetTime, pi)
                }
            } catch (e: Exception) {
                try {
                    am.set(AlarmManager.RTC, targetTime, pi)
                } catch (ignored: Exception) {}
            }
        } else {
            try {
                am.cancel(pi)
            } catch (ignored: Exception) {}
        }
    }
}
