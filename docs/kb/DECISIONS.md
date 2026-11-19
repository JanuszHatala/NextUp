# Architectural Decision Records

## ADR-001: Passive Event-Driven Learning Instead of Polling or Exact Background Timers
- **Status:** Accepted
- **Context:** Android restricts third-party apps from reading private alarm databases; only the single nearest alarm is visible via `AlarmManager.getNextAlarmClock()`. Meanwhile, NextUp must maintain ~0 mA background battery draw.
- **Decision:** Observe alarms passively only when OS broadcasts fire (`ACTION_NEXT_ALARM_CLOCK_CHANGED`, `ACTION_USER_PRESENT`) or when the user opens the app. Evaluate missed occurrences lazily on the next natural wakeup rather than scheduling periodic waking timers or `WakeLock`s.
- **Consequences:** ~0% standby battery impact (enforced by `.agents/rules/android-battery.md`), at the cost that widget/DB updates rely on system broadcasts and non-wakeup refresh heuristics.

## ADR-002: Native SQLiteOpenHelper with Pre-Aggregated Statistics Table
- **Status:** Accepted (Updated in v1.5.0 DB v2)
- **Context:** Recording alarm history and future seasonal analytics required fast queries with minimal APK/dependency overhead.
- **Decision:** Use Android's built-in `SQLiteOpenHelper` (`alarm_events`, `alarm_patterns`, `routine_statistics`) instead of adding Room/kapt/KSP overhead. In v1.5.0 (DB v2), add `idx_events_scheduled` descending index and fix `WEEKLY`/`QUARTERLY` SQL filters.
- **Consequences:** Tiny APK footprint and < 5ms IO calls, requiring explicit `onUpgrade` SQL migrations when indices or columns change.

## ADR-003: Collection AppWidget (`ListView` + `RemoteViewsService`) with Synchronous Prefs Commit
- **Status:** Accepted
- **Context:** Static multi-layout permutations clipped alarm lists on varying screen densities, and asynchronous `SharedPreferences.apply()` caused widget alignment toggles to revert when Android froze cached processes after `onReceive()`.
- **Decision:** Use a `<ListView>` collection widget backed by `NextUpWidgetService` with `hasStableIds() = false`, alignment embedded in `Intent.data` URIs, synchronous `commit()` for receiver persistence, and 400ms click debouncing.
- **Consequences:** Smooth touch-scrolling across any device size and reliable alignment toggles without cache corruption.

## ADR-004: Native Notification Chronometer for Status Bar & Ongoing Countdown
- **Status:** Accepted
- **Context:** Updating notification text every minute via background `AlarmManager` wakeups drained overnight battery.
- **Decision:** Delegate live countdown rendering to Android SystemUI via `setUsesChronometer(true)` and `setChronometerCountDown(true)` while only refreshing icon badges when screen is interactive.
- **Consequences:** Zero CPU wakeups for minute ticks in notifications.

## ADR-005: Group Patterns by `(hour, minute)` with Active-Only Main Schedule & Non-Destructive One-Off Cleanup
- **Status:** Accepted (v1.5.0)
- **Context:** One-off and missed alarms cluttered the main `Weekly Schedule & Predictions` screen, making recurring routines hard to scan.
- **Decision:** Keep the `Schedule` tab strictly for active recurring `GroupedAlarmPattern` entries (`activeDays.isNotEmpty()`). Move inactive/one-off groups to `History -> Inactive Archive` while keeping `(hour, minute)` as the single grouping key so reactivating an old time (`03:30`) updates the existing entry instead of creating duplicates. Bulk one-off cleanup deletes only inactive 1x `alarm_patterns`, preserving `alarm_events` for `Stats`.
- **Consequences:** Clean main screen, zero duplicate cards on reactivation, and uninterrupted statistical history.

## ADR-006: Earliest-Confirmed-Alarm-of-Day Metric & Native Compose `Canvas` Charts
- **Status:** Accepted (v1.5.0)
- **Context:** Users set both morning wake-up alarms (`06:00`) and evening reminder alarms (`20:45`). Averaging all alarm timestamps produces meaningless midday numbers, and third-party chart libraries add dependency bloat.
- **Decision:** Compute `Earliest Avg` using each calendar day's earliest `CONFIRMED_SEEN` alarm (split Weekday/Weekend), pair it with Day-of-Week bar and Time-of-Day window distributions, and draw charts using pure Compose `Canvas` + `Box` primitives in `StatsScreen.kt`.
- **Consequences:** Accurate wake-up insights with zero extra Gradle dependencies and full Material 3 Dynamic Color theming.

## ADR-007: Modular Repository Knowledge Base (`docs/kb/`) + Global `jh-project-kb` Skill
- **Status:** Accepted (v1.5.0)
- **Context:** Feature backlog items and architectural context scattered across past Antigravity conversation transcripts became hard to locate over time.
- **Decision:** Maintain modular `docs/kb/` (`INDEX.md`, `FEATURES.md`, `BACKLOG.md`, `DECISIONS.md`, `CHANGELOG.md`) enforced by `.agents/rules/knowledge-base.md` and supported by a reusable global skill (`~/.gemini/config/skills/jh-project-kb/`).
- **Consequences:** Every PR keeps engineering context, postponed work, and ADRs discoverable inside version control.
