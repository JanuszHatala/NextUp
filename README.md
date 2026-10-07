# NextUp ⏰

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9336)-green.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-1.1.0-orange.svg)](https://github.com/JanuszHatala/NextUp)

A lightweight, zero-battery-drain Android application and fully responsive home screen widget that tracks and counts down to your upcoming alarms, while passively learning your full weekly alarm routine.

---

## 🌟 Highlights & Features

### 1. Smart Countdown Formatting
Adheres to strict, clean readability rules:
- **No Days if 0**: `3h 45m` (omits `0d`).
- **No Hours if 0**: `45m` (omits `0d 0h`).
- **Multi-day**: `2d 4h 15m`.
- **Under 1 Minute**: `< 1m`.
- **No Active Alarms**: `No alarm set`.

### 2. Fully Responsive & Adaptive Widget
- **Tiny / 1x1**: Ultra-compact tile with alarm icon and dynamically auto-sized countdown (`autoSizeTextType="uniform"`). All buttons and subtitles are stripped so the countdown is guaranteed 100% visibility without truncating.
- **Compact Wide / 2x1**: Minimalist horizontal layout with auto-scaling countdown and concise day shortcuts (`Tom 06:00`, `Today 14:00`, `Sun 07:00`) that never clip.
- **Medium Wide / 3x1 & 4x1**: Alarm icon, countdown, date shortcut, and compact NextUp app launch chip.
- **Expanded / Tall (2x2, 3x2, 4x2+)**: Displays the **Confirmed Next Alarm** at the top, and in tall mode shows the **Upcoming Routine [EST]** with the next 1–2 predicted alarms and a `+X more in app` summary.
- **Material You Monet Dynamic Theming**: Automatically adapts background and text colors to your Pixel/Android wallpaper palette.
- **Tap Actions**: Tapping the main widget card launches Google Clock directly into the alarm edit screen; tapping the NextUp chip opens NextUp.

### 3. Passive Weekly Alarm Learning Engine (0 mA Battery Drain)
Because Android strictly sandboxes 3rd-party apps from reading other apps' private alarm lists (only exposing the single nearest alarm via `AlarmManager.getNextAlarmClock()`), NextUp introduces a passive learning engine:
- **Zero Polling & Zero WakeLocks**: Never wakes the phone from sleep. Executes in < 5ms only when Android already fires an alarm change broadcast or when the user unlocks the screen.
- **Weekly Cycle Pattern Detection**: As alarms ring and cycle, NextUp registers recurring day and time slots.
- **Single-Week Trial & Auto-Deactivation**: If an alarm was scheduled for last week but is never confirmed during this week's cycle, it is automatically marked as inactive/missed (preventing phantom alarms).
- **Manual Management**: View, toggle, delete, or tap "Set" to recreate any learned pattern directly in Google Clock.

### 4. Comprehensive SQLite Event Ledger & Routine Statistics
Built-in native SQLite database storing:
- `alarm_events`: Immutable event log capturing every lifecycle event with timestamps, day of week, month, year, and season (`SPRING`, `SUMMER`, `AUTUMN`, `WINTER`).
- `alarm_patterns`: Weekly repeating slots with confirmed counts, missed counts, consecutive weeks, and confidence scores.
- `routine_statistics`: Pre-aggregated statistics table computing wake-up metrics across `WEEKLY`, `MONTHLY`, `QUARTERLY`, `SEASONAL`, and `ALL_TIME` periods (average wake time, earliest/latest wake minutes, reliability rates).

---

## 🛠 Tech Stack & Architecture

- **Language**: Kotlin 2.3+
- **UI Framework**: Jetpack Compose (Edge-to-Edge, Material 3, Dynamic Color)
- **Widget**: Android `AppWidgetProvider` + `RemoteViews` with Android 12+ Responsive Sizing (`RemoteViews(Map<SizeF, RemoteViews>)`) and TextView Auto-Sizing
- **Persistence**: Android native `SQLiteOpenHelper` with transactional single-pass queries
- **Target SDK**: Android 16 (API 36) / Compatible with Android 17 (Pixel 10 Pro)
- **Minimum SDK**: Android 8.0 (API 26)

---

## 🚀 Building from Source

### Prerequisites
- Android SDK installed (`platforms/android-36` or `android-37`)
- JDK 17+

### Clone & Build
```bash
git clone https://github.com/JanuszHatala/NextUp.git
cd NextUp

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install on connected device (ADB)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.
