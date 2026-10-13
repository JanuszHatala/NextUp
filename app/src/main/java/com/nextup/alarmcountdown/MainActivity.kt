package com.nextup.alarmcountdown

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import com.nextup.alarmcountdown.data.AlarmModel
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.NextUpPreferences
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import com.nextup.alarmcountdown.notification.AlarmNotificationManager
import com.nextup.alarmcountdown.ui.theme.NextUpTheme
import com.nextup.alarmcountdown.util.AlarmFormatter
import com.nextup.alarmcountdown.widget.NextUpWidgetProvider
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppScreen {
    MAIN,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: AlarmRepository
    private lateinit var dbManager: AlarmDatabaseManager
    private lateinit var prefs: NextUpPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = AlarmRepository(this)
        dbManager = AlarmDatabaseManager(this)
        prefs = NextUpPreferences.getInstance(this)

        setContent {
            NextUpTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }

                BackHandler(enabled = currentScreen != AppScreen.MAIN) {
                    currentScreen = AppScreen.MAIN
                }

                when (currentScreen) {
                    AppScreen.MAIN -> {
                        MainScreen(
                            repository = repository,
                            dbManager = dbManager,
                            prefs = prefs,
                            onOpenClock = { openClockApp(repository.getNextAlarm()) },
                            onSetAlarm = { openSetAlarmScreen() },
                            onSetGroupedPatternInClock = { group -> setGroupedPatternInClock(group) },
                            onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            prefs = prefs,
                            dbManager = dbManager,
                            onBackClick = { currentScreen = AppScreen.MAIN },
                            onPinWidget = { pinWidgetToHomeScreen() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.syncFromDisk()
        NextUpWidgetProvider.updateAllWidgets(applicationContext)
        com.nextup.alarmcountdown.notification.AlarmNotificationManager.updateNotification(applicationContext)
    }

    private fun pinWidgetToHomeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val myProvider = ComponentName(this, NextUpWidgetProvider::class.java)
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            }
        }
    }

    private fun openClockApp(alarm: AlarmModel?) {
        // 1. Try launching Google Clock directly (brings Clock reliably to foreground)
        val deskClockIntent = packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
        if (deskClockIntent != null) {
            try {
                deskClockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(deskClockIntent)
                return
            } catch (ignored: Exception) {
            }
        }

        // 2. Try standard Android SHOW_ALARMS intent
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            return
        } catch (ignored: Exception) {
        }

        // 3. Try alarm's showIntent with explicit ActivityOptions for Android 14+ BAL
        if (alarm?.showIntent != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = android.app.ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    alarm.showIntent.send(this, 0, null, null, null, null, options.toBundle())
                } else {
                    alarm.showIntent.send()
                }
                return
            } catch (ignored: Exception) {
            }
        }

        // 4. Try OEM clock fallbacks (Samsung, generic AOSP DeskClock)
        val clockPackages = listOf("com.sec.android.app.clockpackage", "com.android.deskclock")
        for (pkg in clockPackages) {
            val fallbackIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (fallbackIntent != null) {
                try {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(fallbackIntent)
                    return
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun openSetAlarmScreen() {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            openClockApp(null)
        }
    }

    private fun setGroupedPatternInClock(group: GroupedAlarmPattern) {
        val current = repository.getNextAlarm()
        if (isGroupMatchingNextAlarm(group, current)) {
            android.widget.Toast.makeText(
                this,
                "Alarm for ${group.timeFormatted} is already active in Clock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            openClockApp(current)
            return
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, group.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, group.minute)
            if (group.activeDays.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(group.activeDays))
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            putExtra(AlarmClock.EXTRA_MESSAGE, "NextUp Alarm")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            openClockApp(null)
        }
    }
}

fun isGroupMatchingNextAlarm(group: GroupedAlarmPattern, nextAlarm: AlarmModel?): Boolean {
    if (nextAlarm == null || nextAlarm.triggerTimeMillis <= 0L) return false
    val cal = Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTimeMillis }
    return group.hour == cal.get(Calendar.HOUR_OF_DAY) &&
           group.minute == cal.get(Calendar.MINUTE) &&
           (cal.get(Calendar.DAY_OF_WEEK) in group.activeDays)
}

