package com.astute.body.ui.workout

import kotlinx.serialization.Serializable

@Serializable
data class PlannedExerciseRef(
    val exerciseId: String,
    val muscleGroup: String
)
