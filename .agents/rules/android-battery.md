# Android Battery & Background Processing Standards

## Strict Zero-Drain Invariant
All Android applications and features must adhere to ultra-low power consumption guidelines (targeting ~0 mA background draw):

1. **Strictly Event-Driven (Passive)**:
   - Never use polling loops, periodic timers, or high-frequency background alarms.
   - Do not wake the CPU from Android Doze mode.
   - Execute logic strictly in response to natural OS system broadcasts (e.g. `ACTION_NEXT_ALARM_CLOCK_CHANGED`) or interactive user events (`ACTION_USER_PRESENT`, app open).

2. **No Persistent WakeLocks or Foreground Services**:
   - Never acquire `PowerManager.WakeLock` unless doing active real-time media/navigation playback explicitly requested by the user.
   - Avoid background services that remain resident in memory.

3. **Lazy Evaluation for Time-Based State**:
   - Never schedule waking timers merely to update an internal state or detect misses.
   - Evaluate elapsed transitions and missed occurrences lazily upon the next natural system wake or user interaction.

4. **Microsecond Database Operations**:
   - Perform all SQLite / persistence operations on `Dispatchers.IO` using indexed, single-pass queries.
   - Close cursors and release binder IPC resources immediately so the app process can enter deep idle.
