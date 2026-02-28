package com.astutebody.app.data.repository

import com.astutebody.app.data.local.entity.ExerciseEntity
import com.astutebody.app.data.local.entity.RecoveryConfigEntity
import com.astutebody.app.data.local.entity.UserPreferencesEntity
import com.astutebody.app.domain.model.MuscleGroup

interface IWorkoutRepository {
    suspend fun getLastTrainedMillis(muscleGroup: MuscleGroup): Long?
    suspend fun getSessionCountLast14Days(muscleGroup: MuscleGroup): Int
    suspend fun getRecentExerciseIds(muscleGroup: MuscleGroup, limit: Int): List<String>
    suspend fun getExercisesForMuscles(
        muscles: Set<String>,
        equipment: List<String>,
        level: String
    ): List<ExerciseEntity>
    suspend fun getExercisesForMusclesRelaxed(
        muscles: Set<String>,
        equipment: List<String>
    ): List<ExerciseEntity>
    suspend fun getRecoveryConfig(muscleGroup: MuscleGroup): RecoveryConfigEntity?
    suspend fun getAllRecoveryConfigs(): List<RecoveryConfigEntity>
    suspend fun getUserPreferences(): UserPreferencesEntity
}
