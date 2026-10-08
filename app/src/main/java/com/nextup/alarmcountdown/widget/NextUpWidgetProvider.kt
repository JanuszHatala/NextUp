package com.nextup.alarmcountdown.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.util.SizeF
import android.widget.RemoteViews
import com.nextup.alarmcountdown.MainActivity
import com.nextup.alarmcountdown.R
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.receiver.AlarmBroadcastReceiver
import com.nextup.alarmcountdown.util.AlarmFormatter

class NextUpWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_alarms_list)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, NextUpWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                for (widgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, widgetId)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_alarms_list)
            }
        }

        private fun buildViews(
            context: Context,
            layoutId: Int,
            countdownText: String,
            dateTimeText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent? = null,
            alignIconRes: Int = 0
        ): RemoteViews {
            return RemoteViews(context.packageName, layoutId).apply {
                setTextViewText(R.id.widget_countdown, countdownText)
                try {
                    setTextViewText(R.id.widget_alarm_time, dateTimeText)
                } catch (ignored: Exception) {
                }
                // Tap root card / time -> Google Clock
                setOnClickPendingIntent(R.id.widget_root, clockPendingIntent)
                setOnClickPendingIntent(R.id.widget_countdown, clockPendingIntent)
                try {
                    setOnClickPendingIntent(R.id.widget_alarm_time, clockPendingIntent)
                } catch (ignored: Exception) {
                }

                // Tap top bar, header, or title -> NextUp App
                try {
                    setOnClickPendingIntent(R.id.widget_top_bar, appPendingIntent)
                } catch (ignored: Exception) {
                }
                try {
                    setOnClickPendingIntent(R.id.widget_header, appPendingIntent)
                } catch (ignored: Exception) {
                }
                try {
                    setOnClickPendingIntent(R.id.widget_title, appPendingIntent)
                } catch (ignored: Exception) {
                }
                // In medium/compact layouts (where there is no header text), icon taps open NextUp App
                try {
                    setOnClickPendingIntent(R.id.widget_icon, appPendingIntent)
                } catch (ignored: Exception) {
                }

                // Alignment toggle button
                if (alignTogglePendingIntent != null && alignIconRes != 0) {
                    try {
                        setImageViewResource(R.id.widget_btn_align_toggle, alignIconRes)
                        setOnClickPendingIntent(R.id.widget_btn_align_toggle, alignTogglePendingIntent)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }

        private fun buildTallViews(
            context: Context,
            layoutId: Int,
            appWidgetId: Int,
            countdownText: String,
            fullTargetDateTimeText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent,
            alignIconRes: Int
        ): RemoteViews {
            return RemoteViews(context.packageName, layoutId).apply {
                setTextViewText(R.id.widget_countdown, countdownText)
                setTextViewText(R.id.widget_alarm_time, fullTargetDateTimeText)

                // Root card tap default -> Google Clock
                setOnClickPendingIntent(R.id.widget_root, clockPendingIntent)

                // Top header / title tap -> NextUp App
                try {
                    setOnClickPendingIntent(R.id.widget_top_bar, appPendingIntent)
                } catch (ignored: Exception) {}
                try {
                    setOnClickPendingIntent(R.id.widget_header, appPendingIntent)
                } catch (ignored: Exception) {}
                try {
                    setOnClickPendingIntent(R.id.widget_title, appPendingIntent)
                } catch (ignored: Exception) {}

                // Alignment toggle button
                try {
                    setImageViewResource(R.id.widget_btn_align_toggle, alignIconRes)
                    setOnClickPendingIntent(R.id.widget_btn_align_toggle, alignTogglePendingIntent)
                } catch (ignored: Exception) {}

                // Alarm countdown and target time tap -> Google Clock
                try {
                    setOnClickPendingIntent(R.id.widget_countdown, clockPendingIntent)
                } catch (ignored: Exception) {}
                try {
                    setOnClickPendingIntent(R.id.widget_alarm_time, clockPendingIntent)
                } catch (ignored: Exception) {}
                try {
                    setOnClickPendingIntent(R.id.widget_icon, appPendingIntent)
                } catch (ignored: Exception) {}

                // Routine subheader tap -> NextUp App
                try {
                    setOnClickPendingIntent(R.id.widget_section_predicted, appPendingIntent)
                } catch (ignored: Exception) {}

                // Bind RemoteViewsService collection adapter for smooth touch-scrolling
                val serviceIntent = Intent(context, NextUpWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                setRemoteAdapter(R.id.widget_alarms_list, serviceIntent)
                setEmptyView(R.id.widget_alarms_list, R.id.widget_predicted_empty)
                setPendingIntentTemplate(R.id.widget_alarms_list, clockPendingIntent)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repository = AlarmRepository(context)
            val nextAlarm = repository.getNextAlarm()

            val countdownText = AlarmFormatter.formatRemaining(nextAlarm?.triggerTimeMillis)
            val fullTargetDateTimeText = AlarmFormatter.formatTargetDateTime(nextAlarm?.triggerTimeMillis)
            val shortTargetDateTimeText = AlarmFormatter.formatShortTargetDateTime(nextAlarm?.triggerTimeMillis)

            // Main clock tap -> Google Clock
            val clockPendingIntent = run {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                    ?: Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            // App card tap -> NextUp App
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                1,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prefs = com.nextup.alarmcountdown.data.NextUpPreferences.getInstance(context)
            val isCentered = prefs.widgetAlignment == com.nextup.alarmcountdown.data.NextUpPreferences.ALIGNMENT_CENTER

            val compactWideLayout = if (isCentered) R.layout.widget_next_up_compact_wide_center else R.layout.widget_next_up_compact_wide
            val mediumLayout = if (isCentered) R.layout.widget_next_up_medium_center else R.layout.widget_next_up_medium
            val largeLayout = if (isCentered) R.layout.widget_next_up_center else R.layout.widget_next_up
            val tallLayout = if (isCentered) R.layout.widget_next_up_tall_center else R.layout.widget_next_up_tall

            val toggleAlignIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = AlarmBroadcastReceiver.ACTION_TOGGLE_WIDGET_ALIGNMENT
            }
            val alignTogglePendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                toggleAlignIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alignIconRes = if (isCentered) R.drawable.ic_format_align_center else R.drawable.ic_format_align_left

            // 1. Tiny (1x1 or smallest shrinked): icon + countdown only
            val tinyViews = buildViews(
                context,
                R.layout.widget_next_up_tiny,
                countdownText,
                "",
                clockPendingIntent,
                appPendingIntent
            )

            // 2. Compact wide (2x1): icon + countdown + concise shortcut day/time + alignment toggle
            val compactWideViews = buildViews(
                context,
                compactWideLayout,
                countdownText,
                shortTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent,
                alignTogglePendingIntent,
                alignIconRes
            )

            // 3. Medium wide (3x1, 4x1): icon + countdown + concise day/time + alignment toggle
            val mediumViews = buildViews(
                context,
                mediumLayout,
                countdownText,
                shortTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent,
                alignTogglePendingIntent,
                alignIconRes
            )

            // 4. Large (2x2, 3x2, etc.): full header, large countdown, full day/time + alignment toggle
            val largeViews = buildViews(
                context,
                largeLayout,
                countdownText,
                fullTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent,
                alignTogglePendingIntent,
                alignIconRes
            )

            // 5. Tall / Expanded: Dynamically driven by ListView collection widget (scrollable, no scrollbars)
            val tallViews = buildTallViews(
                context = context,
                layoutId = tallLayout,
                appWidgetId = appWidgetId,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes
            )

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Responsive layout: maps standard form-factor archetypes.
                // tallViews with its scrollable ListView automatically handles ANY height >= 150dp!
                RemoteViews(
                    mapOf(
                        SizeF(40f, 40f) to tinyViews,
                        SizeF(100f, 40f) to compactWideViews,
                        SizeF(180f, 40f) to mediumViews,
                        SizeF(100f, 100f) to largeViews,
                        SizeF(100f, 150f) to tallViews
                    )
                )
            } else {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                when {
                    minHeight >= 150 -> tallViews
                    minHeight >= 100 -> largeViews
                    minWidth >= 180 -> mediumViews
                    minWidth >= 100 -> compactWideViews
                    else -> tinyViews
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_alarms_list)

            // Auto-refresh exactly when the alarm is due to ring
            val triggerTime = nextAlarm?.triggerTimeMillis
            if (triggerTime != null && triggerTime > System.currentTimeMillis()) {
                val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                    action = AlarmBroadcastReceiver.ACTION_REFRESH_WIDGET
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                try {
                    am?.set(AlarmManager.RTC, triggerTime, pi)
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
