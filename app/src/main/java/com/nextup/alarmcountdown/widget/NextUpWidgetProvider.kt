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

        private val PREDICTED_ROW_IDS = intArrayOf(
            R.id.widget_predicted_row_1,
            R.id.widget_predicted_row_2,
            R.id.widget_predicted_row_3,
            R.id.widget_predicted_row_4,
            R.id.widget_predicted_row_5,
            R.id.widget_predicted_row_6,
            R.id.widget_predicted_row_7,
            R.id.widget_predicted_row_8
        )

        private fun buildTallViews(
            context: Context,
            layoutId: Int,
            maxItems: Int,
            countdownText: String,
            fullTargetDateTimeText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent,
            alignIconRes: Int,
            activePatterns: List<com.nextup.alarmcountdown.data.model.AlarmPattern>,
            nextAlarm: com.nextup.alarmcountdown.data.AlarmModel?
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

                if (activePatterns.isEmpty()) {
                    for (rowId in PREDICTED_ROW_IDS) {
                        setViewVisibility(rowId, android.view.View.GONE)
                    }
                    setViewVisibility(R.id.widget_predicted_more, android.view.View.VISIBLE)
                    setTextViewText(R.id.widget_predicted_more, "Learning weekly routine...")
                    setOnClickPendingIntent(R.id.widget_predicted_more, clockPendingIntent)
                } else {
                    val showCount = if (activePatterns.size <= maxItems) {
                        activePatterns.size.coerceAtMost(PREDICTED_ROW_IDS.size)
                    } else {
                        (maxItems - 1).coerceAtMost(PREDICTED_ROW_IDS.size)
                    }

                    val now = System.currentTimeMillis()
                    val hasActiveAlarm = nextAlarm?.triggerTimeMillis != null && nextAlarm.triggerTimeMillis > now
                    val fromTime = if (hasActiveAlarm) nextAlarm.triggerTimeMillis else now

                    for (i in PREDICTED_ROW_IDS.indices) {
                        if (i < showCount) {
                            val pattern = activePatterns[i]
                            val occurrenceTime = pattern.getNextOccurrenceMillis(fromTime)
                            val text = AlarmFormatter.formatRoutineLine(
                                pattern.dayNameShort,
                                pattern.timeFormatted,
                                occurrenceTime,
                                now
                            )
                            setViewVisibility(PREDICTED_ROW_IDS[i], android.view.View.VISIBLE)
                            setTextViewText(PREDICTED_ROW_IDS[i], text)
                            setOnClickPendingIntent(PREDICTED_ROW_IDS[i], clockPendingIntent)
                        } else {
                            setViewVisibility(PREDICTED_ROW_IDS[i], android.view.View.GONE)
                        }
                    }

                    val remainingCount = activePatterns.size - showCount
                    if (remainingCount > 0) {
                        setViewVisibility(R.id.widget_predicted_more, android.view.View.VISIBLE)
                        setTextViewText(R.id.widget_predicted_more, "+$remainingCount more in NextUp")
                        setOnClickPendingIntent(R.id.widget_predicted_more, appPendingIntent)
                    } else {
                        setViewVisibility(R.id.widget_predicted_more, android.view.View.GONE)
                    }
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

            // 5. Tall / Expanded: Dynamically generated with 2, 3, 4, 6, or 8 items based on widget height
            val dbManager = com.nextup.alarmcountdown.data.db.AlarmDatabaseManager(context)
            val activePatterns = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val allActive = dbManager.getAllPatterns().filter { it.isActive }
                val now = System.currentTimeMillis()
                val hasActiveAlarm = nextAlarm?.triggerTimeMillis != null && nextAlarm.triggerTimeMillis > now
                val fromTime = if (hasActiveAlarm) nextAlarm.triggerTimeMillis else now

                // Filter out the pattern that matches current active alarm
                val filtered = if (hasActiveAlarm) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTimeMillis }
                    val cPatternId = com.nextup.alarmcountdown.data.model.AlarmPattern.buildPatternId(
                        cal.get(java.util.Calendar.DAY_OF_WEEK),
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE)
                    )
                    allActive.filter { it.patternId != cPatternId }
                } else {
                    allActive
                }

                // Sort chronologically starting from the current active alarm (or now if no active alarm)
                filtered.sortedBy { it.getNextOccurrenceMillis(fromTime) }
            }

            // 5. Tall / Expanded: Dynamically generated with 2..8 items based on widget height
            val tallViews2 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 2,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )
            val tallViews3 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 3,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )
            val tallViews4 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 4,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )
            val tallViews5 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 5,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )
            val tallViews6 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 6,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )
            val tallViews7 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 7,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )
            val tallViews8 = buildTallViews(
                context = context,
                layoutId = tallLayout,
                maxItems = 8,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                activePatterns = activePatterns,
                nextAlarm = nextAlarm
            )

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Responsive layout: system automatically selects best layout as widget is resized
                RemoteViews(
                    mapOf(
                        SizeF(40f, 40f) to tinyViews,
                        SizeF(100f, 40f) to compactWideViews,
                        SizeF(180f, 40f) to mediumViews,
                        SizeF(100f, 100f) to largeViews,
                        SizeF(100f, 150f) to tallViews2,
                        SizeF(100f, 170f) to tallViews3,
                        SizeF(100f, 190f) to tallViews4,
                        SizeF(100f, 210f) to tallViews5,
                        SizeF(100f, 230f) to tallViews6,
                        SizeF(100f, 250f) to tallViews7,
                        SizeF(100f, 265f) to tallViews8
                    )
                )
            } else {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                when {
                    minHeight >= 265 -> tallViews8
                    minHeight >= 250 -> tallViews7
                    minHeight >= 230 -> tallViews6
                    minHeight >= 210 -> tallViews5
                    minHeight >= 190 -> tallViews4
                    minHeight >= 170 -> tallViews3
                    minHeight >= 150 -> tallViews2
                    minHeight >= 100 -> largeViews
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
