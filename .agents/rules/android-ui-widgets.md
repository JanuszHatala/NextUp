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
  - **Badges, Tags & Acronym Chips (e.g. `EST`, `AM/PM`, Day Chips)**:
    - Never allow badges or tag labels to soft-wrap into multiple lines under layout pressure.
    - Badges MUST always enforce `maxLines = 1` and `softWrap = false`.
    - Containers wrapping tags must use `Modifier.wrapContentWidth()` (or horizontal scroll/flow) rather than being squished by adjacent weighted elements.

## 3. AlarmManager & System Clock Integration
- **Prevent Duplicate Alarms**:
  - Before invoking `AlarmClock.ACTION_SET_ALARM`, always verify if the target time matches `AlarmManager.getNextAlarmClock()`.
  - If already active, reflect this in the UI (e.g. "Active ✓") and navigate to the existing alarm rather than dispatching a duplicate create intent.

## 4. AppWidget Collections & Dynamic Sizing
- **Use Collection Widgets (`ListView` + `RemoteViewsService`) for Variable Lists**:
  - Never simulate dynamic lists by generating multiple permutations of static layouts with hardcoded row counts (`tallViews2`, `tallViews3`, etc.) and fixed height breakpoints.
  - Variable lists in AppWidgets must use `<ListView android:scrollbars="none" .../>` backed by a `RemoteViewsService` and `RemoteViewsFactory`.
  - The `ListView` dynamically expands (`layout_height="0dp"`, `layout_weight="1"`) to fill whatever vertical space is available on any device form factor (small phone, foldable, tablet) and allows smooth touch scrolling with hidden scrollbars.
  - Set `setPendingIntentTemplate` on the `ListView` and `setOnClickFillInIntent` on item views to route clicks cleanly.
- **Dynamic Sizing over Hardcoded Guesswork**:
  - Do not hardcode height/width guesses for text rows or widget sizes.
  - Rely on Android's native view measurement (`wrap_content`, `0dp` with weights), responsive archetypes (`RemoteViews(Map<SizeF, RemoteViews>)` with standard form factors), and `AppWidgetManager.getAppWidgetOptions()` for dimension inspection.

## 5. AppWidget State Persistence & Collection Invalidation
- **Synchronous Disk Persistence in Broadcast Receivers**:
  - Always use `commit()` instead of `apply()` when persisting settings in `BroadcastReceiver` handlers (such as widget toggle buttons).
  - Modern Android freezes cached app processes immediately after `onReceive()` returns; asynchronous `apply()` writes may be delayed or preempted, causing state reversion upon subsequent process wakeup or system broadcasts.
- **Debounce Interactive Widget Buttons**:
  - Interactive widget buttons wired to broadcast PendingIntents must be debounced (e.g., minimum 400ms threshold) to ignore accidental rapid double-taps or bounced touch events that toggle state back and forth.
- **Invalidate RemoteViewsFactory Cache on Layout/Alignment Changes**:
  - When collection item layouts change dynamically (e.g., alignment switch), set `hasStableIds() = false`.
  - Embed the dynamic layout state in the `RemoteViewsService` `Intent.data` URI (e.g., `content://com.nextup.alarmcountdown.widget/$appWidgetId?align=$isCentered`) so Android's `RemoteViewsAdapter` recognizes the data-source change and purges stale cached layouts.
- **Avoid Leaking Activity Contexts**:
  - Always use `context.applicationContext` when invoking `AppWidgetManager.getInstance(context)` or updating widgets from Compose / Activities to avoid leaking Activity service connections (`ServiceConnectionLeaked`).

