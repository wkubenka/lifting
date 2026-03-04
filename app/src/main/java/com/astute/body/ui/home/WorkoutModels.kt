package com.astute.body.ui.home

import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.model.PlannedExercise
import kotlinx.serialization.Serializable

enum class WorkoutMode {
    PLANNING,
    ACTIVE,
    COMPLETE
}

@Serializable
data class ExerciseLogEntry(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val volumeMultiplier: Int = 1
)

@Serializable
data class SetEntry(
    val reps: Int,
    val weight: Double
)

data class ExercisePerformance(
    val lastWeight: Double?,
    val lastReps: Int?,
    val maxWeight: Double,
    val maxReps: Int,
    val maxWeightDate: Long?,
    val maxRepsDate: Long?
)

@Serializable
data class NewPR(
    val exerciseId: String,
    val exerciseName: String,
    val type: PRType,
    val oldValue: Double,
    val newValue: Double
)

@Serializable
enum class PRType { WEIGHT, REPS }

@Serializable
data class PlannedExerciseRef(
    val exerciseId: String,
    val muscleGroup: String
)

fun formatWeight(weight: Double): String {
    return weight.toBigDecimal().stripTrailingZeros().toPlainString()
}

fun getRestDuration(exercise: PlannedExercise, prefs: UserPreferencesEntity): Int {
    val isAbdominal = exercise.exercise.primaryMuscles.contains("abdominals")
    val isBodyweight = exercise.exercise.equipment == null ||
            exercise.exercise.equipment == "body only"

    return when {
        isAbdominal && isBodyweight -> prefs.restBodyweightAb
        exercise.exercise.mechanic == "compound" -> prefs.restCompound
        else -> prefs.restIsolation
    }
}

fun detectNewPRs(entry: ExerciseLogEntry, performance: ExercisePerformance?): List<NewPR> {
    val prs = mutableListOf<NewPR>()
    val maxWeight = performance?.maxWeight ?: 0.0
    val maxReps = performance?.maxReps ?: 0

    if (entry.weight > maxWeight) {
        prs.add(
            NewPR(
                exerciseId = entry.exerciseId,
                exerciseName = entry.exerciseName,
                type = PRType.WEIGHT,
                oldValue = maxWeight,
                newValue = entry.weight
            )
        )
    }
    if (entry.reps > maxReps) {
        prs.add(
            NewPR(
                exerciseId = entry.exerciseId,
                exerciseName = entry.exerciseName,
                type = PRType.REPS,
                oldValue = maxReps.toDouble(),
                newValue = entry.reps.toDouble()
            )
        )
    }
    return prs
}
