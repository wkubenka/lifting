package com.astute.body.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseJson(
    val id: String,
    val name: String,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val volumeMultiplier: Int = 1
)
