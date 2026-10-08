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
    private val patterns = mutableListOf<AlarmPattern>()
    private var fromTimeMillis = System.currentTimeMillis()
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

        val nextAlarm = repository.getNextAlarm()
        val now = System.currentTimeMillis()
        val hasActiveAlarm = nextAlarm?.triggerTimeMillis != null && nextAlarm.triggerTimeMillis > now
        fromTimeMillis = if (hasActiveAlarm) nextAlarm.triggerTimeMillis else now

        val allPatterns = runBlocking {
            dbManager.getAllPatterns().filter { it.isActive }
        }

        // Filter out pattern matching the current active alarm
        val filtered = if (hasActiveAlarm) {
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

        patterns.clear()
        // Sort chronologically starting from the active alarm trigger time (or now)
        patterns.addAll(filtered.sortedBy { it.getNextOccurrenceMillis(fromTimeMillis) })
        com.nextup.alarmcountdown.util.NextUpLog.i("WidgetFactory", "onDataSetChanged: isCentered=$isCentered, patternsCount=${patterns.size}")
    }

    override fun onDestroy() {
        patterns.clear()
    }

    override fun getCount(): Int = patterns.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= patterns.size) return null

        val pattern = patterns[position]
        val occurrenceTime = pattern.getNextOccurrenceMillis(fromTimeMillis)
        val text = AlarmFormatter.formatRoutineLine(
            pattern.dayNameShort,
            pattern.timeFormatted,
            occurrenceTime,
            System.currentTimeMillis()
        )

        val currentCentered = NextUpPreferences.getInstance(context).widgetAlignment == NextUpPreferences.ALIGNMENT_CENTER

        com.nextup.alarmcountdown.util.NextUpLog.d(
            "WidgetFactory",
            "getViewAt(pos=$position): currentCentered=$currentCentered, text='$text'"
        )

        return RemoteViews(context.packageName, R.layout.widget_item_routine).apply {
            if (currentCentered) {
                setViewVisibility(R.id.widget_item_routine_text_left, android.view.View.GONE)
                setViewVisibility(R.id.widget_item_routine_text_center, android.view.View.VISIBLE)
                setTextViewText(R.id.widget_item_routine_text_center, text)
                setOnClickFillInIntent(R.id.widget_item_routine_text_center, Intent())
            } else {
                setViewVisibility(R.id.widget_item_routine_text_center, android.view.View.GONE)
                setViewVisibility(R.id.widget_item_routine_text_left, android.view.View.VISIBLE)
                setTextViewText(R.id.widget_item_routine_text_left, text)
                setOnClickFillInIntent(R.id.widget_item_routine_text_left, Intent())
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
