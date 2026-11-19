package com.nextup.alarmcountdown.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextup.alarmcountdown.data.model.AlarmEvent
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistorySubTab {
    INACTIVE,
    EVENT_LOG
}

enum class EventLogFilter(val label: String) {
    ALL("All"),
    CONFIRMED("Confirmed"),
    MISSED("Missed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    learnedPatterns: List<AlarmPattern>,
    alarmEvents: List<AlarmEvent>,
    subTab: HistorySubTab,
    onSelectSubTab: (HistorySubTab) -> Unit,
    onSetGroupedPatternInClock: (GroupedAlarmPattern) -> Unit,
    onRestoreGroup: (Int, Int) -> Unit,
    onDeleteGroup: (Int, Int) -> Unit,
    onCleanupOneOffs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inactiveGroups = remember(learnedPatterns) {
        GroupedAlarmPattern.fromPatterns(learnedPatterns)
            .filter { !it.isActive }
            .sortedWith(
                compareByDescending<GroupedAlarmPattern> { it.lastSeenMillis }
                    .thenBy { it.hour }
                    .thenBy { it.minute }
            )
    }

    var eventFilter by remember { mutableStateOf(EventLogFilter.ALL) }
    var groupToDelete by remember { mutableStateOf<GroupedAlarmPattern?>(null) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = subTab == HistorySubTab.INACTIVE,
                onClick = { onSelectSubTab(HistorySubTab.INACTIVE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(
                    text = "Inactive (${inactiveGroups.size})",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SegmentedButton(
                selected = subTab == HistorySubTab.EVENT_LOG,
                onClick = { onSelectSubTab(HistorySubTab.EVENT_LOG) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(
                    text = "Event Log",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (subTab) {
            HistorySubTab.INACTIVE -> {
                InactiveArchiveContent(
                    inactiveGroups = inactiveGroups,
                    onSetInClock = onSetGroupedPatternInClock,
                    onRestoreGroup = onRestoreGroup,
                    onRequestDelete = { groupToDelete = it },
                    onRequestCleanupOneOffs = { showCleanupDialog = true }
                )
            }
            HistorySubTab.EVENT_LOG -> {
                EventLogContent(
                    alarmEvents = alarmEvents,
                    filter = eventFilter,
                    onFilterChange = { eventFilter = it }
                )
            }
        }
    }

    if (groupToDelete != null) {
        val target = groupToDelete!!
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = {
                Text(
                    text = "Delete Archived Alarm?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove ${target.timeFormatted} (${target.historicalDaysLabel}) from the archive? Past event history and stats will be preserved.",
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

    if (showCleanupDialog) {
        val oneOffCount = inactiveGroups.count { it.isOneOff }
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = {
                Text(
                    text = "Clean Up One-Off Alarms?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove $oneOffCount inactive one-off alarm(s) from this archive list? All recorded event logs and statistics will remain untouched.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanupDialog = false
                        onCleanupOneOffs()
                    }
                ) {
                    Text("Clean Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InactiveArchiveContent(
    inactiveGroups: List<GroupedAlarmPattern>,
    onSetInClock: (GroupedAlarmPattern) -> Unit,
    onRestoreGroup: (Int, Int) -> Unit,
    onRequestDelete: (GroupedAlarmPattern) -> Unit,
    onRequestCleanupOneOffs: () -> Unit
) {
    val oneOffCount = remember(inactiveGroups) { inactiveGroups.count { it.isOneOff } }

    if (inactiveGroups.isEmpty()) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Archive is Empty",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "One-off or deactivated alarms will appear here automatically to keep your active schedule focused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "archive_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${inactiveGroups.size} archived · $oneOffCount one-off",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (oneOffCount > 0) {
                    TextButton(
                        onClick = onRequestCleanupOneOffs,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Clean up one-offs",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        items(
            items = inactiveGroups,
            key = { "${it.hour}:${it.minute}" }
        ) { group ->
            InactiveAlarmCard(
                group = group,
                onSetInClock = { onSetInClock(group) },
                onRestore = { onRestoreGroup(group.hour, group.minute) },
                onDelete = { onRequestDelete(group) }
            )
        }
    }
}

@Composable
private fun InactiveAlarmCard(
    group: GroupedAlarmPattern,
    onSetInClock: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val lastDateLabel = remember(group.lastSeenMillis) {
        if (group.lastSeenMillis > 0L) {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(group.lastSeenMillis))
        } else {
            "Unknown"
        }
    }

    val metaText = if (group.isOneOff) {
        "One-off · Last $lastDateLabel"
    } else {
        "Previously ${group.historicalDaysLabel} · Seen ${group.totalConfirmedCount}x · Last $lastDateLabel"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                Text(
                    text = group.timeFormatted,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onSetInClock,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Set",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                FilledTonalButton(
                    onClick = onRestore,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Restore",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                IconButton(
                    onClick = onDelete,
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

@Composable
private fun EventLogContent(
    alarmEvents: List<AlarmEvent>,
    filter: EventLogFilter,
    onFilterChange: (EventLogFilter) -> Unit
) {
    val filteredEvents = remember(alarmEvents, filter) {
        when (filter) {
            EventLogFilter.ALL -> alarmEvents
            EventLogFilter.CONFIRMED -> alarmEvents.filter { it.eventType == "CONFIRMED_SEEN" }
            EventLogFilter.MISSED -> alarmEvents.filter { it.eventType == "MISSED_OR_DEACTIVATED" }
        }
    }

    val groupedByDate = remember(filteredEvents) {
        val nowCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val dateFormatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        filteredEvents.groupBy { event ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (event.scheduledEpochMillis > 0L) event.scheduledEpochMillis else event.recordedEpochMillis
            }
            val isToday = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
            val isYesterday = cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
            val formatted = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(cal.timeInMillis))
            when {
                isToday -> "Today, $formatted"
                isYesterday -> "Yesterday, $formatted"
                else -> dateFormatter.format(Date(cal.timeInMillis))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EventLogFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredEvents.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Recorded Events",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Confirmed alarms and missed routine events will appear here in chronological order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedByDate.forEach { (dateLabel, dayEvents) ->
                    item(key = "header_$dateLabel") {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(
                        items = dayEvents,
                        key = { "${it.id}_${it.scheduledEpochMillis}_${it.eventType}" }
                    ) { event ->
                        EventLogRow(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLogRow(event: AlarmEvent) {
    val isConfirmed = event.eventType == "CONFIRMED_SEEN"
    val timeText = String.format("%02d:%02d", event.alarmHour, event.alarmMinute)
    val dayShort = GroupedAlarmPattern.dayOfWeekToShortName(event.dayOfWeek)
    val recordedTime = remember(event.recordedEpochMillis) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(event.recordedEpochMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "$dayShort · Logged $recordedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isConfirmed) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isConfirmed) "Confirmed" else "Missed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isConfirmed) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
