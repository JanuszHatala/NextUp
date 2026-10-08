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
import com.nextup.alarmcountdown.data.AlarmModel
import com.nextup.alarmcountdown.data.AlarmRepository
import com.nextup.alarmcountdown.data.NextUpPreferences
import com.nextup.alarmcountdown.data.db.AlarmDatabaseManager
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.notification.AlarmNotificationManager
import com.nextup.alarmcountdown.ui.theme.NextUpTheme
import com.nextup.alarmcountdown.util.AlarmFormatter
import com.nextup.alarmcountdown.widget.NextUpWidgetProvider
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
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
                            onOpenClock = { openClockApp(repository.getNextAlarm()) },
                            onSetAlarm = { openSetAlarmScreen() },
                            onSetPatternInClock = { pattern -> setPatternInClock(pattern) },
                            onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                        )
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            prefs = prefs,
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

    private fun setPatternInClock(pattern: AlarmPattern) {
        val current = repository.getNextAlarm()
        if (isPatternMatchingNextAlarm(pattern, current)) {
            android.widget.Toast.makeText(
                this,
                "Alarm for ${pattern.timeFormatted} is already active in Clock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            openClockApp(current)
            return
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, pattern.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, pattern.minute)
            putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(pattern.dayOfWeek))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            openClockApp(null)
        }
    }
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
    onOpenClock: () -> Unit,
    onSetAlarm: () -> Unit,
    onSetPatternInClock: (AlarmPattern) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nextAlarm by repository.observeNextAlarm().collectAsStateWithLifecycle(initialValue = repository.getNextAlarm())

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
                    WeeklyScheduleSection(
                        learnedPatterns = learnedPatterns,
                        nowMillis = nowMillis,
                        nextAlarm = nextAlarm,
                        onOpenClock = onOpenClock,
                        onSetPatternInClock = onSetPatternInClock,
                        onToggleActive = { pattern, active ->
                            coroutineScope.launch {
                                dbManager.togglePatternActive(pattern.patternId, active)
                                refreshPatterns()
                                NextUpWidgetProvider.updateAllWidgets(context)
                            }
                        },
                        onDeletePattern = { pattern ->
                            coroutineScope.launch {
                                dbManager.deletePattern(pattern.patternId)
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

                Spacer(modifier = Modifier.height(20.dp))

                WeeklyScheduleSection(
                    learnedPatterns = learnedPatterns,
                    nowMillis = nowMillis,
                    nextAlarm = nextAlarm,
                    onOpenClock = onOpenClock,
                    onSetPatternInClock = onSetPatternInClock,
                    onToggleActive = { pattern, active ->
                        coroutineScope.launch {
                            dbManager.togglePatternActive(pattern.patternId, active)
                            refreshPatterns()
                            NextUpWidgetProvider.updateAllWidgets(context)
                        }
                    },
                    onDeletePattern = { pattern ->
                        coroutineScope.launch {
                            dbManager.deletePattern(pattern.patternId)
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
    onSetPatternInClock: (AlarmPattern) -> Unit,
    onToggleActive: (AlarmPattern, Boolean) -> Unit,
    onDeletePattern: (AlarmPattern) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var patternToDelete by remember { mutableStateOf<AlarmPattern?>(null) }
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
                text = "${learnedPatterns.count { it.isActive }} active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (learnedPatterns.isEmpty()) {
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
            learnedPatterns.forEach { pattern ->
                val nextOccurrence = pattern.getNextOccurrenceMillis(nowMillis)
                val remaining = AlarmFormatter.formatRemaining(nextOccurrence, nowMillis)
                val isAlreadyActive = isPatternMatchingNextAlarm(pattern, nextAlarm)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pattern.isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Day Chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = pattern.dayNameShort.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = pattern.timeFormatted,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Predicted Badge
                                Box(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EST",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (pattern.isActive) "Next: in $remaining" else "Deactivated / Missed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pattern.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAlreadyActive) {
                                FilledTonalButton(
                                    onClick = {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Alarm for ${pattern.timeFormatted} is already active in Clock",
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
                                    onClick = { onSetPatternInClock(pattern) },
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

                            // Toggle active switch with safe scaling & margin
                            Switch(
                                checked = pattern.isActive,
                                onCheckedChange = { active ->
                                    onToggleActive(pattern, active)
                                },
                                modifier = Modifier.scale(0.85f)
                            )

                            // Delete button (prompts confirmation dialog)
                            IconButton(
                                onClick = {
                                    patternToDelete = pattern
                                },
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
                }
            }
        }
    }

    if (patternToDelete != null) {
        val target = patternToDelete!!
        AlertDialog(
            onDismissRequest = { patternToDelete = null },
            title = {
                Text(
                    text = "Delete Learned Alarm?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the learned routine for ${target.dayNameShort} at ${target.timeFormatted}? NextUp won't predict this alarm until it is detected again.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePattern(target)
                        patternToDelete = null
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
                TextButton(onClick = { patternToDelete = null }) {
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
