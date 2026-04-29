package com.astute.body.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astute.body.domain.model.PlannedExercise
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WorkoutActiveHeader(
    startTimeMillis: Long,
    currentIndex: Int,
    totalExercises: Int
) {
    var elapsedMinutes by remember { mutableIntStateOf(0) }
    LaunchedEffect(startTimeMillis) {
        while (true) {
            elapsedMinutes = ((System.currentTimeMillis() - startTimeMillis) / 60000).toInt()
            delay(60_000)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Workout in Progress",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "${elapsedMinutes} min",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${currentIndex + 1} / $totalExercises exercises",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / totalExercises },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun CompletedExerciseCard(
    exercise: PlannedExercise,
    logEntry: ExerciseLogEntry?,
    weightUnit: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = exercise.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (logEntry != null) {
                    Text(
                        text = "${logEntry.sets}\u00d7${logEntry.reps} @ ${formatWeight(logEntry.weight)} $weightUnit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun CurrentExerciseCard(
    exercise: PlannedExercise,
    uiState: HomeUiState,
    onExerciseTap: () -> Unit,
    onSwapExercise: () -> Unit,
    onUpdateReps: (Int) -> Unit,
    onUpdateWeight: (Double) -> Unit,
    onLogSet: () -> Unit,
    onCompleteExercise: () -> Unit,
    onSkipExercise: () -> Unit,
    onGoBack: () -> Unit,
    onSkipTimer: () -> Unit,
    onExtendTimer: () -> Unit,
    onEditSet: (Int) -> Unit,
    onSaveEditedSet: () -> Unit,
    onCancelEditingSet: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // Exercise name + info button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = exercise.exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${exercise.muscleGroup.displayName} \u2022 ${exercise.exercise.mechanic ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSwapExercise) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Swap exercise",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onExerciseTap) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "View exercise instructions",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Previous performance
            Spacer(Modifier.height(8.dp))
            PerformanceCard(
                performance = uiState.previousPerformance,
                weightUnit = uiState.weightUnit,
                dateFormat = dateFormat
            )

            // New PR badge for current exercise
            val currentPRs = uiState.newPRs.filter { it.exerciseId == exercise.exercise.id }
            if (currentPRs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                NewPRBadge(prs = currentPRs, weightUnit = uiState.weightUnit)
            }

            Spacer(Modifier.height(16.dp))

            // Logged sets
            if (uiState.currentExerciseSets.isNotEmpty()) {
                Text(
                    text = "Sets completed: ${uiState.currentExerciseSets.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                uiState.currentExerciseSets.forEachIndexed { index, set ->
                    if (uiState.editingSetIndex == index) {
                        EditingSetCard(
                            index = index,
                            currentReps = uiState.currentReps,
                            currentWeight = uiState.currentWeight,
                            weightUnit = uiState.weightUnit,
                            onUpdateReps = onUpdateReps,
                            onUpdateWeight = onUpdateWeight,
                            onSave = onSaveEditedSet,
                            onCancel = onCancelEditingSet
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Set ${index + 1}: ${set.reps} reps @ ${formatWeight(set.weight)} ${uiState.weightUnit}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { onEditSet(index) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit set", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Rest timer
            AnimatedVisibility(visible = uiState.timerRunning) {
                RestTimerCard(
                    seconds = uiState.timerSeconds,
                    total = uiState.timerTotal,
                    onSkip = onSkipTimer,
                    onExtend = onExtendTimer
                )
            }

            // Input fields (hidden during timer and editing)
            AnimatedVisibility(visible = !uiState.timerRunning && uiState.editingSetIndex == null) {
                Column {
                    StepperRow(
                        label = "Reps",
                        value = uiState.currentReps,
                        onDecrement = { onUpdateReps(uiState.currentReps - 1) },
                        onIncrement = { onUpdateReps(uiState.currentReps + 1) }
                    )

                    Spacer(Modifier.height(12.dp))

                    WeightTextField(
                        weight = uiState.currentWeight,
                        onWeightChange = onUpdateWeight,
                        label = { Text("Weight (${uiState.weightUnit})") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onLogSet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Log Set")
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onCompleteExercise,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Complete Exercise")
                        }
                        OutlinedButton(
                            onClick = onSkipExercise,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Skip")
                        }
                    }

                    if (uiState.currentIndex > 0) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = onGoBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Previous Exercise")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditingSetCard(
    index: Int,
    currentReps: Int,
    currentWeight: Double,
    weightUnit: String,
    onUpdateReps: (Int) -> Unit,
    onUpdateWeight: (Double) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Set ${index + 1}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentReps.toString(),
                    onValueChange = { text ->
                        onUpdateReps(text.toIntOrNull() ?: 1)
                    },
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                WeightTextField(
                    weight = currentWeight,
                    onWeightChange = onUpdateWeight,
                    label = { Text(weightUnit) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun UpcomingExerciseCard(
    exercise: PlannedExercise,
    index: Int,
    currentIndex: Int,
    totalExercises: Int,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onSwapExercise: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = exercise.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(exercise.muscleGroup.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            IconButton(onClick = onSwapExercise) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap exercise")
            }
        }
    }
}

// --- Shared composables ---

@Composable
internal fun PerformanceCard(
    performance: ExercisePerformance?,
    weightUnit: String,
    dateFormat: SimpleDateFormat
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (performance != null) {
                if (performance.lastWeight != null && performance.lastReps != null) {
                    Text(
                        text = "Last: ${performance.lastReps} reps @ ${formatWeight(performance.lastWeight)} $weightUnit",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                val prParts = mutableListOf<String>()
                if (performance.maxWeight > 0.0) {
                    val datePart = performance.maxWeightDate?.let { " (${dateFormat.format(Date(it))})" } ?: ""
                    prParts.add("${formatWeight(performance.maxWeight)} $weightUnit$datePart")
                }
                if (performance.maxReps > 0) {
                    val datePart = performance.maxRepsDate?.let { " (${dateFormat.format(Date(it))})" } ?: ""
                    prParts.add("${performance.maxReps} reps$datePart")
                }
                if (prParts.isNotEmpty()) {
                    Text(
                        text = "PR: ${prParts.joinToString(" \u00b7 ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                Text(
                    text = "First time \u2014 no previous data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
internal fun NewPRBadge(prs: List<NewPR>, weightUnit: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = "New personal record",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                prs.forEach { pr ->
                    val label = when (pr.type) {
                        PRType.WEIGHT -> "Weight: ${formatWeight(pr.oldValue)} \u2192 ${formatWeight(pr.newValue)} $weightUnit"
                        PRType.REPS -> "Reps: ${pr.oldValue.toInt()} \u2192 ${pr.newValue.toInt()}"
                    }
                    Text(
                        text = "NEW PR! $label",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun WeightTextField(
    weight: Double,
    onWeightChange: (Double) -> Unit,
    label: @Composable (() -> Unit),
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(if (weight == 0.0) "" else formatWeight(weight)) }

    LaunchedEffect(weight) {
        val textAsDouble = text.toDoubleOrNull() ?: 0.0
        if (textAsDouble != weight) {
            text = if (weight == 0.0) "" else formatWeight(weight)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onWeightChange(it.toDoubleOrNull() ?: 0.0)
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        singleLine = true
    )
}

@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onIncrement) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}

@Composable
internal fun RestTimerCard(
    seconds: Int,
    total: Int,
    onSkip: () -> Unit,
    onExtend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Rest", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (total > 0) seconds.toFloat() / total else 0f },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp
                )
                Text(
                    text = "${seconds}s",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSkip) {
                    Text("Skip")
                }
                FilledTonalButton(onClick = onExtend) {
                    Text("+15s")
                }
            }
        }
    }
}
