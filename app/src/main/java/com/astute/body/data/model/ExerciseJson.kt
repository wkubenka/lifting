package com.astute.body.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseJson(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String,
    val mechanic: String? = null,
    val equipment: String? = null,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val volumeMultiplier: Int = 1
)
