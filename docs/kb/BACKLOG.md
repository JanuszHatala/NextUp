# Feature Backlog & Postponed Scope

## Planned / Postponed Features

### BKL-001: Seasonal, Quarterly & Monthly Routine Analytics UI
- **Priority:** Medium
- **Origin:** Session `05507a19` (2026-10-07); deferred during v1.1.0 and v1.5.0 planning.
- **Goal:** Visualize long-term wake-up routine evolution across weeks, months, quarters, and meteorological seasons (`SPRING`, `SUMMER`, `AUTUMN`, `WINTER`), including Weekday vs. Weekend shifts and behavioral change detection.
- **Existing Groundwork:**
  - `routine_statistics` SQLite table in [NextUpDatabaseHelper.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/db/NextUpDatabaseHelper.kt) with `WEEKLY`, `MONTHLY`, `QUARTERLY`, `SEASONAL`, and `ALL_TIME` period keys.
  - [AlarmDatabaseManager.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/db/AlarmDatabaseManager.kt) pre-aggregates confirmed/missed totals, reliability, and wake minutes (fixed in v1.5.0 to filter `WEEKLY` and `QUARTERLY` queries properly and exclude missed events from wake averages).
- **Remaining Work:**
  - Add a seasonal/period comparison selector and trend cards on [StatsScreen.kt](../../app/src/main/java/com/nextup/alarmcountdown/ui/screens/StatsScreen.kt).
  - Add a one-time recalculation helper to backfill historical `routine_statistics` rows recorded prior to v1.5.0.

### BKL-002: First-Alarm-of-Day Wake-Up Trend Line Chart
- **Priority:** Medium
- **Origin:** v1.5.0 `Stats` tab design scoping (2026-11-19).
- **Goal:** Plot the earliest confirmed alarm per calendar day over the last 14/30 days (separating Weekdays vs. Weekends) so users can see sleep schedule drift visually over time.
- **Existing Groundwork:** `AlarmStatsCalculator.compute` already computes per-day earliest confirmed alarm minutes separated by `isWeekend`.
- **Remaining Work:** Expose daily earliest-alarm series from `AlarmStatsCalculator` and render a line/dot chart on `StatsScreen`.

### BKL-003: Rich Direct System Alarm Management from Learned Routines
- **Priority:** Low
- **Origin:** Session `05507a19` (2026-10-07).
- **Goal:** Allow editing or creating multi-day system alarms and per-day overrides directly from NextUp without manual re-entry across multiple Clock screens.
- **Existing Groundwork:** `Set` / `Active ✓` button dispatches `AlarmClock.ACTION_SET_ALARM` with `EXTRA_HOUR`, `EXTRA_MINUTES`, and `EXTRA_DAYS` when supported by the system Clock app.
- **Remaining Work:** Investigate OEM Clock intent limitations (e.g., programmatic dismissal/editing requires user Clock app UI or `DISMISS_ALARM` permissions) and design inline time-picker / day-override flows.

### BKL-004: Google Play Store Release & Signing Pipeline
- **Priority:** Low (Post-stabilization)
- **Origin:** Session `05507a19` (2026-10-07).
- **Goal:** Publish NextUp publicly on Google Play Store while remaining 100% offline and GPLv3.
- **Existing Groundwork:** Complete technical checklist in [PLAY_STORE_ROADMAP.md](../../PLAY_STORE_ROADMAP.md).
- **Remaining Work:** Generate release keystore (`nextup-release-key.jks`), produce 512x512 icon + 1024x500 feature graphic + screenshots, host static privacy policy, and enable R8 minification (`isMinifyEnabled = true`) in release builds.
