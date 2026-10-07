package com.nextup.alarmcountdown.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NextUpDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "nextup_alarms.db"
        const val DATABASE_VERSION = 1

        // Table 1: Raw alarm events
        const val TABLE_ALARM_EVENTS = "alarm_events"
        const val COL_EVENT_ID = "id"
        const val COL_EVENT_HOUR = "alarm_hour"
        const val COL_EVENT_MINUTE = "alarm_minute"
        const val COL_EVENT_SCHEDULED_EPOCH = "scheduled_epoch_millis"
        const val COL_EVENT_TYPE = "event_type"
        const val COL_EVENT_RECORDED_EPOCH = "recorded_epoch_millis"
        const val COL_EVENT_DAY_OF_WEEK = "day_of_week"
        const val COL_EVENT_DAY_OF_MONTH = "day_of_month"
        const val COL_EVENT_MONTH = "month"
        const val COL_EVENT_YEAR = "year"
        const val COL_EVENT_SEASON = "season"
        const val COL_EVENT_IS_WEEKEND = "is_weekend"

        // Table 2: Detected recurring patterns
        const val TABLE_ALARM_PATTERNS = "alarm_patterns"
        const val COL_PATTERN_ID = "pattern_id"
        const val COL_PATTERN_HOUR = "hour"
        const val COL_PATTERN_MINUTE = "minute"
        const val COL_PATTERN_DAY_OF_WEEK = "day_of_week"
        const val COL_PATTERN_IS_ACTIVE = "is_active"
        const val COL_PATTERN_FIRST_SEEN = "first_seen_millis"
        const val COL_PATTERN_LAST_SEEN = "last_seen_millis"
        const val COL_PATTERN_CONFIRMED_COUNT = "confirmed_count"
        const val COL_PATTERN_MISSED_COUNT = "missed_count"
        const val COL_PATTERN_CONSECUTIVE_WEEKS = "consecutive_weeks"
        const val COL_PATTERN_CONFIDENCE = "confidence_score"

        // Table 3: Generic routine statistics
        const val TABLE_ROUTINE_STATS = "routine_statistics"
        const val COL_STAT_ID = "stat_id"
        const val COL_STAT_PERIOD_TYPE = "period_type"
        const val COL_STAT_PERIOD_KEY = "period_key"
        const val COL_STAT_DAY_CATEGORY = "day_category"
        const val COL_STAT_AVG_WAKE_MINUTES = "avg_wake_time_minutes"
        const val COL_STAT_EARLIEST_WAKE_MINUTES = "earliest_wake_minutes"
        const val COL_STAT_LATEST_WAKE_MINUTES = "latest_wake_minutes"
        const val COL_STAT_TOTAL_CONFIRMED = "total_confirmed"
        const val COL_STAT_TOTAL_MISSED = "total_missed"
        const val COL_STAT_RELIABILITY_RATE = "reliability_rate"
        const val COL_STAT_LAST_UPDATED = "last_updated_millis"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create alarm_events
        db.execSQL(
            """
            CREATE TABLE $TABLE_ALARM_EVENTS (
                $COL_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_EVENT_HOUR INTEGER NOT NULL,
                $COL_EVENT_MINUTE INTEGER NOT NULL,
                $COL_EVENT_SCHEDULED_EPOCH INTEGER NOT NULL,
                $COL_EVENT_TYPE TEXT NOT NULL,
                $COL_EVENT_RECORDED_EPOCH INTEGER NOT NULL,
                $COL_EVENT_DAY_OF_WEEK INTEGER NOT NULL,
                $COL_EVENT_DAY_OF_MONTH INTEGER NOT NULL,
                $COL_EVENT_MONTH INTEGER NOT NULL,
                $COL_EVENT_YEAR INTEGER NOT NULL,
                $COL_EVENT_SEASON TEXT NOT NULL,
                $COL_EVENT_IS_WEEKEND INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_date ON $TABLE_ALARM_EVENTS($COL_EVENT_YEAR, $COL_EVENT_MONTH, $COL_EVENT_SEASON);")
        db.execSQL("CREATE INDEX idx_events_time ON $TABLE_ALARM_EVENTS($COL_EVENT_DAY_OF_WEEK, $COL_EVENT_HOUR, $COL_EVENT_MINUTE);")

        // Create alarm_patterns
        db.execSQL(
            """
            CREATE TABLE $TABLE_ALARM_PATTERNS (
                $COL_PATTERN_ID TEXT PRIMARY KEY,
                $COL_PATTERN_HOUR INTEGER NOT NULL,
                $COL_PATTERN_MINUTE INTEGER NOT NULL,
                $COL_PATTERN_DAY_OF_WEEK INTEGER NOT NULL,
                $COL_PATTERN_IS_ACTIVE INTEGER NOT NULL DEFAULT 1,
                $COL_PATTERN_FIRST_SEEN INTEGER NOT NULL,
                $COL_PATTERN_LAST_SEEN INTEGER NOT NULL,
                $COL_PATTERN_CONFIRMED_COUNT INTEGER NOT NULL DEFAULT 1,
                $COL_PATTERN_MISSED_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_PATTERN_CONSECUTIVE_WEEKS INTEGER NOT NULL DEFAULT 1,
                $COL_PATTERN_CONFIDENCE REAL NOT NULL DEFAULT 1.0
            );
            """.trimIndent()
        )

        // Create routine_statistics
        db.execSQL(
            """
            CREATE TABLE $TABLE_ROUTINE_STATS (
                $COL_STAT_ID TEXT PRIMARY KEY,
                $COL_STAT_PERIOD_TYPE TEXT NOT NULL,
                $COL_STAT_PERIOD_KEY TEXT NOT NULL,
                $COL_STAT_DAY_CATEGORY TEXT NOT NULL,
                $COL_STAT_AVG_WAKE_MINUTES REAL NOT NULL,
                $COL_STAT_EARLIEST_WAKE_MINUTES INTEGER NOT NULL,
                $COL_STAT_LATEST_WAKE_MINUTES INTEGER NOT NULL,
                $COL_STAT_TOTAL_CONFIRMED INTEGER NOT NULL DEFAULT 0,
                $COL_STAT_TOTAL_MISSED INTEGER NOT NULL DEFAULT 0,
                $COL_STAT_RELIABILITY_RATE REAL NOT NULL DEFAULT 1.0,
                $COL_STAT_LAST_UPDATED INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_stats_period ON $TABLE_ROUTINE_STATS($COL_STAT_PERIOD_TYPE, $COL_STAT_PERIOD_KEY);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future database migrations
    }
}
