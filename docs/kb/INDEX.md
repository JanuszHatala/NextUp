# NextUp Knowledge Base

- **Current Version:** `1.5.0` (Build `18`)
- **Platform / Stack:** Kotlin 2.3.20, Jetpack Compose (Material 3, Dynamic Color), RemoteViews AppWidget (`ListView` + `RemoteViewsService`), Native `SQLiteOpenHelper` (DB v2), Min SDK 26 / Target SDK 36
- **License:** GNU General Public License v3.0 (GPLv3)
- **Repository Rules:**
  - [android-battery.md](../../.agents/rules/android-battery.md) - Strict ~0 mA passive event-driven invariants
  - [android-ui-widgets.md](../../.agents/rules/android-ui-widgets.md) - RemoteViews, Compose sizing, and AlarmManager rules
  - [git-workflow.md](../../.agents/rules/git-workflow.md) - Feature branch + PR workflow and Git identity enforcement
  - [knowledge-base.md](../../.agents/rules/knowledge-base.md) - Mandatory `docs/kb/` sync lifecycle

## Document Map

- [FEATURES.md](./FEATURES.md) - Canonical specification of implemented capabilities.
- [BACKLOG.md](./BACKLOG.md) - Deferred features, future analytics roadmap, and Play Store backlog.
- [DECISIONS.md](./DECISIONS.md) - Architectural Decision Records (`ADR-001` through `ADR-007`).
- [CHANGELOG.md](./CHANGELOG.md) - Release and commit history from `v1.0.0` through `v1.5.0`.

## High-Level Architecture

- **Entry & UI:** [MainActivity.kt](../../app/src/main/java/com/nextup/alarmcountdown/MainActivity.kt) hosts 3 top-level tabs (`Schedule`, `History`, `Stats`) via `NavigationBar` (portrait) / `NavigationRail` (landscape) plus `SettingsScreen` from the top app bar.
- **Data & Learning:** [AlarmRepository.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/AlarmRepository.kt), [AlarmLearningEngine.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/AlarmLearningEngine.kt), and [AlarmDatabaseManager.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/db/AlarmDatabaseManager.kt) observe `AlarmManager.getNextAlarmClock()` passively on OS broadcasts and foreground interaction.
- **Analytics:** [AlarmStatsCalculator.kt](../../app/src/main/java/com/nextup/alarmcountdown/data/AlarmStatsCalculator.kt) computes pure-Kotlin KPIs, day-of-week counts, and time-of-day distributions lazily from SQLite `alarm_events` and `alarm_patterns`.
- **Notifications & Widgets:** [AlarmNotificationManager.kt](../../app/src/main/java/com/nextup/alarmcountdown/notification/AlarmNotificationManager.kt) uses native SystemUI chronometers; [NextUpWidgetProvider.kt](../../app/src/main/java/com/nextup/alarmcountdown/widget/NextUpWidgetProvider.kt) and [NextUpWidgetService.kt](../../app/src/main/java/com/nextup/alarmcountdown/widget/NextUpWidgetService.kt) render auto-sizing countdowns and scrollable active routine lists.
