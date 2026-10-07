# Android AppWidget (RemoteViews) & Compose UI Standards

## 1. RemoteViews & AppWidget Invariants
- **Strict Allowed View Hierarchy**:
  - RemoteViews strictly allows only classes annotated with `@RemoteView` (`FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout`, `ImageView`, `TextView`, `Button`, `ProgressBar`, `AdapterViewFlipper`, `StackView`, `ListView`, `GridView`).
  - **Never use `<View>`** for dividers, spacers, or placeholders. Always use `<ImageView android:contentDescription="@null">` with a color background or container borders.
- **Explicit Click Boundaries**:
  - Do not rely on parent card containers (`widget_root`, `widget_top_section`) to route child clicks.
  - Every interactive element (e.g. alarm countdown, target time, app icon, individual routine rows) must have an explicit `setOnClickPendingIntent` attached to prevent accidental click bubbling to parent containers.

## 2. Jetpack Compose Constraints
- **Material 3 Switch Sizing**:
  - M3 `Switch` has a minimum internal drawing canvas of 52dp.
  - **Never use `Modifier.size(...)` on `Switch`** as it causes layout overflow and collision with adjacent controls.
  - Scale down using `Modifier.scale(0.8f - 0.85f)` paired with explicit spacing (`Arrangement.spacedBy(...)`).
- **Compact Button Centering**:
  - When constraining `OutlinedButton` or `Button` height (e.g. `32dp - 36dp`), override default padding with `contentPadding = PaddingValues(horizontal = 10.dp..12.dp, vertical = 0.dp)` to ensure label text is vertically and horizontally centered.
- **Badge & Status Text Overflow**:
  - Subtitle status indicators must enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to prevent awkward multi-line word breaks.

## 3. AlarmManager & System Clock Integration
- **Prevent Duplicate Alarms**:
  - Before invoking `AlarmClock.ACTION_SET_ALARM`, always verify if the target time matches `AlarmManager.getNextAlarmClock()`.
  - If already active, reflect this in the UI (e.g. "Active ✓") and navigate to the existing alarm rather than dispatching a duplicate create intent.
