package com.astutebody.app.domain.model

import com.astutebody.app.data.local.entity.ExerciseEntity

data class WorkoutPlan(
    val muscleGroupAllocations: List<MuscleGroupAllocation>,
    val generatedAtMillis: Long = System.currentTimeMillis()
)

data class MuscleGroupAllocation(
    val muscleGroup: MuscleGroup,
    val score: Double,
    val exercises: List<PlannedExercise>
)

data class PlannedExercise(
    val exercise: ExerciseEntity,
    val muscleGroup: MuscleGroup,
    val isLocked: Boolean = false
)
