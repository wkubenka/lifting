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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveWorkoutScreen(
    onWorkoutComplete: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(uiState.timerFinished) {
        if (uiState.timerFinished) {
            // Vibrate
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1)
            )

            // Play notification sound (respect ringer mode)
            val audioManager = context.getSystemService(AudioManager::class.java)
            if (audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.play()
            }

            viewModel.clearTimerFinished()
        }
    }

    LaunchedEffect(uiState.newPRDetected) {
        if (uiState.newPRDetected) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.clearNewPRDetected()
        }
    }

    when {
        uiState.isSaved -> {
            onWorkoutComplete()
        }
        uiState.isComplete -> {
            WorkoutSummary(
                uiState = uiState,
                onSave = { viewModel.saveWorkout() },
                onDiscard = {
                    viewModel.discardWorkout()
                    onWorkoutComplete()
                }
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
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

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

        // Previous performance and PR display
        Spacer(Modifier.height(8.dp))
        PerformanceCard(
            performance = uiState.previousPerformance,
            weightUnit = uiState.weightUnit,
            dateFormat = dateFormat
        )

        // New PR indicator for current exercise
        val currentPRs = uiState.newPRs.filter { it.exerciseId == current.exercise.id }
        if (currentPRs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            NewPRBadge(prs = currentPRs, weightUnit = uiState.weightUnit)
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
                    label = { Text("Weight (${uiState.weightUnit})") },
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
private fun PerformanceCard(
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
private fun NewPRBadge(prs: List<NewPR>, weightUnit: String) {
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
    val volumeByGroup = uiState.logEntries
        .groupBy { it.muscleGroup }
        .mapValues { (_, logs) -> logs.sumOf { it.sets * it.reps * it.weight } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Workout Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        // Overview stats
        SummaryRow("Exercises", "${uiState.logEntries.size}")
        SummaryRow("Total Sets", "$totalSets")
        SummaryRow("Total Volume", "${totalVolume.toLong()} ${uiState.weightUnit}")
        SummaryRow("Duration", "$durationMinutes min")

        // New PRs section
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
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
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
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
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

        TextButton(
            onClick = onDiscard,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
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

private fun formatWeight(weight: Double): String {
    return weight.toBigDecimal().stripTrailingZeros().toPlainString()
}
