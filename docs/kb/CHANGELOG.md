# Changelog

## [1.5.0] - 2026-11-19 (Build 18)
### Added
- 3-tab top-level navigation (`Schedule`, `History`, `Stats`) using Material 3 `NavigationBar` (portrait) and `NavigationRail` (landscape).
- `History` screen (`ui/screens/HistoryScreen.kt`) with `Inactive Archive` sub-tab (Set, Restore, Delete, and bulk `Clean up one-offs` keeping event stats intact) and filterable `Event Log` timeline (`All`, `Confirmed`, `Missed`).
- `Stats` screen (`ui/screens/StatsScreen.kt`) powered by `AlarmStatsCalculator` with `30 Days` / `All Time` timeframes, KPI grid (`Confirmed`, `Reliability`, `Top Alarm`, `Earliest Avg`), native Compose `Canvas` Day-of-Week bar chart, and Time-of-Day distribution bars.
- Project Knowledge Base (`docs/kb/`) and enforcement rule (`.agents/rules/knowledge-base.md`).

### Changed
- `WeeklyScheduleSection` now displays only active recurring routines plus a compact footer link to `History` when inactive/one-off alarms exist.
- Database upgraded to version 2 with descending index `idx_events_scheduled` on `alarm_events(scheduled_epoch_millis DESC)`.

### Fixed
- Fixed missing SQL `WHERE` filters for `WEEKLY` and `QUARTERLY` rows in `AlarmDatabaseManager.computeAndUpsertStatsForPeriod` and restricted wake-time aggregations to `CONFIRMED_SEEN` events.

## [1.4.6] - 2026-10-28 (Build 17)
### Fixed
- Eliminated `NotificationManager` wakelocks, screen-off UI re-renders, and duplicate IPC calls to minimize standby battery drain (`#15`).

## [1.4.5] - 2026-10-25 (Build 16)
### Fixed
- Decoupled non-wakeup minute refreshes from ring wakeups to prevent frozen widgets (`#13`).
- Eliminated cached wakelocks and recurring screen-off waking alarms (`#12`).

## [1.3.0 - 1.4.0] - 2026-10-17
### Added
- Status bar hero-digit alarm countdown badge and enlarged badge layout (`#10`, `#11`).
- Routine Learning progress banner (computed from earliest recorded alarm and distinct days) with Lock/Restart/Reset controls.
- Landscape 2-column app layout and `MainActivity` Settings screen.

### Fixed
- Immediate notification update on clock change without coroutine blocking.
- Stabilized widget alignment toggle via synchronous `commit()`, 400ms debounce, and `RemoteViews` adapter cache invalidation (`#9`).

## [1.2.0] - 2026-10-08 (Build 3)
### Added
- Scrollable `ListView` routine list in home screen widget via `NextUpWidgetService` (`#8`).
- Left/Center widget alignment toggle inside widget and Settings.
- Grouped weekly routine patterns (`Every day`, `Weekdays`, `Weekend`, ordered day chips).

### Fixed
- Replaced disallowed `<View>` tags in widget layouts with `<ImageView>` (`#6`).
- Fixed widget click routing boundaries and prevented duplicate Clock alarm creation (`#3`).

## [1.1.0] - 2026-10-07
### Added
- Passive Weekly Alarm Learning Engine (`AlarmLearningEngine`) and SQLite persistence (`alarm_events`, `alarm_patterns`, `routine_statistics`).
- Persistent countdown notification using native SystemUI chronometer (`#4`).
- Play Store release backlog (`PLAY_STORE_ROADMAP.md`) and GitHub Actions CI (`ci.yml`).

## [1.0.0] - 2026-10-03
### Added
- Initial NextUp Android application, smart duration formatter (`AlarmFormatter`), and responsive home screen widgets.
