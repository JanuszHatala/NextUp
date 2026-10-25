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
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
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
        com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(appContext)
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
                val appContext = context.applicationContext
                for (widgetId in appWidgetIds) {
                    updateWidget(appContext, appWidgetManager, widgetId)
                }
            }
            com.nextup.alarmcountdown.scheduler.AlarmRefreshScheduler.scheduleNextRefresh(context)
        }

        private fun buildTinyViews(
            context: Context,
            countdownText: String,
            appPendingIntent: PendingIntent
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_next_up_tiny).apply {
                setTextViewText(R.id.widget_countdown, countdownText)
                setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
                setOnClickPendingIntent(R.id.widget_countdown, appPendingIntent)
                setOnClickPendingIntent(R.id.widget_icon, appPendingIntent)
            }
        }

        private fun buildCompactViews(
            context: Context,
            countdownText: String,
            shortTargetDateTimeText: String,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent,
            alignIconRes: Int,
            isCentered: Boolean
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_next_up_compact_wide).apply {
                setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
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
                    setOnClickPendingIntent(R.id.widget_countdown_center, appPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_center, appPendingIntent)
                } else {
                    setViewVisibility(R.id.widget_alarm_section_center, View.GONE)
                    setViewVisibility(R.id.widget_alarm_section_left, View.VISIBLE)
                    setTextViewText(R.id.widget_countdown_left, countdownText)
                    setTextViewText(R.id.widget_alarm_time_left, shortTargetDateTimeText)
                    setOnClickPendingIntent(R.id.widget_countdown_left, appPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_left, appPendingIntent)
                }
            }
        }

        private fun buildTallViews(
            context: Context,
            appWidgetId: Int,
            countdownText: String,
            fullTargetDateTimeText: String,
            appPendingIntent: PendingIntent,
            alignTogglePendingIntent: PendingIntent,
            alignIconRes: Int,
            isCentered: Boolean,
            groupedRoutineAlarms: List<Pair<GroupedAlarmPattern, Long>>
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_next_up_tall).apply {
                setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
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
                    setOnClickPendingIntent(R.id.widget_countdown_center, appPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_center, appPendingIntent)

                    setViewVisibility(R.id.widget_section_predicted_left, View.GONE)
                    setViewVisibility(R.id.widget_section_predicted_center, View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_section_predicted_center, appPendingIntent)

                    setEmptyView(R.id.widget_alarms_list, R.id.widget_predicted_empty_center)
                } else {
                    setViewVisibility(R.id.widget_alarm_section_center, View.GONE)
                    setViewVisibility(R.id.widget_alarm_section_left, View.VISIBLE)
                    setTextViewText(R.id.widget_countdown_left, countdownText)
                    setTextViewText(R.id.widget_alarm_time_left, fullTargetDateTimeText)
                    setOnClickPendingIntent(R.id.widget_countdown_left, appPendingIntent)
                    setOnClickPendingIntent(R.id.widget_alarm_time_left, appPendingIntent)

                    setViewVisibility(R.id.widget_section_predicted_center, View.GONE)
                    setViewVisibility(R.id.widget_section_predicted_left, View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_section_predicted_left, appPendingIntent)

                    setEmptyView(R.id.widget_alarms_list, R.id.widget_predicted_empty_left)
                }

                // Populate grouped routine list items
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val builder = RemoteViews.RemoteCollectionItems.Builder()
                    val now = System.currentTimeMillis()
                    for ((index, item) in groupedRoutineAlarms.withIndex()) {
                        val (group, occurrenceTime) = item
                        val timeText = AlarmFormatter.formatRoutineTimeAndCountdown(
                            timeFormatted = group.timeFormatted,
                            occurrenceMillis = occurrenceTime,
                            nowMillis = now
                        )
                        val daysText = group.daysLabel
                        NextUpLog.i("WidgetTall", "Row $index: time='$timeText', days='$daysText'")
                        val rowView = RemoteViews(context.packageName, R.layout.widget_item_routine).apply {
                            if (isCentered) {
                                setViewVisibility(R.id.widget_item_routine_container_left, View.GONE)
                                setViewVisibility(R.id.widget_item_routine_container_center, View.VISIBLE)
                                setTextViewText(R.id.widget_item_routine_time_center, timeText)
                                setTextViewText(R.id.widget_item_routine_days_center, daysText)
                            } else {
                                setViewVisibility(R.id.widget_item_routine_container_center, View.GONE)
                                setViewVisibility(R.id.widget_item_routine_container_left, View.VISIBLE)
                                setTextViewText(R.id.widget_item_routine_time_left, timeText)
                                setTextViewText(R.id.widget_item_routine_days_left, daysText)
                            }
                            setOnClickFillInIntent(R.id.widget_item_root, Intent())
                        }
                        builder.addItem((group.hour * 60 + group.minute).toLong(), rowView)
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
                setPendingIntentTemplate(R.id.widget_alarms_list, appPendingIntent)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repository = AlarmRepository(context)
            val nextAlarm = repository.getNextAlarm()

            val triggerTime = nextAlarm?.triggerTimeMillis

            val countdownText = AlarmFormatter.formatRemaining(triggerTime)
            val fullTargetDateTimeText = AlarmFormatter.formatTargetDateTime(triggerTime)
            val shortTargetDateTimeText = AlarmFormatter.formatShortTargetDateTime(triggerTime)

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
                appPendingIntent
            )

            // 2. Compact (2x1, 3x1, 4x1): icon + countdown + concise day/time + alignment toggle
            val compactViews = buildCompactViews(
                context,
                countdownText,
                shortTargetDateTimeText,
                appPendingIntent,
                alignTogglePendingIntent,
                alignIconRes,
                isCentered
            )

            val dbManager = AlarmDatabaseManager(context)
            val allPatterns = dbManager.getAllPatternsDirect().filter { it.isActive }
            val now = System.currentTimeMillis()

            val grouped = GroupedAlarmPattern.fromPatterns(allPatterns)
            val sortedGrouped = grouped.mapNotNull { group ->
                val nextOccur = group.getNextOccurrenceMillis(now, triggerTime)
                if (nextOccur != null) {
                    Pair(group, nextOccur)
                } else {
                    null
                }
            }.sortedBy { it.second }

            // 3. Tall / Expanded (2x2 and taller): full header, countdown, subheader + scrollable collection
            val tallViews = buildTallViews(
                context = context,
                appWidgetId = appWidgetId,
                countdownText = countdownText,
                fullTargetDateTimeText = fullTargetDateTimeText,
                appPendingIntent = appPendingIntent,
                alignTogglePendingIntent = alignTogglePendingIntent,
                alignIconRes = alignIconRes,
                isCentered = isCentered,
                groupedRoutineAlarms = sortedGrouped
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
        }
    }
}
