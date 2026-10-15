package com.nextup.alarmcountdown.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.nextup.alarmcountdown.R
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.NextUpPreferences
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.util.AlarmFormatter
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class NextUpWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NextUpWidgetFactory(applicationContext, intent)
    }
}

class NextUpWidgetFactory(
    private val context: Context,
    private val intent: Intent? = null
) : RemoteViewsService.RemoteViewsFactory {

    private val dbManager = AlarmDatabaseManager(context)
    private val repository = AlarmRepository(context)
    private val items = mutableListOf<Pair<com.nextup.alarmcountdown.data.model.GroupedAlarmPattern, Long>>()
    private var isCentered: Boolean = false

    override fun onCreate() {
        val prefs = NextUpPreferences.getInstance(context)
        prefs.syncFromDisk()
        isCentered = prefs.widgetAlignment == NextUpPreferences.ALIGNMENT_CENTER
        com.nextup.alarmcountdown.util.NextUpLog.i("WidgetFactory", "onCreate: isCentered=$isCentered")
    }

    override fun onDataSetChanged() {
        val prefs = NextUpPreferences.getInstance(context)
        prefs.syncFromDisk()
        isCentered = prefs.widgetAlignment == NextUpPreferences.ALIGNMENT_CENTER

        val now = System.currentTimeMillis()

        val allPatterns = dbManager.getAllPatternsDirect().filter { it.isActive }

        val grouped = com.nextup.alarmcountdown.data.model.GroupedAlarmPattern.fromPatterns(allPatterns)
        val nextAlarm = repository.getNextAlarm()
        val confirmedNextMillis = nextAlarm?.triggerTimeMillis

        val sortedGrouped = grouped.mapNotNull { group ->
            val nextOccur = group.getNextOccurrenceMillis(now, confirmedNextMillis)
            if (nextOccur != null) {
                Pair(group, nextOccur)
            } else {
                null
            }
        }.sortedBy { it.second }

        items.clear()
        items.addAll(sortedGrouped)
        com.nextup.alarmcountdown.util.NextUpLog.i("WidgetFactory", "onDataSetChanged: isCentered=$isCentered, count=${items.size}")
    }

    override fun onDestroy() {
        items.clear()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= items.size) return null

        val (group, occurrenceTime) = items[position]
        val timeText = AlarmFormatter.formatRoutineTimeAndCountdown(
            timeFormatted = group.timeFormatted,
            occurrenceMillis = occurrenceTime,
            nowMillis = System.currentTimeMillis()
        )
        val daysText = group.daysLabel

        val currentCentered = NextUpPreferences.getInstance(context).widgetAlignment == NextUpPreferences.ALIGNMENT_CENTER

        com.nextup.alarmcountdown.util.NextUpLog.d(
            "WidgetFactory",
            "getViewAt(pos=$position): currentCentered=$currentCentered, time='$timeText', days='$daysText'"
        )

        return RemoteViews(context.packageName, R.layout.widget_item_routine).apply {
            if (currentCentered) {
                setViewVisibility(R.id.widget_item_routine_container_left, android.view.View.GONE)
                setViewVisibility(R.id.widget_item_routine_container_center, android.view.View.VISIBLE)
                setTextViewText(R.id.widget_item_routine_time_center, timeText)
                setTextViewText(R.id.widget_item_routine_days_center, daysText)
            } else {
                setViewVisibility(R.id.widget_item_routine_container_center, android.view.View.GONE)
                setViewVisibility(R.id.widget_item_routine_container_left, android.view.View.VISIBLE)
                setTextViewText(R.id.widget_item_routine_time_left, timeText)
                setTextViewText(R.id.widget_item_routine_days_left, daysText)
            }
            setOnClickFillInIntent(R.id.widget_item_root, Intent())
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
