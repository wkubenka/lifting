package com.astute.body.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onNavigateToManageExercises: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...")
        }
        return
    }

    val prefs = uiState.preferences

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        // Experience Level
        SectionHeader("Experience Level")
        listOf("beginner", "intermediate", "expert").forEach { level ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setExperienceLevel(level) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = prefs.experienceLevel == level,
                    onClick = { viewModel.setExperienceLevel(level) }
                )
                Text(
                    text = level.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        SectionDivider()

        // Weight Unit
        SectionHeader("Weight Unit")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("lbs", "kg").forEach { unit ->
                Row(
                    Modifier.clickable { viewModel.setWeightUnit(unit) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = prefs.weightUnit == unit,
                        onClick = { viewModel.setWeightUnit(unit) }
                    )
                    Text(unit, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        SectionDivider()

        // Target Workout Size
        SectionHeader("Target Workout Size: ${prefs.targetWorkoutSize} exercises")
        Slider(
            value = prefs.targetWorkoutSize.toFloat(),
            onValueChange = { viewModel.setTargetWorkoutSize(it.toInt()) },
            valueRange = 5f..12f,
            steps = 6,
            modifier = Modifier.fillMaxWidth()
        )

        SectionDivider()

        // Rest Timer Durations
        SectionHeader("Rest Timer Durations (seconds)")
        Spacer(Modifier.height(8.dp))

        RestTimerInput(
            label = "Compound exercises",
            value = prefs.restCompound,
            onValueChange = { viewModel.setRestCompound(it) }
        )
        Spacer(Modifier.height(8.dp))
        RestTimerInput(
            label = "Isolation exercises",
            value = prefs.restIsolation,
            onValueChange = { viewModel.setRestIsolation(it) }
        )
        Spacer(Modifier.height(8.dp))
        RestTimerInput(
            label = "Bodyweight / Ab exercises",
            value = prefs.restBodyweightAb,
            onValueChange = { viewModel.setRestBodyweightAb(it) }
        )

        SectionDivider()

        // Equipment Selection
        SectionHeader("Available Equipment")
        Text(
            text = "${prefs.availableEquipment.size} of ${uiState.allEquipment.size} selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        uiState.allEquipment.forEach { equipment ->
            val isChecked = prefs.availableEquipment.contains(equipment)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleEquipment(equipment) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { viewModel.toggleEquipment(equipment) }
                )
                Text(
                    text = equipment,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        SectionDivider()

        // Manage Favorites & Exclusions
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onNavigateToManageExercises() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Manage Favorites & Exclusions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${uiState.favoriteCount} favorites, ${uiState.excludedCount} excluded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Go to manage exercises",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RestTimerInput(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text ->
                onValueChange(text.toIntOrNull() ?: value)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(80.dp),
            singleLine = true
        )
    }
}
