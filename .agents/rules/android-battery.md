# Android Battery & Background Processing Standards

## Strict Zero-Drain Invariant
All Android applications and features must adhere to ultra-low power consumption guidelines (targeting ~0 mA background draw):

1. **Strictly Event-Driven (Passive) & No Periodic Exact Alarms**:
   - **NEVER** use `AlarmManager.setExactAndAllowWhileIdle()` or `setExact()` for recurring periodic UI updates (e.g., minute-by-minute countdowns).
   - Only use exact alarms for true user-critical scheduled deadlines (e.g., the exact alarm ring time).
   - Do not wake the CPU from Android Doze mode.
   - Execute logic strictly in response to natural OS system broadcasts (e.g. `ACTION_NEXT_ALARM_CLOCK_CHANGED`) or interactive user events (`ACTION_USER_PRESENT`, app open).

2. **Native Notification Chronometer for Countdowns**:
   - For live countdown notifications, use Android's native framework capability (`.setUsesChronometer(true)`, `.setChronometerCountDown(true)`, `.setWhen(triggerTime)`).
   - Allow SystemUI to handle countdown rendering on the GPU/display server without waking the app CPU, coroutines, or process.

3. **Screen-Off / Idle Awareness**:
   - Check `PowerManager.isInteractive` before executing non-critical UI re-renders or bitmap generations.
   - Defer non-critical updates until natural system events (`ACTION_SCREEN_ON`, `ACTION_USER_PRESENT`, or app foregrounding).

4. **No Persistent WakeLocks or Foreground Services**:
   - Never acquire `PowerManager.WakeLock` unless doing active real-time media/navigation playback explicitly requested by the user.
   - Avoid background services that remain resident in memory.

5. **Lazy Evaluation for Time-Based State**:
   - Never schedule waking timers merely to update an internal state or detect misses.
   - Evaluate elapsed transitions and missed occurrences lazily upon the next natural system wake or user interaction.

6. **Microsecond Database Operations**:
   - Perform all SQLite / persistence operations on `Dispatchers.IO` using indexed, single-pass queries.
   - Close cursors and release binder IPC resources immediately so the app process can enter deep idle.

