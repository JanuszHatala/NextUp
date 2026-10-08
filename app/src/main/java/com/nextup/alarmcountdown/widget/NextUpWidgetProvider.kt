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
import android.view.View
import android.widget.RemoteViews
import com.nextup.alarmcountdown.MainActivity
import com.nextup.alarmcountdown.R
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.NextUpPreferences
import com.nextup.alarmcountdown.receiver.AlarmBroadcastReceiver
import com.nextup.alarmcountdown.util.AlarmFormatter
import com.nextup.alarmcountdown.util.NextUpLog
import java.util.Calendar
import kotlinx.coroutines.runBlocking

class NextUpWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        for (appWidgetId in appWidgetIds) {
            updateWidget(appContext, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context.applicationContext, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return
            val componentName = ComponentName(appContext, NextUpWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            NextUpLog.i(
                "WidgetProvider",
                "updateAllWidgets: found ${appWidgetIds?.size ?: 0} widgets: ${appWidgetIds?.contentToString()}"
            )
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                for (widgetId in appWidgetIds) {
                    updateWidget(appContext, appWidgetManager, widgetId)
                }
            }
        }

        private fun buildTinyViews(
            context: Context,
            countdownText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_next_up_tiny).apply {
                setTextViewText(R.id.widget_countdown, countdownText)
                setOnClickPendingIntent(R.id.widget_root, clockPendingIntent)
                setOnClickPendingIntent(R.id.widget_countdown, clockPendingIntent)
                setOnClickPendingIntent(R.id.widget_icon, appPendingIntent)
            }
        }

        private fun buildCompactViews(
            context: Context,
            countdownText: String,
            shortTargetDateTimeText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent,
            alignIconRes: Int,
            isCentered: Boolean
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_next_up_compact_wide).apply {
                setOnClickPendingIntent(R.id.widget_root, clockPendingIntent)
                setOnClickPendingIntent(R.id.widget_header, appPendingIntent)
                setOnClickPendingIntent(R.id.widget_title, appPendingIntent)
                setOnClickPendingIntent(R.id.widget_icon, appPendingIntent)

                setImageViewResource(R.id.widget_btn_align_toggle, alignIconRes)
                setOnClickPendingIntent(R.id.widget_btn_align_toggle, alignTogglePendingIntent)

                if (isCentered) {
                    setViewVisibility(R.id.widget_alarm_section_left, View.GONE)
                    setViewVisibility(R.id.widget_alarm_section_center, View.VISIBLE)
                    setTextViewText(R.id.widget_countdown_center, countdownText)
                    setTextViewText(R.id.widget_alarm_time_center, shortTargetDateTimeText)
                    setOnClickPendingIntent(R.id.widget_countdown_center, clockPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_center, clockPendingIntent)
                } else {
                    setViewVisibility(R.id.widget_alarm_section_center, View.GONE)
                    setViewVisibility(R.id.widget_alarm_section_left, View.VISIBLE)
                    setTextViewText(R.id.widget_countdown_left, countdownText)
                    setTextViewText(R.id.widget_alarm_time_left, shortTargetDateTimeText)
                    setOnClickPendingIntent(R.id.widget_countdown_left, clockPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_left, clockPendingIntent)
                }
            }
        }

        private fun buildTallViews(
            context: Context,
            appWidgetId: Int,
            countdownText: String,
            fullTargetDateTimeText: String,
            clockPendingIntent: PendingIntent,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent,
            alignIconRes: Int,
            isCentered: Boolean,
            routinePatterns: List<AlarmPattern>,
            fromTimeMillis: Long
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_next_up_tall).apply {
                setOnClickPendingIntent(R.id.widget_root, clockPendingIntent)
                setOnClickPendingIntent(R.id.widget_header, appPendingIntent)
                setOnClickPendingIntent(R.id.widget_title, appPendingIntent)
                setOnClickPendingIntent(R.id.widget_icon, appPendingIntent)

                setImageViewResource(R.id.widget_btn_align_toggle, alignIconRes)
                setOnClickPendingIntent(R.id.widget_btn_align_toggle, alignTogglePendingIntent)

                if (isCentered) {
                    setViewVisibility(R.id.widget_alarm_section_left, View.GONE)
                    setViewVisibility(R.id.widget_alarm_section_center, View.VISIBLE)
                    setTextViewText(R.id.widget_countdown_center, countdownText)
                    setTextViewText(R.id.widget_alarm_time_center, fullTargetDateTimeText)
                    setOnClickPendingIntent(R.id.widget_countdown_center, clockPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_center, clockPendingIntent)

                    setViewVisibility(R.id.widget_section_predicted_left, View.GONE)
                    setViewVisibility(R.id.widget_section_predicted_center, View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_section_predicted_center, appPendingIntent)

                    setEmptyView(R.id.widget_alarms_list, R.id.widget_predicted_empty_center)
                } else {
                    setViewVisibility(R.id.widget_alarm_section_center, View.GONE)
                    setViewVisibility(R.id.widget_alarm_section_left, View.VISIBLE)
                    setTextViewText(R.id.widget_countdown_left, countdownText)
                    setTextViewText(R.id.widget_alarm_time_left, fullTargetDateTimeText)
                    setOnClickPendingIntent(R.id.widget_countdown_left, clockPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_left, clockPendingIntent)

                    setViewVisibility(R.id.widget_section_predicted_center, View.GONE)
                    setViewVisibility(R.id.widget_section_predicted_left, View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_section_predicted_left, appPendingIntent)

                    setEmptyView(R.id.widget_alarms_list, R.id.widget_predicted_empty_left)
                }

                // Populate routine list items
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val builder = RemoteViews.RemoteCollectionItems.Builder()
                    val now = System.currentTimeMillis()
                    for (pattern in routinePatterns) {
                        val occurrenceTime = pattern.getNextOccurrenceMillis(fromTimeMillis)
                        val text = AlarmFormatter.formatRoutineLine(
                            pattern.dayNameShort,
                            pattern.timeFormatted,
                            occurrenceTime,
                            now
                        )
                        val rowView = RemoteViews(context.packageName, R.layout.widget_item_routine).apply {
                            if (isCentered) {
                                setViewVisibility(R.id.widget_item_routine_text_left, View.GONE)
                                setViewVisibility(R.id.widget_item_routine_text_center, View.VISIBLE)
                                setTextViewText(R.id.widget_item_routine_text_center, text)
                                setOnClickFillInIntent(R.id.widget_item_root, Intent())
                                setOnClickFillInIntent(R.id.widget_item_routine_text_center, Intent())
                            } else {
                                setViewVisibility(R.id.widget_item_routine_text_center, View.GONE)
                                setViewVisibility(R.id.widget_item_routine_text_left, View.VISIBLE)
                                setTextViewText(R.id.widget_item_routine_text_left, text)
                                setOnClickFillInIntent(R.id.widget_item_root, Intent())
                                setOnClickFillInIntent(R.id.widget_item_routine_text_left, Intent())
                            }
                        }
                        builder.addItem(pattern.patternId.hashCode().toLong(), rowView)
                    }
                    builder.setHasStableIds(true)
                    builder.setViewTypeCount(1)
                    setRemoteAdapter(R.id.widget_alarms_list, builder.build())
                } else {
                    val serviceIntent = Intent(context, NextUpWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = Uri.parse("content://com.nextup.alarmcountdown.widget/$appWidgetId")
                    }
                    setRemoteAdapter(R.id.widget_alarms_list, serviceIntent)
                }
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
                val launchIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage("com.google.android.deskclock")
                }
                if (context.packageManager.resolveActivity(launchIntent, 0) == null) {
                    launchIntent.setPackage(null)
                }
                PendingIntent.getActivity(
                    context,
                    2001,
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

            val prefs = NextUpPreferences.getInstance(context)
            prefs.syncFromDisk()
            val isCentered = prefs.widgetAlignment == NextUpPreferences.ALIGNMENT_CENTER

            val toggleAlignIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = AlarmBroadcastReceiver.ACTION_TOGGLE_WIDGET_ALIGNMENT
                data = Uri.parse("nextup://widget/align_toggle/$appWidgetId")
            }
            val alignTogglePendingIntent = PendingIntent.getBroadcast(
                context,
                10000 + appWidgetId,
                toggleAlignIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alignIconRes = if (isCentered) R.drawable.ic_format_align_center else R.drawable.ic_format_align_left

            // 1. Tiny (1x1): icon + countdown only
            val tinyViews = buildTinyViews(
                context,
                countdownText,
                clockPendingIntent,
                appPendingIntent
            )

            // 2. Compact (2x1, 3x1, 4x1): icon + countdown + concise day/time + alignment toggle
            val compactViews = buildCompactViews(
                context,
                countdownText,
                shortTargetDateTimeText,
                clockPendingIntent,
                appPendingIntent,
                alignTogglePendingIntent,
                alignIconRes,
                isCentered
            )

            val dbManager = AlarmDatabaseManager(context)
            val allPatterns = runBlocking {
                dbManager.getAllPatterns().filter { it.isActive }
            }
            val now = System.currentTimeMillis()
            val hasActiveAlarm = nextAlarm?.triggerTimeMillis != null && nextAlarm.triggerTimeMillis > now
            val fromTimeMillis = if (hasActiveAlarm) nextAlarm.triggerTimeMillis else now
            val filteredPatterns = if (hasActiveAlarm) {
                val cal = Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTimeMillis }
                val cPatternId = AlarmPattern.buildPatternId(
                    cal.get(Calendar.DAY_OF_WEEK),
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE)
                )
                allPatterns.filter { it.patternId != cPatternId }
            } else {
                allPatterns
            }
            val sortedPatterns = filteredPatterns.sortedBy { it.getNextOccurrenceMillis(fromTimeMillis) }

            // 3. Tall / Expanded (2x2 and taller): full header, countdown, subheader + scrollable collection
            val tallViews = buildTallViews(
                context = context,
                appWidgetId = appWidgetId,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                clockPendingIntent = clockPendingIntent,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                isCentered = isCentered,
                routinePatterns = sortedPatterns,
                fromTimeMillis = fromTimeMillis
            )

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val isPortrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val height = if (isPortrait) {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            } else {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            }.let { if (it > 0) it else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) }

            val width = if (isPortrait) {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            } else {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            }.let { if (it > 0) it else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) }

            val views = when {
                height >= 150 -> tallViews
                width >= 100 -> compactViews
                else -> tinyViews
            }

            val layoutName = when {
                views == tallViews -> "tall"
                views == compactViews -> "compact"
                else -> "tiny"
            }

            NextUpLog.i(
                "WidgetProvider",
                "updateWidget(id=$appWidgetId): layout=$layoutName, width=$width, height=$height, isCentered=$isCentered, countdown='$countdownText'"
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && views == tallViews) {
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_alarms_list)
            }
            NextUpLog.d("WidgetProvider", "updateWidget(id=$appWidgetId): updateAppWidget dispatched ($layoutName)")

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
