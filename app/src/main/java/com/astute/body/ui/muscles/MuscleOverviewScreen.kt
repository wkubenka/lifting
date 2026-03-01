package com.astute.body.ui.muscles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MuscleOverviewScreen(
    viewModel: MuscleOverviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Muscle Groups",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.groups, key = { it.muscleGroup.name }) { status ->
                MuscleGroupCard(status)
            }
        }
    }
}

@Composable
private fun MuscleGroupCard(status: MuscleGroupStatus) {
    val freshnessColor = when (status.freshnessStatus) {
        FreshnessStatus.RECOVERING -> Color(0xFF5C6BC0) // Indigo
        FreshnessStatus.READY -> Color(0xFF43A047)      // Green
        FreshnessStatus.DUE -> Color(0xFFFFA000)        // Amber
        FreshnessStatus.OVERDUE -> Color(0xFFE53935)    // Red
    }

    val freshnessLabel = when (status.freshnessStatus) {
        FreshnessStatus.RECOVERING -> "Recovering"
        FreshnessStatus.READY -> "Ready"
        FreshnessStatus.DUE -> "Due"
        FreshnessStatus.OVERDUE -> "Overdue"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = status.muscleGroup.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = classificationLabel(status.muscleGroup.classification),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(freshnessLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = freshnessColor.copy(alpha = 0.15f),
                        labelColor = freshnessColor
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = formatTimeSince(status.hoursSinceLastTrained),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            val targetProgress = (status.recoveryRatio.coerceAtMost(1.0)).toFloat()
            var animTarget by remember { mutableFloatStateOf(0f) }
            val animatedProgress by animateFloatAsState(
                targetValue = animTarget,
                animationSpec = tween(durationMillis = 500),
                label = "recovery"
            )
            LaunchedEffect(targetProgress) { animTarget = targetProgress }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = freshnessColor,
                trackColor = freshnessColor.copy(alpha = 0.12f)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${status.sessionsLast14Days} session${if (status.sessionsLast14Days != 1) "s" else ""} in last 14 days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun classificationLabel(classification: com.astute.body.domain.model.MuscleGroup.Classification): String {
    return when (classification) {
        com.astute.body.domain.model.MuscleGroup.Classification.MAJOR_PUSH -> "Major Push"
        com.astute.body.domain.model.MuscleGroup.Classification.MAJOR_PULL -> "Major Pull"
        com.astute.body.domain.model.MuscleGroup.Classification.MAJOR -> "Major"
        com.astute.body.domain.model.MuscleGroup.Classification.MINOR -> "Minor"
    }
}

private fun formatTimeSince(hours: Double?): String {
    if (hours == null) return "Never trained"
    return when {
        hours < 1 -> "Trained less than an hour ago"
        hours < 24 -> "${hours.toInt()} hour${if (hours.toInt() != 1) "s" else ""} ago"
        else -> {
            val days = (hours / 24).toInt()
            "$days day${if (days != 1) "s" else ""} ago"
        }
    }
}
