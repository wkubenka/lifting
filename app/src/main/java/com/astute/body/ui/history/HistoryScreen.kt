package com.astute.body.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onNavigateToExerciseDetail: (String) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showDeleteLogConfirm != null) {
        val logId = uiState.showDeleteLogConfirm!!
        val log = uiState.selectedSessionLogs.find { it.logId == logId }
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteLog() },
            title = { Text("Delete Exercise Log?") },
            text = { Text("This cannot be undone. Personal records will be recalculated.") },
            confirmButton = {
                TextButton(onClick = {
                    if (log != null) {
                        viewModel.deleteLog(logId, log.exerciseId)
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteLog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Workout History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...")
                }
            }
            uiState.sessions.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No workouts yet.\nComplete a workout to see it here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.sessions, key = { it.sessionId }) { session ->
                        SessionCard(
                            session = session,
                            isExpanded = uiState.selectedSessionId == session.sessionId,
                            logs = if (uiState.selectedSessionId == session.sessionId) uiState.selectedSessionLogs else emptyList(),
                            editingLog = uiState.editingLog,
                            weightUnit = uiState.weightUnit,
                            volumeMultipliers = uiState.volumeMultipliers,
                            onToggle = { viewModel.selectSession(session.sessionId) },
                            onDelete = { viewModel.deleteSession(session.sessionId) },
                            onExerciseTap = onNavigateToExerciseDetail,
                            onEditLog = { viewModel.startEditingLog(it) },
                            onDeleteLog = { viewModel.confirmDeleteLog(it.logId) },
                            onSaveEdit = { log, sets, reps, weight ->
                                viewModel.updateLog(log, sets, reps, weight)
                            },
                            onCancelEdit = { viewModel.cancelEdit() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    session: WorkoutSessionEntity,
    isExpanded: Boolean,
    logs: List<ExerciseLogEntity>,
    editingLog: ExerciseLogEntity?,
    weightUnit: String,
    volumeMultipliers: Map<String, Int>,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onExerciseTap: (String) -> Unit,
    onEditLog: (ExerciseLogEntity) -> Unit,
    onDeleteLog: (ExerciseLogEntity) -> Unit,
    onSaveEdit: (ExerciseLogEntity, Int, Int, Double) -> Unit,
    onCancelEdit: () -> Unit
) {
    val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    val totalExercises = if (isExpanded) logs.size else 0
    val totalVolume = if (isExpanded) logs.sumOf { it.sets * it.reps * it.weight * (volumeMultipliers[it.exerciseId] ?: 1) } else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = dateFormat.format(Date(session.date)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                session.muscleGroups.forEach { group ->
                    AssistChip(
                        onClick = {},
                        label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && logs.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "$totalExercises exercises \u2022 ${totalVolume.toLong()} $weightUnit total volume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    logs.forEach { log ->
                        if (editingLog?.logId == log.logId) {
                            EditableExerciseLogRow(
                                log = log,
                                weightUnit = weightUnit,
                                onSave = { sets, reps, weight -> onSaveEdit(log, sets, reps, weight) },
                                onCancel = onCancelEdit
                            )
                        } else {
                            ExerciseLogRow(
                                log = log,
                                weightUnit = weightUnit,
                                onTap = { onExerciseTap(log.exerciseId) },
                                onEdit = { onEditLog(log) },
                                onDelete = { onDeleteLog(log) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete session",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseLogRow(
    log: ExerciseLogEntity,
    weightUnit: String,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = log.exerciseId.replace("_", " "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${log.sets} \u00d7 ${log.reps} @ ${log.weight.toBigDecimal().stripTrailingZeros().toPlainString()} $weightUnit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit log",
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete log",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EditableExerciseLogRow(
    log: ExerciseLogEntity,
    weightUnit: String,
    onSave: (Int, Int, Double) -> Unit,
    onCancel: () -> Unit
) {
    var sets by remember { mutableStateOf(log.sets.toString()) }
    var reps by remember { mutableStateOf(log.reps.toString()) }
    var weight by remember { mutableStateOf(log.weight.toBigDecimal().stripTrailingZeros().toPlainString()) }

    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = log.exerciseId.replace("_", " "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = sets,
                onValueChange = { sets = it },
                label = { Text("Sets") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                label = { Text("Reps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(weightUnit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            IconButton(
                onClick = {
                    val s = sets.toIntOrNull() ?: return@IconButton
                    val r = reps.toIntOrNull() ?: return@IconButton
                    val w = weight.toDoubleOrNull() ?: return@IconButton
                    onSave(s, r, w)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
            }
        }
    }
}
