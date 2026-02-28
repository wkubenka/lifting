package com.astute.body.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ActiveWorkoutScreen(
    onWorkoutComplete: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isSaved -> {
            onWorkoutComplete()
        }
        uiState.isComplete -> {
            WorkoutSummary(
                uiState = uiState,
                onSave = { viewModel.saveWorkout() },
                onDiscard = onWorkoutComplete
            )
        }
        uiState.exercises.isNotEmpty() -> {
            ExerciseLoggingScreen(
                uiState = uiState,
                onUpdateSets = { viewModel.updateSets(it) },
                onUpdateReps = { viewModel.updateReps(it) },
                onUpdateWeight = { viewModel.updateWeight(it) },
                onLogSet = { viewModel.logSet() },
                onCompleteExercise = { viewModel.completeExercise() },
                onSkipExercise = { viewModel.skipExercise() },
                onFinishEarly = { viewModel.finishEarly() },
                onSkipTimer = { viewModel.skipTimer() },
                onExtendTimer = { viewModel.extendTimer() }
            )
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workout loaded")
            }
        }
    }
}

@Composable
private fun ExerciseLoggingScreen(
    uiState: WorkoutUiState,
    onUpdateSets: (Int) -> Unit,
    onUpdateReps: (Int) -> Unit,
    onUpdateWeight: (Double) -> Unit,
    onLogSet: () -> Unit,
    onCompleteExercise: () -> Unit,
    onSkipExercise: () -> Unit,
    onFinishEarly: () -> Unit,
    onSkipTimer: () -> Unit,
    onExtendTimer: () -> Unit
) {
    val current = uiState.exercises[uiState.currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Progress
        LinearProgressIndicator(
            progress = { (uiState.currentIndex + 1).toFloat() / uiState.exercises.size },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Exercise ${uiState.currentIndex + 1} of ${uiState.exercises.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Exercise info
        Text(
            text = current.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${current.muscleGroup.displayName} \u2022 ${current.exercise.mechanic ?: ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Previous performance
        if (uiState.previousWeight != null && uiState.previousReps != null) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Last: ${uiState.previousReps} reps @ ${uiState.previousWeight} lbs",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Sets completed indicator
        if (uiState.setsCompleted > 0) {
            Text(
                text = "Sets completed: ${uiState.setsCompleted}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
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

        // Input fields (hidden during timer)
        AnimatedVisibility(visible = !uiState.timerRunning) {
            Column {
                // Reps stepper
                StepperRow(
                    label = "Reps",
                    value = uiState.currentReps,
                    onDecrement = { onUpdateReps(uiState.currentReps - 1) },
                    onIncrement = { onUpdateReps(uiState.currentReps + 1) }
                )

                Spacer(Modifier.height(12.dp))

                // Weight input
                OutlinedTextField(
                    value = if (uiState.currentWeight == 0.0) "" else uiState.currentWeight.toBigDecimal().stripTrailingZeros().toPlainString(),
                    onValueChange = { text ->
                        onUpdateWeight(text.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Weight (lbs)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Action buttons
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
            }
        }

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onFinishEarly,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Finish Early")
        }
    }
}

@Composable
private fun StepperRow(
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
private fun RestTimerCard(
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

@Composable
private fun WorkoutSummary(
    uiState: WorkoutUiState,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val totalSets = uiState.logEntries.sumOf { it.sets }
    val totalVolume = uiState.logEntries.sumOf { it.sets * it.reps * it.weight }
    val durationMinutes = ((System.currentTimeMillis() - uiState.startTimeMillis) / 60000).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Workout Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        SummaryRow("Exercises", "${uiState.logEntries.size}")
        SummaryRow("Total Sets", "$totalSets")
        SummaryRow("Total Volume", "${totalVolume.toLong()} lbs")
        SummaryRow("Duration", "$durationMinutes min")

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Workout")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onDiscard) {
            Text("Discard")
        }
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
