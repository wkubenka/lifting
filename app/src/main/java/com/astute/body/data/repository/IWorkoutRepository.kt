package com.astute.body.data.repository

import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.domain.model.MuscleGroup

interface IWorkoutRepository {
    suspend fun getLastTrainedMillis(muscleGroup: MuscleGroup): Long?
    suspend fun getSessionCountLast14Days(muscleGroup: MuscleGroup, nowMillis: Long): Int
    suspend fun getRecentExerciseIds(muscleGroup: MuscleGroup, limit: Int): List<String>
    suspend fun getExercisesForMuscles(muscles: Set<String>): List<ExerciseEntity>
    suspend fun getRecoveryConfig(muscleGroup: MuscleGroup): RecoveryConfigEntity?
    suspend fun getAllRecoveryConfigs(): List<RecoveryConfigEntity>
    suspend fun getUserPreferences(): UserPreferencesEntity
    suspend fun getExercisesByIds(ids: List<String>): List<ExerciseEntity>

    // Active workout persistence
    suspend fun getActiveWorkout(): ActiveWorkoutEntity?
    suspend fun saveActiveWorkout(entity: ActiveWorkoutEntity)
    suspend fun clearActiveWorkout()

    // Workout saving
    suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long
    suspend fun insertExerciseLogs(logs: List<ExerciseLogEntity>)

    // Personal records
    suspend fun getPersonalRecord(exerciseId: String): PersonalRecordEntity?
    suspend fun upsertPersonalRecord(record: PersonalRecordEntity)
}
