package com.nextup.alarmcountdown.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
            }
        }

        private fun buildViews(
            context: Context,
            layoutId: Int,
            countdownText: String,
            dateTimeText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent
        ): RemoteViews {
            return RemoteViews(context.packageName, layoutId).apply {
                setTextViewText(R.id.widget_countdown, countdownText)
                try {
                    setTextViewText(R.id.widget_alarm_time, dateTimeText)
                } catch (ignored: Exception) {
                }
                // Tap main widget area -> Open NextUp App
                setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
                // Tap alarm icon -> Open Google Clock
                try {
                    setOnClickPendingIntent(R.id.widget_icon, clockPendingIntent)
                } catch (ignored: Exception) {
                }
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

            // Main clock tap -> Google Clock (or specific alarm)
            val clockPendingIntent = nextAlarm?.showIntent ?: run {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                PendingIntent.getActivity(
                    context,
                    0,
                    clockIntent,
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

            // 1. Tiny (1x1 or smallest shrinked): icon + countdown only
            val tinyViews = buildViews(
                context,
                R.layout.widget_next_up_tiny,
                countdownText,
                "",
                clockPendingIntent,
                appPendingIntent
            )

            // 2. Compact wide (2x1): icon + countdown + concise shortcut day/time
            val compactWideViews = buildViews(
                context,
                R.layout.widget_next_up_compact_wide,
                countdownText,
                shortTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent
            )

            // 3. Medium wide (3x1, 4x1): icon + countdown + concise day/time
            val mediumViews = buildViews(
                context,
                R.layout.widget_next_up_medium,
                countdownText,
                shortTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent
            )

            // 4. Large (2x2, 3x2, etc.): full header, large countdown, full day/time
            val largeViews = buildViews(
                context,
                R.layout.widget_next_up,
                countdownText,
                fullTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent
            )

            // 5. Tall / Expanded (vertically expanded: 2x3, 3x3, 4x2+): confirmed + upcoming predicted alarms
            val tallViews = RemoteViews(context.packageName, R.layout.widget_next_up_tall).apply {
                setTextViewText(R.id.widget_countdown, countdownText)
                setTextViewText(R.id.widget_alarm_time, fullTargetDateTimeText)
                
                // Top section / card tap -> NextUp App
                try {
                    setOnClickPendingIntent(R.id.widget_top_section, appPendingIntent)
                } catch (ignored: Exception) {}
                try {
                    setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
                } catch (ignored: Exception) {}
                // Icon tap -> Google Clock
                try {
                    setOnClickPendingIntent(R.id.widget_icon, clockPendingIntent)
                } catch (ignored: Exception) {}

                val dbManager = com.nextup.alarmcountdown.data.db.AlarmDatabaseManager(context)
                val activePatterns = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    val sorted = dbManager.getActivePatternsSorted()
                    if (nextAlarm?.triggerTimeMillis != null && nextAlarm.triggerTimeMillis > 0L) {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTimeMillis }
                        val cPatternId = com.nextup.alarmcountdown.data.model.AlarmPattern.buildPatternId(
                            cal.get(java.util.Calendar.DAY_OF_WEEK),
                            cal.get(java.util.Calendar.HOUR_OF_DAY),
                            cal.get(java.util.Calendar.MINUTE)
                        )
                        sorted.filter { it.patternId != cPatternId }
                    } else {
                        sorted
                    }
                }

                if (activePatterns.isNotEmpty()) {
                    val p1 = activePatterns[0]
                    val p1Next = p1.getNextOccurrenceMillis()
                    val p1Countdown = com.nextup.alarmcountdown.util.AlarmFormatter.formatRemaining(p1Next)
                    setViewVisibility(R.id.widget_predicted_row_1, android.view.View.VISIBLE)
                    setTextViewText(R.id.widget_predicted_1_text, "${p1.dayNameShort} ${p1.timeFormatted} • in $p1Countdown")

                    val p1Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, p1.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, p1.minute)
                        putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(p1.dayOfWeek))
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val p1Pending = PendingIntent.getActivity(
                        context,
                        101,
                        p1Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setOnClickPendingIntent(R.id.widget_predicted_row_1, p1Pending)

                    if (activePatterns.size > 1) {
                        val p2 = activePatterns[1]
                        val p2Next = p2.getNextOccurrenceMillis()
                        val p2Countdown = com.nextup.alarmcountdown.util.AlarmFormatter.formatRemaining(p2Next)
                        setViewVisibility(R.id.widget_predicted_row_2, android.view.View.VISIBLE)
                        setTextViewText(R.id.widget_predicted_2_text, "${p2.dayNameShort} ${p2.timeFormatted} • in $p2Countdown")

                        val p2Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, p2.hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, p2.minute)
                            putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(p2.dayOfWeek))
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val p2Pending = PendingIntent.getActivity(
                            context,
                            102,
                            p2Intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setOnClickPendingIntent(R.id.widget_predicted_row_2, p2Pending)
                    } else {
                        setViewVisibility(R.id.widget_predicted_row_2, android.view.View.GONE)
                    }

                    if (activePatterns.size > 2) {
                        setViewVisibility(R.id.widget_predicted_more, android.view.View.VISIBLE)
                        setTextViewText(R.id.widget_predicted_more, "+${activePatterns.size - 2} more in NextUp")
                        setOnClickPendingIntent(R.id.widget_predicted_more, appPendingIntent)
                    } else {
                        setViewVisibility(R.id.widget_predicted_more, android.view.View.GONE)
                    }
                } else {
                    setViewVisibility(R.id.widget_predicted_row_1, android.view.View.GONE)
                    setViewVisibility(R.id.widget_predicted_row_2, android.view.View.GONE)
                    setViewVisibility(R.id.widget_predicted_more, android.view.View.VISIBLE)
                    setTextViewText(R.id.widget_predicted_more, "Learning weekly routine...")
                    setOnClickPendingIntent(R.id.widget_predicted_more, appPendingIntent)
                }
            }

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Responsive layout: system automatically selects best layout as widget is resized
                RemoteViews(
                    mapOf(
                        SizeF(40f, 40f) to tinyViews,
                        SizeF(100f, 40f) to compactWideViews,
                        SizeF(180f, 40f) to mediumViews,
                        SizeF(120f, 75f) to largeViews,
                        SizeF(120f, 110f) to tallViews
                    )
                )
            } else {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                when {
                    minHeight >= 110 -> tallViews
                    minHeight >= 75 -> largeViews
                    minWidth >= 180 -> mediumViews
                    minWidth >= 100 -> compactWideViews
                    else -> tinyViews
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)

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
