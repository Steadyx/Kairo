package com.kairo.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.ReadingMomentum
import com.kairo.reader.core.model.ReadingMomentumDay
import com.kairo.reader.core.model.ReadingSessionItem
import com.kairo.reader.core.model.ReadingSessionMode
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun LibraryMomentumContent(
    momentum: ReadingMomentum,
    weeklyGoalMinutes: Int,
    onWeeklyGoalChange: (Int) -> Unit,
    onResetMomentum: () -> Unit = {},
) {
    val weekMinutes = (momentum.weekDurationMs / MILLIS_PER_MINUTE).toInt()
    val visibleSessions = visibleMomentumSessions(momentum.sessions)
    var previousWeeksExpanded by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val goalProgress =
        (weekMinutes.toFloat() / weeklyGoalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "momentum_summary") {
            MomentumSummaryCard(momentum, weekMinutes, weeklyGoalMinutes, goalProgress)
        }
        item(key = "momentum_goal") {
            MomentumGoalSelector(weeklyGoalMinutes, onWeeklyGoalChange)
        }
        item(key = "momentum_days") {
            MomentumConsistencyCard(momentum)
        }
        item(key = "momentum_profile") {
            MomentumProfileCard(momentum)
        }
        item(key = "momentum_history_header") {
            MomentumHistoryHeader(
                previousWeekCount = momentum.previousWeeks.size,
                expanded = previousWeeksExpanded,
                onToggle = { previousWeeksExpanded = !previousWeeksExpanded },
                onReset = { showResetConfirmation = true },
            )
        }
        if (previousWeeksExpanded) {
            items(
                items = momentum.previousWeeks,
                key = { "momentum_week_${it.startedAt}" },
            ) { week ->
                MomentumPreviousWeekRow(week)
            }
        }
        item(key = "momentum_recent_title") {
            MomentumRecentSessionsHeader(momentum.sessions.size, visibleSessions.size)
        }
        if (momentum.sessions.isEmpty()) {
            item(key = "momentum_empty") {
                Text(
                    text = stringResource(R.string.momentum_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(visibleSessions, key = { it.session.id }) { item ->
                MomentumSessionRow(item)
            }
        }
    }
    if (showResetConfirmation) {
        MomentumResetConfirmationDialog(
            onConfirm = {
                onResetMomentum()
                showResetConfirmation = false
            },
            onDismiss = { showResetConfirmation = false },
        )
    }
}

@Composable
private fun MomentumSummaryCard(
    momentum: ReadingMomentum,
    weekMinutes: Int,
    weeklyGoalMinutes: Int,
    goalProgress: Float,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.momentum_this_week),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MomentumMetric(stringResource(R.string.momentum_minutes_read, weekMinutes))
                MomentumMetric(
                    stringResource(
                        R.string.momentum_words_read,
                        NumberFormat.getIntegerInstance().format(momentum.weekWordsRead),
                    ),
                )
            }
            LinearProgressIndicator(
                progress = { goalProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text =
                stringResource(
                    R.string.momentum_goal_progress,
                    weekMinutes,
                    weeklyGoalMinutes,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.momentum_stored_locally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun MomentumMetric(value: String) {
    Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MomentumConsistencyCard(momentum: ReadingMomentum) {
    val days = remember(momentum.dailyActivity) { momentumDaysForDisplay(momentum.dailyActivity) }
    val todayIndex = remember(days, momentum.todayStartedAt) {
        momentumTodayDayIndex(days, momentum.todayStartedAt)
    }
    var selectedDayIndex by remember(days, todayIndex) { mutableIntStateOf(todayIndex) }
    val selectedDay = days[selectedDayIndex.coerceIn(days.indices)]
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.momentum_consistency_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.momentum_consistency, momentum.activeDaysInLastSeven),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MomentumDayBars(
                days = days,
                selectedDayIndex = selectedDayIndex,
                onDaySelected = { selectedDayIndex = it },
            )
            MomentumDaySummary(
                day = selectedDay,
                isToday = selectedDayIndex == todayIndex,
            )
        }
    }
}

@Composable
private fun MomentumDayBars(
    days: List<ReadingMomentumDay>,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
) {
    val maxDuration = days.maxOfOrNull { it.activeDurationMs }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(DAY_CHART_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEachIndexed { index, day ->
            MomentumDayBar(
                day = day,
                isSelected = index == selectedDayIndex,
                maxDuration = maxDuration,
                onClick = { onDaySelected(index) },
                modifier =
                Modifier
                    .weight(1f)
                    .height(DAY_CHART_HEIGHT_DP.dp),
            )
        }
    }
}

@Composable
private fun MomentumDayBar(
    day: ReadingMomentumDay,
    isSelected: Boolean,
    maxDuration: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = day.activeDurationMs
    val dayLabel = remember(day.startedAt) { shortDayLabel(day.startedAt) }
    val durationLabel = momentumDurationText(duration)
    val description = stringResource(R.string.momentum_day_bar_description, dayLabel, durationLabel)
    val barHeight = momentumBarHeight(duration, maxDuration)
    Column(
        modifier =
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ).clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.RadioButton
                selected = isSelected
            }.padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                Modifier
                    .width(DAY_BAR_WIDTH_DP.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            duration == 0L -> MaterialTheme.colorScheme.outlineVariant
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = UNSELECTED_BAR_ALPHA)
                        },
                    ),
            )
        }
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            color =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun MomentumDaySummary(
    day: ReadingMomentumDay,
    isToday: Boolean,
) {
    val dayLabel =
        if (isToday) {
            stringResource(R.string.momentum_today)
        } else {
            remember(day.startedAt) { fullDayLabel(day.startedAt) }
        }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.momentum_total_reading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = momentumDurationText(day.activeDurationMs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                    stringResource(
                        R.string.momentum_words_read,
                        NumberFormat.getIntegerInstance().format(day.wordsRead),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                    pluralStringResource(
                        R.plurals.momentum_sessions_count,
                        day.sessionCount,
                        day.sessionCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MomentumRecentSessionsHeader(
    totalSessions: Int,
    visibleSessions: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.momentum_recent_sessions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (totalSessions > visibleSessions) {
            Text(
                text = stringResource(R.string.momentum_recent_sessions_limit, visibleSessions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun momentumDurationText(durationMs: Long): String {
    val value = momentumDurationValue(durationMs)
    return when {
        value.isLessThanMinute -> stringResource(R.string.momentum_duration_less_than_minute)
        value.hours == 0 -> stringResource(R.string.momentum_duration_minutes, value.minutes)
        value.minutes == 0 -> stringResource(R.string.momentum_duration_hours, value.hours)
        else -> stringResource(R.string.momentum_duration_hours_minutes, value.hours, value.minutes)
    }
}

private fun momentumBarHeight(
    durationMs: Long,
    maxDurationMs: Long,
): Int {
    if (durationMs == 0L) return INACTIVE_BAR_HEIGHT_DP
    val fraction = durationMs.toFloat() / maxDurationMs.toFloat()
    return (MIN_ACTIVE_BAR_HEIGHT_DP + (ACTIVE_BAR_HEIGHT_RANGE_DP * fraction)).roundToInt()
}

private fun shortDayLabel(timestamp: Long): String =
    SimpleDateFormat(SHORT_DAY_PATTERN, Locale.getDefault()).format(Date(timestamp))

private fun fullDayLabel(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

@Composable
private fun MomentumProfileCard(momentum: ReadingMomentum) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.momentum_reading_profile),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            MomentumProfileRow(
                label = stringResource(R.string.momentum_comfortable_pace),
                value =
                momentum.averageEffectiveWpm?.let {
                    stringResource(R.string.momentum_pace_value, it)
                } ?: stringResource(R.string.momentum_not_available),
            )
            MomentumProfileRow(
                label = stringResource(R.string.momentum_preferred_mode),
                value = momentum.preferredMode?.displayName() ?: stringResource(R.string.momentum_not_available),
            )
        }
    }
}

@Composable
private fun MomentumProfileRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MomentumSessionRow(item: ReadingSessionItem) {
    val session = item.session
    val minutes = (session.activeDurationMs / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
    val wordText = NumberFormat.getIntegerInstance().format(session.wordsRead)
    val estimatedSuffix = if (session.isWordCountEstimated) stringResource(R.string.momentum_estimated_suffix) else ""
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.book.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = sessionDateTimeLabel(session.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text =
                stringResource(
                    R.string.momentum_session_summary,
                    minutes,
                    wordText + estimatedSuffix,
                    session.mode.displayName(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun sessionDateTimeLabel(timestamp: Long): String =
    DateFormat
        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestamp))

@Composable
private fun ReadingSessionMode.displayName(): String =
    when (this) {
        ReadingSessionMode.READER -> stringResource(R.string.reading_mode_reader)
        ReadingSessionMode.RSVP -> stringResource(R.string.reading_mode_rsvp)
        ReadingSessionMode.BIONIC -> stringResource(R.string.reading_mode_bionic)
    }

private const val MIN_ACTIVE_BAR_HEIGHT_DP = 12f
private const val ACTIVE_BAR_HEIGHT_RANGE_DP = 42f
private const val INACTIVE_BAR_HEIGHT_DP = 6
private const val DAY_CHART_HEIGHT_DP = 96
private const val DAY_BAR_WIDTH_DP = 16
private const val UNSELECTED_BAR_ALPHA = 0.48f
private const val SHORT_DAY_PATTERN = "EEE"
private const val MILLIS_PER_MINUTE = 60_000L
