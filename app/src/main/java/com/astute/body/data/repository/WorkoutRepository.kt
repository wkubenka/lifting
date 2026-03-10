package com.astute.body.data.repository

import com.astute.body.data.local.dao.ActiveWorkoutDao
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.RecoveryConfigDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.domain.model.MuscleGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val recoveryConfigDao: RecoveryConfigDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val personalRecordDao: PersonalRecordDao,
    private val activeWorkoutDao: ActiveWorkoutDao
) : IWorkoutRepository {

    override suspend fun getLastTrainedMillis(muscleGroup: MuscleGroup): Long? {
        return workoutSessionDao.getLastTrainedDate(muscleGroup.displayName)
    }

    override suspend fun getSessionCountLast14Days(muscleGroup: MuscleGroup, nowMillis: Long): Int {
        val fourteenDaysAgo = nowMillis - 14L * 24 * 60 * 60 * 1000
        return workoutSessionDao.getSessionCountSince(muscleGroup.displayName, fourteenDaysAgo)
    }

    override suspend fun getRecentExerciseIds(muscleGroup: MuscleGroup, limit: Int): List<String> {
        return exerciseLogDao.getRecentExerciseIds(muscleGroup.displayName, limit)
    }

    override suspend fun getExercisesForMuscles(
        muscles: Set<String>,
        equipment: List<String>,
        level: String
    ): List<ExerciseEntity> {
        return queryExercises(muscles, equipment, level)
    }

    override suspend fun getExercisesForMusclesRelaxed(
        muscles: Set<String>,
        equipment: List<String>
    ): List<ExerciseEntity> {
        return queryExercises(muscles, equipment, level = null)
    }

    private suspend fun queryExercises(
        muscles: Set<String>,
        equipment: List<String>,
        level: String?
    ): List<ExerciseEntity> {
        return muscles.flatMap { exerciseDao.getByMuscle(it) }
            .distinctBy { it.id }
            .filter { exercise ->
                (exercise.equipment == null ||
                    exercise.equipment == "body only" ||
                    equipment.contains(exercise.equipment)) &&
                    exercise.category == "strength" &&
                    (level == null || isLevelCompatible(exercise.level, level))
            }
    }

    override suspend fun getRecoveryConfig(muscleGroup: MuscleGroup): RecoveryConfigEntity? {
        return recoveryConfigDao.getByMuscleGroup(muscleGroup.displayName)
    }

    override suspend fun getAllRecoveryConfigs(): List<RecoveryConfigEntity> {
        return recoveryConfigDao.getAll()
    }

    override suspend fun getUserPreferences(): UserPreferencesEntity {
        return userPreferencesDao.getOnce() ?: UserPreferencesEntity()
    }

    override suspend fun getExercisesByIds(ids: List<String>): List<ExerciseEntity> {
        if (ids.isEmpty()) return emptyList()
        return exerciseDao.getByIds(ids)
    }

    // Active workout persistence

    override suspend fun getActiveWorkout(): ActiveWorkoutEntity? {
        return activeWorkoutDao.get()
    }

    override suspend fun saveActiveWorkout(entity: ActiveWorkoutEntity) {
        activeWorkoutDao.upsert(entity)
    }

    override suspend fun clearActiveWorkout() {
        activeWorkoutDao.clear()
    }

    // Workout saving

    override suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long {
        return workoutSessionDao.insert(session)
    }

    override suspend fun insertExerciseLogs(logs: List<ExerciseLogEntity>) {
        exerciseLogDao.insertAll(logs)
    }

    // Personal records

    override suspend fun getPersonalRecord(exerciseId: String): PersonalRecordEntity? {
        return personalRecordDao.getByExerciseId(exerciseId)
    }

    override suspend fun upsertPersonalRecord(record: PersonalRecordEntity) {
        personalRecordDao.upsert(record)
    }

    private fun isLevelCompatible(exerciseLevel: String, userLevel: String): Boolean {
        val levels = listOf("beginner", "intermediate", "expert")
        val exerciseIdx = levels.indexOf(exerciseLevel)
        val userIdx = levels.indexOf(userLevel)
        return exerciseIdx <= userIdx
    }
}
