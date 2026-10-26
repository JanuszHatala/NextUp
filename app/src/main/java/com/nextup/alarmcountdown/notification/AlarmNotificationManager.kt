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
import android.os.PowerManager
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

    @Volatile
    private var lastPostedTriggerTime: Long? = Long.MIN_VALUE
    @Volatile
    private var lastPostedHasValidAlarm: Boolean = false
    @Volatile
    private var lastPostedTitle: String? = null
    @Volatile
    private var lastPostedContentText: String? = null
    @Volatile
    private var lastPostedIconBadgeText: String? = null
    @Volatile
    private var lastPostTimestampMillis: Long = 0L

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
        lastPostedTriggerTime = Long.MIN_VALUE
        lastPostedHasValidAlarm = false
        lastPostedTitle = null
        lastPostedContentText = null
        lastPostedIconBadgeText = null
        lastPostTimestampMillis = 0L
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun updateNotification(context: Context, force: Boolean = false) {
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

        val repository = AlarmRepository(appContext)
        val nextAlarm = repository.getNextAlarm()
        val triggerTime = nextAlarm?.triggerTimeMillis
        val now = System.currentTimeMillis()
        val hasValidAlarm = triggerTime != null && triggerTime > now

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true

        // Screen-off optimization: When screen is off and the alarm target did not change,
        // skip calling notify(). Nobody is looking at the screen, and SystemUI's chronometer
        // runs countdown rendering natively without app CPU intervention.
        if (!isInteractive && !force && triggerTime == lastPostedTriggerTime && hasValidAlarm == lastPostedHasValidAlarm) {
            return
        }

        val countdownText = AlarmFormatter.formatRemaining(triggerTime)
        val targetDateTimeText = AlarmFormatter.formatTargetDateTime(triggerTime)
        val iconBadgeText = StatusIconGenerator.getBadgeText(triggerTime, prefs.isStatusBarInfoEnabled)
        val contentText = if (hasValidAlarm) targetDateTimeText else "Tap to configure or set alarms"
        val title = if (hasValidAlarm) "Next Alarm in $countdownText" else "No active alarm"

        // Deduplication & rapid debounce: Skip if content is identical or within 500ms of last post for same alarm
        if (!force &&
            hasValidAlarm == lastPostedHasValidAlarm &&
            triggerTime == lastPostedTriggerTime &&
            iconBadgeText == lastPostedIconBadgeText &&
            contentText == lastPostedContentText &&
            (title == lastPostedTitle || (now - lastPostTimestampMillis < 500L))
        ) {
            return
        }

        createNotificationChannel(appContext)

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

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(statusIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (hasValidAlarm && triggerTime != null) {
            // Native chronometer countdown: Android SystemUI updates the live countdown
            // on the display/GPU without waking the app CPU or draining battery!
            builder.setWhen(triggerTime)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
                .setUsesChronometer(false)
        }

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
            lastPostedTriggerTime = triggerTime
            lastPostedHasValidAlarm = hasValidAlarm
            lastPostedTitle = title
            lastPostedContentText = contentText
            lastPostedIconBadgeText = iconBadgeText
            lastPostTimestampMillis = now
        } catch (ignored: SecurityException) {
        }
    }
}
