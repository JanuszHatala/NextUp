package com.nextup.alarmcountdown.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextup.alarmcountdown.data.AlarmStatsCalculator
import com.nextup.alarmcountdown.data.AlarmStatsSummary
import com.nextup.alarmcountdown.data.DayOfWeekStat
import com.nextup.alarmcountdown.data.StatsTimeframe
import com.nextup.alarmcountdown.data.TimeOfDayStat
import com.nextup.alarmcountdown.data.model.AlarmEvent
import com.nextup.alarmcountdown.data.model.AlarmPattern
import com.nextup.alarmcountdown.data.model.GroupedAlarmPattern
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    alarmEvents: List<AlarmEvent>,
    learnedPatterns: List<AlarmPattern>,
    modifier: Modifier = Modifier
) {
    var timeframe by remember { mutableStateOf(StatsTimeframe.LAST_30_DAYS) }

    val groupedPatterns = remember(learnedPatterns) {
        GroupedAlarmPattern.fromPatterns(learnedPatterns)
    }

    val summary = remember(alarmEvents, groupedPatterns, timeframe) {
        AlarmStatsCalculator.compute(
            events = alarmEvents,
            groupedPatterns = groupedPatterns,
            timeframe = timeframe
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            StatsTimeframe.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = timeframe == option,
                    onClick = { timeframe = option },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = StatsTimeframe.entries.size
                    )
                ) {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (summary.totalConfirmed + summary.totalMissed == 0) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Statistics Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "As NextUp observes your alarms across ${timeframe.label.lowercase()}, your wake-up patterns and distribution charts will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            KpiGrid(summary = summary)

            Spacer(modifier = Modifier.height(16.dp))

            DayOfWeekChartCard(stats = summary.dayOfWeekCounts)

            Spacer(modifier = Modifier.height(16.dp))

            TimeOfDayBreakdownCard(stats = summary.timeOfDayCounts)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun KpiGrid(summary: AlarmStatsSummary) {
    val earliestSubtitle = when {
        summary.weekdayAvgEarliestFormatted != null && summary.weekendAvgEarliestFormatted != null ->
            "Wkday ${summary.weekdayAvgEarliestFormatted} · Wkend ${summary.weekendAvgEarliestFormatted}"
        summary.weekdayAvgEarliestFormatted != null ->
            "Weekdays avg"
        summary.weekendAvgEarliestFormatted != null ->
            "Weekends avg"
        else -> "First alarm of day"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Confirmed",
                value = summary.totalConfirmed.toString(),
                subtitle = "${summary.activeRoutineCount} active routines",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Reliability",
                value = summary.reliabilityPercent?.let { "$it%" } ?: "--",
                subtitle = "${summary.totalMissed} missed",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Top Alarm",
                value = summary.mostFrequentTime ?: "--:--",
                subtitle = if (summary.mostFrequentCount > 0) "Seen ${summary.mostFrequentCount}x" else "No data",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Earliest Avg",
                value = summary.avgEarliestDailyAlarmFormatted ?: "--:--",
                subtitle = earliestSubtitle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DayOfWeekChartCard(stats: List<DayOfWeekStat>) {
    val maxCount = remember(stats) {
        stats.maxOfOrNull { it.confirmedCount }?.coerceAtLeast(1) ?: 1
    }
    val peakColor = MaterialTheme.colorScheme.primary
    val standardColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val emptyBarColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Alarms by Day of Week",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Confirmed alarms across Monday to Sunday",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Count labels above bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                stats.forEach { item ->
                    Text(
                        text = item.confirmedCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (item.confirmedCount == maxCount && item.confirmedCount > 0) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (item.confirmedCount > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val count = stats.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.48f).coerceAtMost(28.dp.toPx())
                val chartHeight = size.height - 4.dp.toPx()

                // Baseline
                drawLine(
                    color = baselineColor,
                    start = Offset(0f, chartHeight),
                    end = Offset(size.width, chartHeight),
                    strokeWidth = 1.dp.toPx()
                )

                stats.forEachIndexed { index, item ->
                    val x = index * slotWidth + (slotWidth - barWidth) / 2f
                    if (item.confirmedCount == 0) {
                        val minBarHeight = 4.dp.toPx()
                        drawRoundRect(
                            color = emptyBarColor,
                            topLeft = Offset(x, chartHeight - minBarHeight),
                            size = Size(barWidth, minBarHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    } else {
                        val ratio = item.confirmedCount.toFloat() / maxCount.toFloat()
                        val barHeight = (chartHeight * ratio).coerceAtLeast(8.dp.toPx())
                        val barColor = if (item.confirmedCount == maxCount) peakColor else standardColor
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, chartHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                stats.forEach { item ->
                    Text(
                        text = item.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeOfDayBreakdownCard(stats: List<TimeOfDayStat>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Time of Day Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Distribution of confirmed alarms across daily windows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            stats.forEach { item ->
                val pct = (item.fraction * 100f).roundToInt()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.bucket.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.bucket.rangeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "${item.count} ($pct%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (item.fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.fraction.coerceIn(0.03f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
