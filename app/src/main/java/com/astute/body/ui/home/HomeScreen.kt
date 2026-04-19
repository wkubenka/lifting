package com.astute.body.ui.home

import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToExerciseDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LifecycleResumeEffect(Unit) {
        if (uiState.needsSetup) {
            viewModel.onSetupComplete()
        }
        onPauseOrDispose {}
    }

    // Timer finished alert
    LaunchedEffect(uiState.timerFinished) {
        if (uiState.timerFinished) {
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
            val audioManager = context.getSystemService(AudioManager::class.java)
            if (audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.play()
            }
            viewModel.clearTimerFinished()
        }
    }

    // New PR haptic
    LaunchedEffect(uiState.newPRDetected) {
        if (uiState.newPRDetected) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.clearNewPRDetected()
        }
    }

    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.needsSetup -> {
            SetupPrompt(onNavigateToSettings = onNavigateToSettings)
        }
        else -> {
            HomeContent(
                uiState = uiState,
                onSwapExercise = { viewModel.swapExercise(it) },
                onRegenerateAll = { viewModel.regenerateAll() },
                onStartWorkout = { viewModel.startWorkout() },
                onExerciseTap = onNavigateToExerciseDetail,
                onToggleMuscleGroup = { viewModel.toggleMuscleGroup(it) },
                onClearMuscleGroups = { viewModel.clearMuscleGroupSelection() },
                onMoveExercise = { from, to -> viewModel.moveExercise(from, to) },
                onUpdateReps = { viewModel.updateReps(it) },
                onUpdateWeight = { viewModel.updateWeight(it) },
                onLogSet = { viewModel.logSet() },
                onCompleteExercise = { viewModel.completeExercise() },
                onSkipExercise = { viewModel.skipExercise() },
                onGoBack = { viewModel.goToPreviousExercise() },
                onFinishEarly = { viewModel.finishEarly() },
                onSkipTimer = { viewModel.skipTimer() },
                onExtendTimer = { viewModel.extendTimer() },
                onEditSet = { viewModel.startEditingSet(it) },
                onSaveEditedSet = { viewModel.saveEditedSet() },
                onCancelEditingSet = { viewModel.cancelEditingSet() },
                onSaveWorkout = { viewModel.saveWorkout() },
                onDiscardWorkout = { viewModel.discardWorkout() },
                onResumeWorkout = { viewModel.resumeWorkout() }
            )
        }
    }
}

