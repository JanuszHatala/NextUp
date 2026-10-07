package com.nextup.alarmcountdown.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.nextup.alarmcountdown.data.model.AlarmEvent
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.RoutineStatistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class AlarmDatabaseManager(context: Context) {

    private val dbHelper = NextUpDatabaseHelper(context)

    /**
     * Records a new confirmed or triggered alarm event into SQLite.
     * Updates the recurring pattern counts and recalculates routine statistics.
     */
    suspend fun recordConfirmedAlarm(triggerMillis: Long): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            NextUpDatabaseHelper.TABLE_ALARM_EVENTS,
            arrayOf(NextUpDatabaseHelper.COL_EVENT_ID),
            "${NextUpDatabaseHelper.COL_EVENT_SCHEDULED_EPOCH} = ? AND ${NextUpDatabaseHelper.COL_EVENT_TYPE} = ?",
            arrayOf(triggerMillis.toString(), "CONFIRMED_SEEN"),
            null,
            null,
            null
        )
        val alreadyRecorded = cursor.use { it.moveToFirst() }
        if (alreadyRecorded) {
            return@withContext
        }

        val event = AlarmEvent.fromTriggerTime(triggerMillis, "CONFIRMED_SEEN")
        insertEvent(event)
        upsertPatternFromEvent(event, isConfirmed = true)
        recalculateRoutineStatistics(event)
    }

    /**
     * Records a missed or deactivated alarm occurrence.
     */
    suspend fun recordMissedAlarm(pattern: AlarmPattern): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val month = cal.get(Calendar.MONTH) + 1
        val season = when (month) {
            3, 4, 5 -> "SPRING"
            6, 7, 8 -> "SUMMER"
            9, 10, 11 -> "AUTUMN"
            else -> "WINTER"
        }
        val isWeekend = pattern.dayOfWeek == Calendar.SATURDAY || pattern.dayOfWeek == Calendar.SUNDAY

        val event = AlarmEvent(
            alarmHour = pattern.hour,
            alarmMinute = pattern.minute,
            scheduledEpochMillis = now,
            eventType = "MISSED_OR_DEACTIVATED",
            recordedEpochMillis = now,
            dayOfWeek = pattern.dayOfWeek,
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            month = month,
            year = cal.get(Calendar.YEAR),
            season = season,
            isWeekend = isWeekend
        )
        insertEvent(event)
        updatePatternMissed(pattern.patternId)
        recalculateRoutineStatistics(event)
    }

    private fun insertEvent(event: AlarmEvent) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(NextUpDatabaseHelper.COL_EVENT_HOUR, event.alarmHour)
            put(NextUpDatabaseHelper.COL_EVENT_MINUTE, event.alarmMinute)
            put(NextUpDatabaseHelper.COL_EVENT_SCHEDULED_EPOCH, event.scheduledEpochMillis)
            put(NextUpDatabaseHelper.COL_EVENT_TYPE, event.eventType)
            put(NextUpDatabaseHelper.COL_EVENT_RECORDED_EPOCH, event.recordedEpochMillis)
            put(NextUpDatabaseHelper.COL_EVENT_DAY_OF_WEEK, event.dayOfWeek)
            put(NextUpDatabaseHelper.COL_EVENT_DAY_OF_MONTH, event.dayOfMonth)
            put(NextUpDatabaseHelper.COL_EVENT_MONTH, event.month)
            put(NextUpDatabaseHelper.COL_EVENT_YEAR, event.year)
            put(NextUpDatabaseHelper.COL_EVENT_SEASON, event.season)
            put(NextUpDatabaseHelper.COL_EVENT_IS_WEEKEND, if (event.isWeekend) 1 else 0)
        }
        db.insert(NextUpDatabaseHelper.TABLE_ALARM_EVENTS, null, values)
    }

    private fun upsertPatternFromEvent(event: AlarmEvent, isConfirmed: Boolean) {
        val db = dbHelper.writableDatabase
        val patternId = AlarmPattern.buildPatternId(event.dayOfWeek, event.alarmHour, event.alarmMinute)

        val cursor = db.query(
            NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
            null,
            "${NextUpDatabaseHelper.COL_PATTERN_ID} = ?",
            arrayOf(patternId),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val existingConfirmed = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_CONFIRMED_COUNT))
                val existingMissed = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_MISSED_COUNT))
                val consecutive = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_CONSECUTIVE_WEEKS))

                val newConfirmed = if (isConfirmed) existingConfirmed + 1 else existingConfirmed
                val newConsecutive = if (isConfirmed) consecutive + 1 else consecutive
                val confidence = newConfirmed.toFloat() / (newConfirmed + existingMissed).coerceAtLeast(1)

                val updateValues = ContentValues().apply {
                    put(NextUpDatabaseHelper.COL_PATTERN_LAST_SEEN, System.currentTimeMillis())
                    put(NextUpDatabaseHelper.COL_PATTERN_CONFIRMED_COUNT, newConfirmed)
                    put(NextUpDatabaseHelper.COL_PATTERN_CONSECUTIVE_WEEKS, newConsecutive)
                    put(NextUpDatabaseHelper.COL_PATTERN_CONFIDENCE, confidence)
                    put(NextUpDatabaseHelper.COL_PATTERN_IS_ACTIVE, 1) // Reactivate when seen
                }
                db.update(
                    NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
                    updateValues,
                    "${NextUpDatabaseHelper.COL_PATTERN_ID} = ?",
                    arrayOf(patternId)
                )
            } else {
                val insertValues = ContentValues().apply {
                    put(NextUpDatabaseHelper.COL_PATTERN_ID, patternId)
                    put(NextUpDatabaseHelper.COL_PATTERN_HOUR, event.alarmHour)
                    put(NextUpDatabaseHelper.COL_PATTERN_MINUTE, event.alarmMinute)
                    put(NextUpDatabaseHelper.COL_PATTERN_DAY_OF_WEEK, event.dayOfWeek)
                    put(NextUpDatabaseHelper.COL_PATTERN_IS_ACTIVE, 1)
                    put(NextUpDatabaseHelper.COL_PATTERN_FIRST_SEEN, System.currentTimeMillis())
                    put(NextUpDatabaseHelper.COL_PATTERN_LAST_SEEN, System.currentTimeMillis())
                    put(NextUpDatabaseHelper.COL_PATTERN_CONFIRMED_COUNT, 1)
                    put(NextUpDatabaseHelper.COL_PATTERN_MISSED_COUNT, 0)
                    put(NextUpDatabaseHelper.COL_PATTERN_CONSECUTIVE_WEEKS, 1)
                    put(NextUpDatabaseHelper.COL_PATTERN_CONFIDENCE, 1.0f)
                }
                db.insert(NextUpDatabaseHelper.TABLE_ALARM_PATTERNS, null, insertValues)
            }
        }
    }

    private fun updatePatternMissed(patternId: String) {
        val db = dbHelper.writableDatabase
        val cursor = db.query(
            NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
            null,
            "${NextUpDatabaseHelper.COL_PATTERN_ID} = ?",
            arrayOf(patternId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val confirmed = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_CONFIRMED_COUNT))
                val missed = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_MISSED_COUNT)) + 1
                val confidence = confirmed.toFloat() / (confirmed + missed).coerceAtLeast(1)

                val values = ContentValues().apply {
                    put(NextUpDatabaseHelper.COL_PATTERN_MISSED_COUNT, missed)
                    put(NextUpDatabaseHelper.COL_PATTERN_CONSECUTIVE_WEEKS, 0)
                    put(NextUpDatabaseHelper.COL_PATTERN_CONFIDENCE, confidence)
                    put(NextUpDatabaseHelper.COL_PATTERN_IS_ACTIVE, 0) // Auto-deactivate
                }
                db.update(
                    NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
                    values,
                    "${NextUpDatabaseHelper.COL_PATTERN_ID} = ?",
                    arrayOf(patternId)
                )
            }
        }
    }

    /**
     * Recalculates pre-aggregated routine statistics across WEEKLY, MONTHLY, QUARTERLY, and SEASONAL periods.
     */
    private fun recalculateRoutineStatistics(event: AlarmEvent) {
        val db = dbHelper.writableDatabase
        val cal = Calendar.getInstance().apply { timeInMillis = event.scheduledEpochMillis }
        val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
        val quarter = (event.month - 1) / 3 + 1

        val periods = listOf(
            Pair("WEEKLY", "${event.year}-W$weekOfYear"),
            Pair("MONTHLY", String.format("%d-%02d", event.year, event.month)),
            Pair("QUARTERLY", "${event.year}-Q$quarter"),
            Pair("SEASONAL", "${event.year}-${event.season}"),
            Pair("ALL_TIME", "ALL")
        )

        for ((periodType, periodKey) in periods) {
            computeAndUpsertStatsForPeriod(db, periodType, periodKey, dayCategory = "ALL")
            computeAndUpsertStatsForPeriod(db, periodType, periodKey, dayCategory = if (event.isWeekend) "WEEKEND" else "WEEKDAY")
        }
    }

    private fun computeAndUpsertStatsForPeriod(
        db: android.database.sqlite.SQLiteDatabase,
        periodType: String,
        periodKey: String,
        dayCategory: String
    ) {
        val statId = "${periodType}_${periodKey}_$dayCategory"

        val whereClause = StringBuilder("1=1")
        val args = mutableListOf<String>()

        when (periodType) {
            "MONTHLY" -> {
                val parts = periodKey.split("-")
                whereClause.append(" AND year = ? AND month = ?")
                args.add(parts[0])
                args.add(parts[1].toInt().toString())
            }
            "SEASONAL" -> {
                val parts = periodKey.split("-")
                whereClause.append(" AND year = ? AND season = ?")
                args.add(parts[0])
                args.add(parts[1])
            }
            "ALL_TIME" -> {
                // all records
            }
        }

        if (dayCategory == "WEEKDAY") {
            whereClause.append(" AND is_weekend = 0")
        } else if (dayCategory == "WEEKEND") {
            whereClause.append(" AND is_weekend = 1")
        }

        val sql = """
            SELECT 
                COUNT(*) as total_count,
                AVG(alarm_hour * 60 + alarm_minute) as avg_wake,
                MIN(alarm_hour * 60 + alarm_minute) as min_wake,
                MAX(alarm_hour * 60 + alarm_minute) as max_wake,
                SUM(CASE WHEN event_type = 'CONFIRMED_SEEN' THEN 1 ELSE 0 END) as confirmed_cnt,
                SUM(CASE WHEN event_type = 'MISSED_OR_DEACTIVATED' THEN 1 ELSE 0 END) as missed_cnt
            FROM ${NextUpDatabaseHelper.TABLE_ALARM_EVENTS}
            WHERE $whereClause
        """.trimIndent()

        val cursor = db.rawQuery(sql, args.toTypedArray())
        cursor.use {
            if (it.moveToFirst()) {
                val total = it.getInt(it.getColumnIndexOrThrow("total_count"))
                if (total == 0) return

                val avgWake = it.getDouble(it.getColumnIndexOrThrow("avg_wake"))
                val minWake = it.getInt(it.getColumnIndexOrThrow("min_wake"))
                val maxWake = it.getInt(it.getColumnIndexOrThrow("max_wake"))
                val confirmed = it.getInt(it.getColumnIndexOrThrow("confirmed_cnt"))
                val missed = it.getInt(it.getColumnIndexOrThrow("missed_cnt"))
                val reliability = if (confirmed + missed > 0) confirmed.toDouble() / (confirmed + missed) else 1.0

                val values = ContentValues().apply {
                    put(NextUpDatabaseHelper.COL_STAT_ID, statId)
                    put(NextUpDatabaseHelper.COL_STAT_PERIOD_TYPE, periodType)
                    put(NextUpDatabaseHelper.COL_STAT_PERIOD_KEY, periodKey)
                    put(NextUpDatabaseHelper.COL_STAT_DAY_CATEGORY, dayCategory)
                    put(NextUpDatabaseHelper.COL_STAT_AVG_WAKE_MINUTES, avgWake)
                    put(NextUpDatabaseHelper.COL_STAT_EARLIEST_WAKE_MINUTES, minWake)
                    put(NextUpDatabaseHelper.COL_STAT_LATEST_WAKE_MINUTES, maxWake)
                    put(NextUpDatabaseHelper.COL_STAT_TOTAL_CONFIRMED, confirmed)
                    put(NextUpDatabaseHelper.COL_STAT_TOTAL_MISSED, missed)
                    put(NextUpDatabaseHelper.COL_STAT_RELIABILITY_RATE, reliability)
                    put(NextUpDatabaseHelper.COL_STAT_LAST_UPDATED, System.currentTimeMillis())
                }
                db.insertWithOnConflict(
                    NextUpDatabaseHelper.TABLE_ROUTINE_STATS,
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    /**
     * Retrieves all detected alarm patterns.
     */
    suspend fun getAllPatterns(): List<AlarmPattern> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AlarmPattern>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
            null,
            null,
            null,
            null,
            null,
            "${NextUpDatabaseHelper.COL_PATTERN_HOUR} ASC, ${NextUpDatabaseHelper.COL_PATTERN_MINUTE} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPattern(it))
            }
        }
        list
    }

    /**
     * Retrieves active alarm patterns sorted by the next upcoming occurrence.
     */
    suspend fun getActivePatternsSorted(): List<AlarmPattern> = withContext(Dispatchers.IO) {
        val allActive = getAllPatterns().filter { it.isActive }
        val now = System.currentTimeMillis()
        allActive.sortedBy { it.getNextOccurrenceMillis(now) }
    }

    suspend fun togglePatternActive(patternId: String, isActive: Boolean): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(NextUpDatabaseHelper.COL_PATTERN_IS_ACTIVE, if (isActive) 1 else 0)
        }
        db.update(
            NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
            values,
            "${NextUpDatabaseHelper.COL_PATTERN_ID} = ?",
            arrayOf(patternId)
        )
    }

    suspend fun deletePattern(patternId: String): Unit = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            NextUpDatabaseHelper.TABLE_ALARM_PATTERNS,
            "${NextUpDatabaseHelper.COL_PATTERN_ID} = ?",
            arrayOf(patternId)
        )
    }

    /**
     * Query pre-aggregated statistics for a specific period type and key.
     */
    suspend fun getRoutineStats(periodType: String, periodKey: String): List<RoutineStatistics> = withContext(Dispatchers.IO) {
        val list = mutableListOf<RoutineStatistics>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            NextUpDatabaseHelper.TABLE_ROUTINE_STATS,
            null,
            "${NextUpDatabaseHelper.COL_STAT_PERIOD_TYPE} = ? AND ${NextUpDatabaseHelper.COL_STAT_PERIOD_KEY} = ?",
            arrayOf(periodType, periodKey),
            null,
            null,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    RoutineStatistics(
                        statId = it.getString(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_ID)),
                        periodType = it.getString(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_PERIOD_TYPE)),
                        periodKey = it.getString(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_PERIOD_KEY)),
                        dayCategory = it.getString(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_DAY_CATEGORY)),
                        avgWakeTimeMinutes = it.getDouble(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_AVG_WAKE_MINUTES)),
                        earliestWakeMinutes = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_EARLIEST_WAKE_MINUTES)),
                        latestWakeMinutes = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_LATEST_WAKE_MINUTES)),
                        totalConfirmed = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_TOTAL_CONFIRMED)),
                        totalMissed = it.getInt(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_TOTAL_MISSED)),
                        reliabilityRate = it.getDouble(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_RELIABILITY_RATE)),
                        lastUpdatedMillis = it.getLong(it.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_STAT_LAST_UPDATED))
                    )
                )
            }
        }
        list
    }

    private fun cursorToPattern(cursor: Cursor): AlarmPattern {
        return AlarmPattern(
            patternId = cursor.getString(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_ID)),
            hour = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_HOUR)),
            minute = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_MINUTE)),
            dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_DAY_OF_WEEK)),
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_IS_ACTIVE)) == 1,
            firstSeenMillis = cursor.getLong(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_FIRST_SEEN)),
            lastSeenMillis = cursor.getLong(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_LAST_SEEN)),
            confirmedCount = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_CONFIRMED_COUNT)),
            missedCount = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_MISSED_COUNT)),
            consecutiveWeeks = cursor.getInt(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_CONSECUTIVE_WEEKS)),
            confidenceScore = cursor.getFloat(cursor.getColumnIndexOrThrow(NextUpDatabaseHelper.COL_PATTERN_CONFIDENCE))
        )
    }
}
