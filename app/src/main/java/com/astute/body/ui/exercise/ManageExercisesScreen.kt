package com.astute.body.ui.exercise

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astute.body.data.local.entity.ExerciseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageExercisesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageExercisesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manage Exercises", maxLines = 1)
                        Text(
                            text = "${uiState.favoritedIds.size} favorites, ${uiState.excludedIds.size} excluded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // Filter tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTab.entries.forEach { tab ->
                    val label = when (tab) {
                        FilterTab.ALL -> "All"
                        FilterTab.FAVORITES -> "Favorites (${uiState.favoritedIds.size})"
                        FilterTab.EXCLUDED -> "Excluded (${uiState.excludedIds.size})"
                    }
                    FilterChip(
                        selected = uiState.filterTab == tab,
                        onClick = { viewModel.setFilterTab(tab) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.filteredExercises.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (uiState.filterTab) {
                                FilterTab.FAVORITES -> "No favorite exercises yet"
                                FilterTab.EXCLUDED -> "No excluded exercises yet"
                                FilterTab.ALL -> "No exercises found"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn {
                        items(uiState.filteredExercises, key = { it.id }) { exercise ->
                            ExerciseManageRow(
                                exercise = exercise,
                                isFavorited = exercise.id in uiState.favoritedIds,
                                isExcluded = exercise.id in uiState.excludedIds,
                                onToggleFavorite = { viewModel.toggleFavorite(exercise.id) },
                                onToggleExcluded = { viewModel.toggleExcluded(exercise.id) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseManageRow(
    exercise: ExerciseEntity,
    isFavorited: Boolean,
    isExcluded: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleExcluded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                exercise.equipment?.let { equip ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(equip, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                exercise.primaryMuscles.take(2).forEach { muscle ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(muscle, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleExcluded) {
            Icon(
                if (isExcluded) Icons.Filled.Block else Icons.Outlined.Block,
                contentDescription = if (isExcluded) "Remove from excluded" else "Exclude exercise",
                tint = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
