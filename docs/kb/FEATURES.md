# Implemented Features

## 1. Nearest Alarm Countdown & Formatting
- **Live Hero Countdown:** Shows remaining time to the next scheduled system alarm (`AlarmManager.getNextAlarmClock()`), target date/time, and `CONFIRMED ACTIVE` / `NO ACTIVE ALARM` status badge in [MainActivity.kt](../../app/src/main/java/com/nextup/alarmcountdown/MainActivity.kt).
- **Smart Duration Format:** Implemented in [AlarmFormatter.kt](../../app/src/main/java/com/nextup/alarmcountdown/util/AlarmFormatter.kt): omits `0d`, omits `0h` when days and hours are 0, displays `< 1m` under 60 seconds, and uses concise `Xd Yh` spacing in widgets.
- **Quick Clock Actions:** `Open Clock` prioritizes Google Clock (`com.google.android.deskclock`), falling back to `AlarmClock.ACTION_SHOW_ALARMS` and alarm `showIntent`; `Set Alarm` opens `AlarmClock.ACTION_SET_ALARM`.

## 2. Home Screen Widget
- **Responsive Sizing:** [NextUpWidgetProvider.kt](../../app/src/main/java/com/nextup/alarmcountdown/widget/NextUpWidgetProvider.kt) maps `SizeF` layouts (`tiny` 1x1, `compact_wide` 2x1, `standard`, and `tall` 2x2+) with auto-scaling text (`autoSizeTextType="uniform"`).
- **Scrollable Routine List:** [NextUpWidgetService.kt](../../app/src/main/java/com/nextup/alarmcountdown/widget/NextUpWidgetService.kt) backs a `<ListView android:scrollbars="none">` showing upcoming active `GroupedAlarmPattern` rows sorted chronologically from the nearest upcoming alarm.
- **Alignment Toggle & Persistence:** Supports Left vs. Centered alignment via widget header icon or Settings, stored synchronously via `commit()` in [NextUpPreferences.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/NextUpPreferences.kt), debounced at >= 400ms, and surfaced cleanly via dynamic `Intent.data` URIs so Android purges stale `RemoteViews` caches.

## 3. Passive Weekly Learning & Active Schedule (`Schedule` Tab)
- **Event-Driven Observation:** [AlarmLearningEngine.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/AlarmLearningEngine.kt) records confirmed alarms when `ACTION_NEXT_ALARM_CLOCK_CHANGED` fires or when the user foregrounds the app, and lazily auto-deactivates patterns unseen for >= 8 days (`recordMissedAlarm`).
- **Grouped Patterns:** [GroupedAlarmPattern.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/model/GroupedAlarmPattern.kt) merges `alarm_patterns` by `(hour, minute)` with human-friendly day labels (`Every day`, `Weekdays`, `Weekend`, or ordered `Mon, Wed, Fri`), eliminating duplicate entries if a previously inactive time is scheduled again.
- **Decluttered Active Schedule:** The `Schedule` tab displays only active recurring routines (`it.isActive`), per-day M-T-W-T-F-S-S toggle chips, a whole-group Switch, `Set` / `Active ✓` Clock integration, and a compact footer link (`"N inactive / one-off alarms -> View in History"`) when archived items exist.
- **7-Day Learning Period Banner:** Tracks calendar day progress from the earliest recorded alarm or start timestamp and offers `Lock Current Reference` / `Restart Learning Period` / `Reset All Alarms & Data` controls.

## 4. History Archive & Chronological Event Log (`History` Tab)
- **Inactive Archive Sub-Tab:** [HistoryScreen.kt](../../app/src/main/java/com/nextup/alarmcountdown/ui/screens/HistoryScreen.kt) displays deactivated and one-off patterns sorted by `lastSeenMillis DESC`, with metadata (`One-off · Last Nov 12` vs `Previously Mon-Fri · Seen 6x · Last Nov 12`), **Set in Clock**, **Restore to Schedule** (reactivates stored days without duplication), **Delete**, and a bulk **Clean up one-offs** action (deletes 1x inactive rows from `alarm_patterns` while leaving `alarm_events` intact for stats).
- **Event Log Sub-Tab:** Filterable (`All` | `Confirmed` | `Missed`) chronological timeline grouped by date (`Today`, `Yesterday`, `EEE, MMM d`) queried lazily from SQLite `alarm_events`.

## 5. Alarm Statistics & Charts (`Stats` Tab)
- **Timeframes & KPIs:** [StatsScreen.kt](../../app/src/main/java/com/nextup/alarmcountdown/ui/screens/StatsScreen.kt) and [AlarmStatsCalculator.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/AlarmStatsCalculator.kt) provide `30 Days` and `All Time` views with Confirmed count, Routine Reliability %, Most Frequent Alarm Time, and Average Earliest Daily Alarm (isolating each day's first confirmed alarm with Weekday/Weekend breakdown so evening alarms do not skew morning averages).
- **Native Charts:** Custom Compose `Canvas` **Alarms by Day of Week (Mon-Sun)** vertical bar chart and **Time of Day Breakdown** (`Morning`, `Afternoon`, `Evening`, `Night`) distribution bars.

## 6. Persistent Notification & Status Bar Countdown
- **Zero-Drain Countdown:** [AlarmNotificationManager.kt](../../app/src/main/java/com/nextup/alarmcountdown/notification/AlarmNotificationManager.kt) delegates live countdown rendering to Android SystemUI (`setUsesChronometer(true)`, `setChronometerCountDown(true)`), draws compact hero-digit status icons via [StatusIconGenerator.kt](../../app/src/main/java/com/nextup/alarmcountdown/notification/StatusIconGenerator.kt), and attaches a quick `Dismiss Next` action when appropriate.