@Composable
private fun SetupPrompt(onNavigateToSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome to Astute Body",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Configure your equipment to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onNavigateToSettings) {
                Text("Go to Settings")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onSwapExercise: (PlannedExercise) -> Unit,
    onRegenerateAll: () -> Unit,
    onStartWorkout: () -> Unit,
    onExerciseTap: (String) -> Unit,
    onToggleMuscleGroup: (MuscleGroup) -> Unit,
    onClearMuscleGroups: () -> Unit,
    onMoveExercise: (Int, Int) -> Unit,
    onUpdateReps: (Int) -> Unit,
    onUpdateWeight: (Double) -> Unit,
    onLogSet: () -> Unit,
    onCompleteExercise: () -> Unit,
    onSkipExercise: () -> Unit,
    onGoBack: () -> Unit,
    onFinishEarly: () -> Unit,
    onSkipTimer: () -> Unit,
    onExtendTimer: () -> Unit,
    onEditSet: (Int) -> Unit,
    onSaveEditedSet: () -> Unit,
    onCancelEditingSet: () -> Unit,
    onSaveWorkout: () -> Unit,
    onDiscardWorkout: () -> Unit,
    onResumeWorkout: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to current exercise when it advances
    if (uiState.workoutMode == WorkoutMode.ACTIVE) {
        LaunchedEffect(uiState.currentIndex) {
            // Account for the header item (index 0)
            listState.animateScrollToItem(uiState.currentIndex + 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (uiState.workoutMode) {
                WorkoutMode.PLANNING -> {
                    // Header
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today's Workout",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onRegenerateAll) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Regenerate all",
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(" Regenerate", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Muscle group filter chips
                    item {
                        val todaysMuscleGroups = uiState.flatExercises.map { it.muscleGroup }.toSet()
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MuscleGroup.entries.forEach { group ->
                                val isInTodaysWorkout = group in todaysMuscleGroups
                                FilterChip(
                                    selected = group in uiState.selectedMuscleGroups,
                                    onClick = { onToggleMuscleGroup(group) },
                                    label = { Text(group.displayName, style = MaterialTheme.typography.labelSmall) },
                                    colors = if (isInTodaysWorkout) {
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        FilterChipDefaults.filterChipColors()
                                    }
                                )
                            }
                            if (uiState.selectedMuscleGroups.isNotEmpty()) {
                                TextButton(onClick = onClearMuscleGroups) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Excluded exercises warning
                    if (uiState.hasExcludedInPlan) {
                        item {
                            Card(
                                onClick = onRegenerateAll,
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Workout contains excluded exercises. Tap to regenerate.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Exercise cards
                    itemsIndexed(uiState.flatExercises, key = { _, it -> it.exercise.id }) { index, exercise ->
                        PlanningExerciseCard(
                            exercise = exercise,
                            isFavorited = exercise.exercise.id in uiState.favoritedIds,
                            onSwap = { onSwapExercise(exercise) },
                            onTap = { onExerciseTap(exercise.exercise.id) },
                            onMoveUp = if (index > 0) ({ onMoveExercise(index, index - 1) }) else null,
                            onMoveDown = if (index < uiState.flatExercises.size - 1) ({ onMoveExercise(index, index + 1) }) else null
                        )
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }

                WorkoutMode.ACTIVE -> {
                    // Active workout header
                    item {
                        WorkoutActiveHeader(
                            startTimeMillis = uiState.startTimeMillis,
                            currentIndex = uiState.currentIndex,
                            totalExercises = uiState.flatExercises.size
                        )
                    }

                    // Exercise cards with three states
                    itemsIndexed(uiState.flatExercises, key = { _, it -> it.exercise.id }) { index, exercise ->
                        when {
                            index < uiState.currentIndex -> {
                                val logEntry = uiState.logEntries.find { it.exerciseId == exercise.exercise.id }
                                CompletedExerciseCard(
                                    exercise = exercise,
                                    logEntry = logEntry,
                                    weightUnit = uiState.weightUnit
                                )
                            }
                            index == uiState.currentIndex -> {
                                CurrentExerciseCard(
                                    exercise = exercise,
                                    uiState = uiState,
                                    onExerciseTap = { onExerciseTap(exercise.exercise.id) },
                                    onSwapExercise = { onSwapExercise(exercise) },
                                    onUpdateReps = onUpdateReps,
                                    onUpdateWeight = onUpdateWeight,
                                    onLogSet = onLogSet,
                                    onCompleteExercise = onCompleteExercise,
                                    onSkipExercise = onSkipExercise,
                                    onGoBack = onGoBack,
                                    onSkipTimer = onSkipTimer,
                                    onExtendTimer = onExtendTimer,
                                    onEditSet = onEditSet,
                                    onSaveEditedSet = onSaveEditedSet,
                                    onCancelEditingSet = onCancelEditingSet
                                )
                            }
                            else -> {
                                UpcomingExerciseCard(
                                    exercise = exercise,
                                    index = index,
                                    currentIndex = uiState.currentIndex,
                                    totalExercises = uiState.flatExercises.size,
                                    onMoveUp = if (index > uiState.currentIndex + 1) ({ onMoveExercise(index, index - 1) }) else null,
                                    onMoveDown = if (index < uiState.flatExercises.size - 1) ({ onMoveExercise(index, index + 1) }) else null,
                                    onSwapExercise = { onSwapExercise(exercise) }
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }

                WorkoutMode.COMPLETE -> {
                    item {
                        InlineWorkoutSummary(
                            uiState = uiState,
                            onSave = onSaveWorkout,
                            onDiscard = onDiscardWorkout,
                            onResume = onResumeWorkout
                        )
                    }
                }
            }
        }

        // Bottom button
        when (uiState.workoutMode) {
            WorkoutMode.PLANNING -> {
                if (uiState.workoutPlan != null) {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Start Workout")
                    }
                }
            }
            WorkoutMode.ACTIVE -> {
                TextButton(
                    onClick = onFinishEarly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val lastExercise = uiState.flatExercises.lastOrNull()
                    val isOnLastExercise = uiState.currentIndex == uiState.flatExercises.size - 1
                    val lastHasSets = lastExercise != null && (
                        uiState.allExerciseSets[lastExercise.exercise.id]?.isNotEmpty() == true ||
                        (isOnLastExercise && uiState.currentExerciseSets.isNotEmpty())
                    )
                    Text(if (lastHasSets) "Finish" else "Finish Early")
                }
            }
            WorkoutMode.COMPLETE -> { /* Save/Discard are inline in the summary */ }
        }
    }
}
