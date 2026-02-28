package com.astute.body.domain.model

import com.astute.body.data.local.entity.ExerciseEntity

data class WorkoutPlan(
    val muscleGroupAllocations: List<MuscleGroupAllocation>,
    val generatedAtMillis: Long = System.currentTimeMillis()
)

data class MuscleGroupAllocation(
    val muscleGroup: MuscleGroup,
    val score: Double,
    val exercises: List<PlannedExercise>,
    val warning: String? = null
)

data class PlannedExercise(
    val exercise: ExerciseEntity,
    val muscleGroup: MuscleGroup,
    val isLocked: Boolean = false
)
