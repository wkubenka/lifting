package com.astute.body.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astute.body.domain.model.MuscleGroupAllocation
import com.astute.body.domain.model.PlannedExercise

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        if (uiState.needsSetup) {
            viewModel.onSetupComplete()
        }
        onPauseOrDispose {}
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
        uiState.workoutPlan != null -> {
            WorkoutPlanContent(
                allocations = uiState.workoutPlan!!.muscleGroupAllocations,
                onSwapExercise = { viewModel.swapExercise(it) },
                onRegenerateGroup = { viewModel.regenerateGroup(it.muscleGroup) },
                onRegenerateAll = { viewModel.regenerateAll() },
                onStartWorkout = {
                    viewModel.startWorkout()
                    onStartWorkout()
                }
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

@Composable
private fun WorkoutPlanContent(
    allocations: List<MuscleGroupAllocation>,
    onSwapExercise: (PlannedExercise) -> Unit,
    onRegenerateGroup: (MuscleGroupAllocation) -> Unit,
    onRegenerateAll: () -> Unit,
    onStartWorkout: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

            allocations.forEach { allocation ->
                item {
                    MuscleGroupHeader(
                        allocation = allocation,
                        onRegenerate = { onRegenerateGroup(allocation) }
                    )
                }
                items(allocation.exercises, key = { it.exercise.id }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onSwap = { onSwapExercise(exercise) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

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

@Composable
private fun MuscleGroupHeader(
    allocation: MuscleGroupAllocation,
    onRegenerate: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = allocation.muscleGroup.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${allocation.exercises.size} exercises",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            allocation.warning?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        IconButton(onClick = onRegenerate) {
            Icon(Icons.Default.Refresh, contentDescription = "Regenerate ${allocation.muscleGroup.displayName}")
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: PlannedExercise,
    onSwap: () -> Unit
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = exercise.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