fun isPatternMatchingNextAlarm(pattern: AlarmPattern, nextAlarm: AlarmModel?): Boolean {
    if (nextAlarm == null || nextAlarm.triggerTimeMillis <= 0L) return false
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTimeMillis }
    return pattern.dayOfWeek == cal.get(java.util.Calendar.DAY_OF_WEEK) &&
           pattern.hour == cal.get(java.util.Calendar.HOUR_OF_DAY) &&
           pattern.minute == cal.get(java.util.Calendar.MINUTE)
}

fun isWidgetAdded(context: Context): Boolean {
    val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
    val componentName = ComponentName(context, NextUpWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
    return widgetIds != null && widgetIds.isNotEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: AlarmRepository,
    dbManager: AlarmDatabaseManager,
    prefs: NextUpPreferences,
    onOpenClock: () -> Unit,
    onSetAlarm: () -> Unit,
    onSetGroupedPatternInClock: (GroupedAlarmPattern) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nextAlarm by repository.observeNextAlarm().collectAsStateWithLifecycle(initialValue = repository.getNextAlarm())
    val isLearningPeriod by prefs.isLearningPeriodFlow.collectAsStateWithLifecycle()
    val learningDayNumber by prefs.learningDayNumberFlow.collectAsStateWithLifecycle()

    var learnedPatterns by remember { mutableStateOf<List<AlarmPattern>>(emptyList()) }

    fun refreshPatterns() {
        coroutineScope.launch {
            learnedPatterns = dbManager.getAllPatterns()
        }
    }

    LaunchedEffect(Unit) {
        refreshPatterns()
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val countdownText = remember(nextAlarm, nowMillis) {
        AlarmFormatter.formatRemaining(nextAlarm?.triggerTimeMillis, nowMillis)
    }
    val targetDateTimeText = remember(nextAlarm) {
        AlarmFormatter.formatTargetDateTime(nextAlarm?.triggerTimeMillis)
    }
    val hasAlarm = nextAlarm != null && (nextAlarm?.triggerTimeMillis ?: 0L) > nowMillis

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_alarm),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (isLandscape) {
            // Horizontal Landscape: 2-Column layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Column: Nearest Alarm hero card + Action buttons
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    NearestAlarmCard(
                        countdownText = countdownText,
                        targetDateTimeText = targetDateTimeText,
                        hasAlarm = hasAlarm
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ActionButtons(
                        onOpenClock = onOpenClock,
                        onSetAlarm = onSetAlarm
                    )
                }

                // Right Column: Weekly Schedule & Predictions
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLearningPeriod) {
                        LearningPeriodBanner(
                            dayNumber = learningDayNumber,
                            onStopLearning = {
                                prefs.stopLearningPeriod()
                                android.widget.Toast.makeText(
                                    context,
                                    "Learning stopped. Current routine saved as reference.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    WeeklyScheduleSection(
                        learnedPatterns = learnedPatterns,
                        nowMillis = nowMillis,
                        nextAlarm = nextAlarm,
                        onOpenClock = onOpenClock,
                        onSetGroupedPatternInClock = onSetGroupedPatternInClock,
                        onToggleDay = { hour, minute, dayOfWeek, active ->
                            coroutineScope.launch {
                                dbManager.upsertPattern(dayOfWeek, hour, minute, active)
                                refreshPatterns()
                                NextUpWidgetProvider.updateAllWidgets(context)
                            }
                        },
                        onToggleAll = { hour, minute, active ->
                            coroutineScope.launch {
                                dbManager.toggleAllPatternsForTime(hour, minute, active)
                                refreshPatterns()
                                NextUpWidgetProvider.updateAllWidgets(context)
                            }
                        },
                        onDeleteGroup = { hour, minute ->
                            coroutineScope.launch {
                                dbManager.deleteAllPatternsForTime(hour, minute)
                                refreshPatterns()
                                NextUpWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )
                }
            }
        } else {
            // Portrait: Single Column layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                NearestAlarmCard(
                    countdownText = countdownText,
                    targetDateTimeText = targetDateTimeText,
                    hasAlarm = hasAlarm
                )

                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(
                    onOpenClock = onOpenClock,
                    onSetAlarm = onSetAlarm
                )

                if (isLearningPeriod) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LearningPeriodBanner(
                        dayNumber = learningDayNumber,
                        onStopLearning = {
                            prefs.stopLearningPeriod()
                            android.widget.Toast.makeText(
                                context,
                                "Learning stopped. Current routine saved as reference.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                WeeklyScheduleSection(
                    learnedPatterns = learnedPatterns,
                    nowMillis = nowMillis,
                    nextAlarm = nextAlarm,
                    onOpenClock = onOpenClock,
                    onSetGroupedPatternInClock = onSetGroupedPatternInClock,
                    onToggleDay = { hour, minute, dayOfWeek, active ->
                        coroutineScope.launch {
                            dbManager.upsertPattern(dayOfWeek, hour, minute, active)
                            refreshPatterns()
                            NextUpWidgetProvider.updateAllWidgets(context)
                        }
                    },
                    onToggleAll = { hour, minute, active ->
                        coroutineScope.launch {
                            dbManager.toggleAllPatternsForTime(hour, minute, active)
                            refreshPatterns()
                            NextUpWidgetProvider.updateAllWidgets(context)
                        }
                    },
                    onDeleteGroup = { hour, minute ->
                        coroutineScope.launch {
                            dbManager.deleteAllPatternsForTime(hour, minute)
                            refreshPatterns()
                            NextUpWidgetProvider.updateAllWidgets(context)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: NextUpPreferences,
    dbManager: AlarmDatabaseManager,
    onBackClick: () -> Unit,
    onPinWidget: () -> Unit
) {
    val context = LocalContext.current
    var isWidgetPinned by remember { mutableStateOf(isWidgetAdded(context)) }

    val isNotificationEnabled by prefs.isNotificationEnabledFlow.collectAsStateWithLifecycle()
    val isStatusBarInfoEnabled by prefs.isStatusBarInfoEnabledFlow.collectAsStateWithLifecycle()
    val widgetAlignment by prefs.widgetAlignmentFlow.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            prefs.isNotificationEnabled = true
            AlarmNotificationManager.updateNotification(context)
        } else {
            prefs.isNotificationEnabled = false
            AlarmNotificationManager.cancelNotification(context)
        }
    }

    LifecycleResumeEffect(Unit) {
        prefs.syncFromDisk()
        isWidgetPinned = isWidgetAdded(context)
        onPauseOrDispose { }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = if (isLandscape) 32.dp else 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            NotificationSettingsCard(
                isNotificationEnabled = isNotificationEnabled,
                isStatusBarInfoEnabled = isStatusBarInfoEnabled,
                onToggleNotification = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            prefs.isNotificationEnabled = true
                            AlarmNotificationManager.updateNotification(context)
                        }
                    } else {
                        prefs.isNotificationEnabled = false
                        AlarmNotificationManager.cancelNotification(context)
                    }
                },
                onToggleStatusBarInfo = { enabled ->
                    prefs.isStatusBarInfoEnabled = enabled
                    if (prefs.isNotificationEnabled) {
                        AlarmNotificationManager.updateNotification(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            WidgetSettingsCard(
                widgetAlignment = widgetAlignment,
                onSetAlignment = { alignment ->
                    com.nextup.alarmcountdown.util.NextUpLog.i("MainActivity", "User selected alignment in Settings: $alignment")
                    prefs.widgetAlignment = alignment
                    NextUpWidgetProvider.updateAllWidgets(context.applicationContext)
                },
                isWidgetPinned = isWidgetPinned,
                onPinWidget = {
                    onPinWidget()
                    isWidgetPinned = isWidgetAdded(context)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            RoutineDataSettingsCard(
                prefs = prefs,
                dbManager = dbManager
            )

            Spacer(modifier = Modifier.height(24.dp))

            FooterInfo()

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun NearestAlarmCard(
    countdownText: String,
    targetDateTimeText: String,
    hasAlarm: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.nearest_alarm_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = countdownText,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = targetDateTimeText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (hasAlarm) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (hasAlarm) "CONFIRMED ACTIVE" else "NO ACTIVE ALARM",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasAlarm) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ActionButtons(
    onOpenClock: () -> Unit,
    onSetAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onOpenClock,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = stringResource(id = R.string.open_clock), fontWeight = FontWeight.SemiBold)
        }

        FilledTonalButton(
            onClick = onSetAlarm,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = stringResource(id = R.string.set_alarm), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun WeeklyScheduleSection(
    learnedPatterns: List<AlarmPattern>,
    nowMillis: Long,
    nextAlarm: AlarmModel?,
    onOpenClock: () -> Unit,
    onSetGroupedPatternInClock: (GroupedAlarmPattern) -> Unit,
    onToggleDay: (Int, Int, Int, Boolean) -> Unit,
    onToggleAll: (Int, Int, Boolean) -> Unit,
    onDeleteGroup: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var groupToDelete by remember { mutableStateOf<GroupedAlarmPattern?>(null) }

    val groupedPatterns = remember(learnedPatterns) {
        GroupedAlarmPattern.fromPatterns(learnedPatterns)
    }

    val sortedGroups = remember(groupedPatterns, nowMillis, nextAlarm) {
        groupedPatterns.sortedBy { group ->
            group.getNextOccurrenceMillis(nowMillis, nextAlarm?.triggerTimeMillis) ?: Long.MAX_VALUE
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Schedule & Predictions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${groupedPatterns.count { it.isActive }} active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (groupedPatterns.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Learning Your Routine...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "As alarms trigger and cycle during the week, NextUp automatically detects recurring patterns and builds your schedule with 0% battery drain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            sortedGroups.forEach { group ->
                val nextOccurrence = group.getNextOccurrenceMillis(nowMillis, nextAlarm?.triggerTimeMillis)
                val isAlreadyActive = isGroupMatchingNextAlarm(group, nextAlarm)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (group.isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        // Top row: Label + Time, and Actions (Set, Switch, Delete)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = group.timeFormatted,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                val remainingText = if (group.isActive && nextOccurrence != null) {
                                    val remaining = AlarmFormatter.formatRemaining(nextOccurrence, nowMillis)
                                    "Next: in $remaining"
                                } else if (group.isActive) {
                                    "No upcoming days"
                                } else {
                                    "One-off / Inactive"
                                }

                                Text(
                                    text = remainingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (group.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isAlreadyActive) {
                                    FilledTonalButton(
                                        onClick = {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Alarm for ${group.timeFormatted} is already active in Clock",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            onOpenClock()
                                        },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = "Active ✓",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSetGroupedPatternInClock(group) },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Set",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Switch(
                                    checked = group.isActive,
                                    onCheckedChange = { active ->
                                        onToggleAll(group.hour, group.minute, active)
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )

                                IconButton(
                                    onClick = { groupToDelete = group },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        text = "✕",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bottom row: 7 circular weekday chips (M, T, W, T, F, S, S)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val days = listOf(
                                Calendar.MONDAY to "M",
                                Calendar.TUESDAY to "T",
                                Calendar.WEDNESDAY to "W",
                                Calendar.THURSDAY to "T",
                                Calendar.FRIDAY to "F",
                                Calendar.SATURDAY to "S",
                                Calendar.SUNDAY to "S"
                            )
                            for ((dayOfWeek, dayLetter) in days) {
                                val isSelected = dayOfWeek in group.activeDays
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = if (isSelected) 0.dp else 1.dp,
                                            color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onToggleDay(group.hour, group.minute, dayOfWeek, !isSelected)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayLetter,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (groupToDelete != null) {
        val target = groupToDelete!!
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = {
                Text(
                    text = "Delete Learned Routine?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the learned routine for ${target.daysLabel} at ${target.timeFormatted}? NextUp won't predict this alarm until it is detected again.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGroup(target.hour, target.minute)
                        groupToDelete = null
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WidgetSettingsCard(
    widgetAlignment: String,
    onSetAlignment: (String) -> Unit,
    isWidgetPinned: Boolean,
    onPinWidget: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Home Screen Widget",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "NextUp provides an auto-resizing widget showing your live countdown and predicted routine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Alignment Selector
            Text(
                text = stringResource(id = R.string.widget_alignment_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(id = R.string.widget_alignment_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isLeft = widgetAlignment == NextUpPreferences.ALIGNMENT_LEFT
                val isCenter = widgetAlignment == NextUpPreferences.ALIGNMENT_CENTER

                if (isLeft) {
                    FilledTonalButton(
                        onClick = { },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_format_align_left),
                            contentDescription = stringResource(id = R.string.widget_alignment_left),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSetAlignment(NextUpPreferences.ALIGNMENT_LEFT) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_format_align_left),
                            contentDescription = stringResource(id = R.string.widget_alignment_left),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isCenter) {
                    FilledTonalButton(
                        onClick = { },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_format_align_center),
                            contentDescription = stringResource(id = R.string.widget_alignment_center),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSetAlignment(NextUpPreferences.ALIGNMENT_CENTER) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_format_align_center),
                            contentDescription = stringResource(id = R.string.widget_alignment_center),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onPinWidget,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isWidgetPinned) "Add Another Widget" else "Add Widget to Home Screen")
            }
        }
    }
}

@Composable
fun NotificationSettingsCard(
    isNotificationEnabled: Boolean,
    isStatusBarInfoEnabled: Boolean,
    onToggleNotification: (Boolean) -> Unit,
    onToggleStatusBarInfo: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(id = R.string.notification_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Notification Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.notification_toggle_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.notification_toggle_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isNotificationEnabled,
                    onCheckedChange = onToggleNotification,
                    modifier = Modifier.scale(0.85f)
                )
            }

            if (isNotificationEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Status Bar Indicator Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.status_bar_info_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.status_bar_info_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isStatusBarInfoEnabled,
                        onCheckedChange = onToggleStatusBarInfo,
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun FooterInfo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (versionName, versionCode) = remember(context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val name = packageInfo.versionName ?: "1.2.0"
            val code = PackageInfoCompat.getLongVersionCode(packageInfo)
            Pair(name, code)
        } catch (e: Exception) {
            Pair("1.2.0", 3L)
        }
    }

    Text(
        text = "NextUp v$versionName (Build $versionCode) • Open Source (GPLv3)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun LearningPeriodBanner(
    dayNumber: Int,
    onStopLearning: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_alarm),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Learning Routine • Day $dayNumber of 7",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "NextUp is observing your alarm routine. Schedule predictions will become more accurate after a full weekly cycle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onStopLearning,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Lock Current Reference",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun RoutineDataSettingsCard(
    prefs: NextUpPreferences,
    dbManager: AlarmDatabaseManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isLearningPeriod by prefs.isLearningPeriodFlow.collectAsStateWithLifecycle()
    val learningDayNumber by prefs.learningDayNumberFlow.collectAsStateWithLifecycle()

    var showStopLearningDialog by remember { mutableStateOf(false) }
    var showRestartLearningDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Routine Learning & History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            val statusText = if (isLearningPeriod) {
                "Learning in progress (Day $learningDayNumber of 7)"
            } else {
                "Learning complete (Reference locked)"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLearningPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Option 1: Stop learning and lock current schedule as reference
            if (isLearningPeriod) {
                OutlinedButton(
                    onClick = { showStopLearningDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stop Learning (Lock Current Reference)")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Option 2: Restart learning phase (keep patterns)
            OutlinedButton(
                onClick = { showRestartLearningDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Restart Learning Period (Keep Data)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Option 3: Destructive reset all alarms & history
            OutlinedButton(
                onClick = { showResetAllDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset All Alarms & Data")
            }
        }
    }

    if (showStopLearningDialog) {
        AlertDialog(
            onDismissRequest = { showStopLearningDialog = false },
            title = { Text("Lock Routine Reference?") },
            text = {
                Text("Stop the 7-day observation period early and keep your current learned alarm schedule as the fixed reference.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopLearningDialog = false
                        prefs.stopLearningPeriod()
                        android.widget.Toast.makeText(
                            context,
                            "Learning stopped. Current routine saved as reference.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("Lock Reference")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopLearningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRestartLearningDialog) {
        AlertDialog(
            onDismissRequest = { showRestartLearningDialog = false },
            title = { Text("Restart Learning Period?") },
            text = {
                Text("This will restart the 7-day routine observation cycle from Day 1. All existing alarm patterns and history will be kept.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartLearningDialog = false
                        prefs.restartLearningPeriod()
                        android.widget.Toast.makeText(
                            context,
                            "7-day learning period restarted",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartLearningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset All Learned Alarms?") },
            text = {
                Text("All learned weekly alarm patterns, event history, and routine statistics will be permanently erased. The 7-day routine learning period will restart from Day 1.\n\nActive alarms in your Clock app will not be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetAllDialog = false
                        coroutineScope.launch {
                            dbManager.resetAllData()
                            prefs.resetLearningPeriod()
                            NextUpWidgetProvider.updateAllWidgets(context)
                            AlarmNotificationManager.updateNotification(context)
                            android.widget.Toast.makeText(
                                context,
                                "All learned alarm data has been reset",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
