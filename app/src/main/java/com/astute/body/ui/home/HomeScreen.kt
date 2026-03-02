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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                onDiscardWorkout = { viewModel.discardWorkout() }
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
    onDiscardWorkout: () -> Unit
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
                                    onMoveDown = if (index < uiState.flatExercises.size - 1) ({ onMoveExercise(index, index + 1) }) else null
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
                            onDiscard = onDiscardWorkout
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
                    Text("Finish Early")
                }
            }
            WorkoutMode.COMPLETE -> { /* Save/Discard are inline in the summary */ }
        }
    }
}

// --- Planning mode card ---

@Composable
private fun PlanningExerciseCard(
    exercise: PlannedExercise,
    isFavorited: Boolean,
    onSwap: () -> Unit,
    onTap: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Card(
        onClick = onTap,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = exercise.exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (isFavorited) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Favorited",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(exercise.muscleGroup.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                    exercise.exercise.equipment?.let { equip ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(equip, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    exercise.exercise.mechanic?.let { mech ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(mech, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            IconButton(onClick = onSwap) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap exercise")
            }
        }
    }
}

// --- Active workout header ---

@Composable
private fun WorkoutActiveHeader(
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

// --- Completed exercise card ---

@Composable
private fun CompletedExerciseCard(
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

// --- Current (expanded) exercise card ---

@Composable
private fun CurrentExerciseCard(
    exercise: PlannedExercise,
    uiState: HomeUiState,
    onExerciseTap: () -> Unit,
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
                                    IconButton(onClick = onSaveEditedSet, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = onCancelEditingSet, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StepperRow(
                                        label = "Reps",
                                        value = uiState.currentReps,
                                        onDecrement = { onUpdateReps(uiState.currentReps - 1) },
                                        onIncrement = { onUpdateReps(uiState.currentReps + 1) }
                                    )
                                    OutlinedTextField(
                                        value = if (uiState.currentWeight == 0.0) "" else uiState.currentWeight.toBigDecimal().stripTrailingZeros().toPlainString(),
                                        onValueChange = { text ->
                                            onUpdateWeight(text.toDoubleOrNull() ?: 0.0)
                                        },
                                        label = { Text(uiState.weightUnit) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(100.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }
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

// --- Upcoming exercise card ---

@Composable
private fun UpcomingExerciseCard(
    exercise: PlannedExercise,
    index: Int,
    currentIndex: Int,
    totalExercises: Int,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
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
                    exercise.exercise.equipment?.let { equip ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(equip, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

// --- Shared composables ---

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

// --- Workout summary ---

@Composable
private fun InlineWorkoutSummary(
    uiState: HomeUiState,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val totalSets = uiState.logEntries.sumOf { it.sets }
    val totalVolume = uiState.logEntries.sumOf { it.sets * it.reps * it.weight }
    val durationMinutes = ((System.currentTimeMillis() - uiState.startTimeMillis) / 60000).toInt()
    val volumeByGroup = uiState.logEntries
        .groupBy { it.muscleGroup }
        .mapValues { (_, logs) -> logs.sumOf { it.sets * it.reps * it.weight } }

    Column(Modifier.padding(top = 16.dp)) {
        Text(
            text = "Workout Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        SummaryRow("Exercises", "${uiState.logEntries.size}")
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

        TextButton(
            onClick = onDiscard,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Discard")
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
