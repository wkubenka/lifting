package com.astute.body.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun InlineWorkoutSummary(
    uiState: HomeUiState,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val totalSets = uiState.logEntries.sumOf { it.sets }
    val totalVolume = uiState.logEntries.sumOf { it.sets * it.reps * it.weight * it.volumeMultiplier }
    val durationMinutes = ((System.currentTimeMillis() - uiState.startTimeMillis) / 60000).toInt()
    val volumeByGroup = uiState.logEntries
        .groupBy { it.muscleGroup }
        .mapValues { (_, logs) -> logs.sumOf { it.sets * it.reps * it.weight * it.volumeMultiplier } }

    Column(Modifier.padding(top = 16.dp)) {
        Text(
            text = "Workout Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        SummaryRow("Exercises", "${uiState.logEntries.distinctBy { it.exerciseId }.size}")
        SummaryRow("Total Sets", "$totalSets")
        SummaryRow("Total Volume", "${totalVolume.toLong()} ${uiState.weightUnit}")
        SummaryRow("Duration", "$durationMinutes min")

        // New PRs
        if (uiState.newPRs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "New personal records",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "New Personal Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))

            uiState.newPRs.forEach { pr ->
                val detail = when (pr.type) {
                    PRType.WEIGHT -> "${formatWeight(pr.oldValue)} \u2192 ${formatWeight(pr.newValue)} ${uiState.weightUnit}"
                    PRType.REPS -> "${pr.oldValue.toInt()} \u2192 ${pr.newValue.toInt()} reps"
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = pr.exerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Volume by muscle group
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Volume by Muscle Group",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        volumeByGroup.forEach { (group, volume) ->
            SummaryRow(group, "${volume.toLong()} ${uiState.weightUnit}")
        }

        // Per-exercise detail
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Exercise Detail",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        uiState.logEntries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${entry.sets} \u00d7 ${entry.reps} @ ${formatWeight(entry.weight)} ${uiState.weightUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Workout")
        }

        Spacer(Modifier.height(8.dp))

        var showDiscardConfirm by remember { mutableStateOf(false) }

        TextButton(
            onClick = { showDiscardConfirm = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Discard")
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text("Discard Workout?") },
                text = { Text("This workout will not be saved. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        onDiscard()
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
