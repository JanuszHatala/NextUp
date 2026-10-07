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

    const val CHANNEL_ID = "nearest_alarm_channel_v2"
    const val NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun updateNotification(context: Context) {
        val prefs = NextUpPreferences.getInstance(context)
        if (!prefs.isNotificationEnabled) {
            cancelNotification(context)
            return
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        val repository = AlarmRepository(context)
        val nextAlarm = repository.getNextAlarm()
        val triggerTime = nextAlarm?.triggerTimeMillis
        val hasValidAlarm = triggerTime != null && triggerTime > System.currentTimeMillis()

        val countdownText = AlarmFormatter.formatRemaining(triggerTime)
        val targetDateTimeText = AlarmFormatter.formatTargetDateTime(triggerTime)

        // 1. Content Intent -> Open NextUp App
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            201,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: Dismiss Notification (Turns setting off and dismisses)
        val dismissNotifIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
        }
        val dismissNotifPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            dismissNotifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Status bar icon
        val statusIcon = StatusIconGenerator.createStatusIcon(
            context,
            triggerTime,
            prefs.isStatusBarInfoEnabled
        )

        val title = if (hasValidAlarm) {
            "Next Alarm: in $countdownText"
        } else {
            "No active alarm"
        }

        val contentText = if (hasValidAlarm) {
            targetDateTimeText
        } else {
            "Tap to configure or set alarms"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(statusIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("NextUp")
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        // 3. Action: Dismiss Alarm (Direct Activity intent to Google Clock HandleApiCalls)
        if (hasValidAlarm) {
            val clockDismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val dismissAlarmPendingIntent = PendingIntent.getActivity(
                context,
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

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (ignored: SecurityException) {
        }

        // Schedule next update tick aligned with minute boundary or trigger time
        scheduleNextUpdate(context, triggerTime)
    }

    private fun scheduleNextUpdate(context: Context, triggerTimeMillis: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        // Calculate next minute mark
        val nextMinute = (now / 60_000L + 1) * 60_000L

        // Next wake up time is either next minute mark or alarm trigger time, whichever is earlier
        val nextWakeTime = if (triggerTimeMillis != null && triggerTimeMillis > now && triggerTimeMillis < nextMinute) {
            triggerTimeMillis
        } else {
            nextMinute
        }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, nextWakeTime, pi)
            } else {
                am.set(AlarmManager.RTC, nextWakeTime, pi)
            }
        } catch (ignored: Exception) {
            try {
                am.set(AlarmManager.RTC, nextWakeTime, pi)
            } catch (ignored2: Exception) {}
        }
    }
}
