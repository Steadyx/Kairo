package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.data.preferences.MAX_WEEKLY_READING_GOAL_MINUTES
import com.kairo.reader.data.preferences.MIN_WEEKLY_READING_GOAL_MINUTES

@Composable
internal fun MomentumGoalSelector(
    weeklyGoalMinutes: Int,
    onWeeklyGoalChange: (Int) -> Unit,
) {
    var showCustomGoalDialog by rememberSaveable { mutableStateOf(false) }
    val isCustomGoal = weeklyGoalMinutes !in WEEKLY_GOAL_OPTIONS

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.momentum_goal_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WEEKLY_GOAL_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = weeklyGoalMinutes == minutes,
                    onClick = { onWeeklyGoalChange(minutes) },
                    label = { Text(stringResource(R.string.momentum_minutes_read, minutes)) },
                )
            }
            FilterChip(
                selected = isCustomGoal,
                onClick = { showCustomGoalDialog = true },
                label = {
                    Text(
                        if (isCustomGoal) {
                            stringResource(R.string.momentum_custom_goal_value, weeklyGoalMinutes)
                        } else {
                            stringResource(R.string.momentum_custom_goal)
                        },
                    )
                },
            )
        }
    }

    if (showCustomGoalDialog) {
        MomentumCustomGoalDialog(
            currentGoalMinutes = weeklyGoalMinutes,
            onSave = { minutes ->
                onWeeklyGoalChange(minutes)
                showCustomGoalDialog = false
            },
            onDismiss = { showCustomGoalDialog = false },
        )
    }
}

@Composable
private fun MomentumCustomGoalDialog(
    currentGoalMinutes: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable(currentGoalMinutes) { mutableStateOf(currentGoalMinutes.toString()) }
    var saveAttempted by rememberSaveable { mutableStateOf(false) }
    val validGoal = validatedWeeklyGoalMinutes(input)
    val showError = validGoal == null && (input.isNotEmpty() || saveAttempted)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.momentum_custom_goal_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    if (value.all(Char::isDigit)) {
                        input = value
                        saveAttempted = false
                    }
                },
                label = { Text(stringResource(R.string.momentum_custom_goal_field)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (showError) {
                                R.string.momentum_custom_goal_error
                            } else {
                                R.string.momentum_custom_goal_range
                            },
                            MIN_WEEKLY_READING_GOAL_MINUTES,
                            MAX_WEEKLY_READING_GOAL_MINUTES,
                        ),
                    )
                },
                isError = showError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validGoal == null) {
                        saveAttempted = true
                    } else {
                        onSave(validGoal)
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private const val SHORT_WEEKLY_GOAL_MINUTES = 60
private const val STANDARD_WEEKLY_GOAL_MINUTES = 120
private const val EXTENDED_WEEKLY_GOAL_MINUTES = 180
private val WEEKLY_GOAL_OPTIONS =
    listOf(
        SHORT_WEEKLY_GOAL_MINUTES,
        STANDARD_WEEKLY_GOAL_MINUTES,
        EXTENDED_WEEKLY_GOAL_MINUTES,
    )
