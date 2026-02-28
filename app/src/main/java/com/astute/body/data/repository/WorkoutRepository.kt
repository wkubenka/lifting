package com.astute.body.data.repository

import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.RecoveryConfigDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.model.MuscleGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val recoveryConfigDao: RecoveryConfigDao,
    private val userPreferencesDao: UserPreferencesDao
) : IWorkoutRepository {

    override suspend fun getLastTrainedMillis(muscleGroup: MuscleGroup): Long? {
        val sessions = workoutSessionDao.getSessionsSince(0L)
        return sessions
            .filter { it.muscleGroups.contains(muscleGroup.displayName) }
            .maxByOrNull { it.date }
            ?.date
    }

    override suspend fun getSessionCountLast14Days(muscleGroup: MuscleGroup): Int {
        val fourteenDaysAgo = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        val sessions = workoutSessionDao.getSessionsSince(fourteenDaysAgo)
        return sessions.count { it.muscleGroups.contains(muscleGroup.displayName) }
    }

    override suspend fun getRecentExerciseIds(muscleGroup: MuscleGroup, limit: Int): List<String> {
        return exerciseLogDao.getRecentExerciseIds(muscleGroup.displayName, limit)
    }

    override suspend fun getExercisesForMuscles(
        muscles: Set<String>,
        equipment: List<String>,
        level: String
    ): List<ExerciseEntity> {
        val allExercises = mutableListOf<ExerciseEntity>()
        for (muscle in muscles) {
            allExercises.addAll(exerciseDao.getByMuscle(muscle))
        }
        return allExercises
            .distinctBy { it.id }
            .filter { exercise ->
                (exercise.equipment == null ||
                    exercise.equipment == "body only" ||
                    equipment.contains(exercise.equipment)) &&
                    exercise.category == "strength" &&
                    isLevelCompatible(exercise.level, level)
            }
    }

    override suspend fun getExercisesForMusclesRelaxed(
        muscles: Set<String>,
        equipment: List<String>
    ): List<ExerciseEntity> {
        val allExercises = mutableListOf<ExerciseEntity>()
        for (muscle in muscles) {
            allExercises.addAll(exerciseDao.getByMuscle(muscle))
        }
        return allExercises
            .distinctBy { it.id }
            .filter { exercise ->
                (exercise.equipment == null ||
                    exercise.equipment == "body only" ||
                    equipment.contains(exercise.equipment)) &&
                    exercise.category == "strength"
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

    private fun isLevelCompatible(exerciseLevel: String, userLevel: String): Boolean {
        val levels = listOf("beginner", "intermediate", "expert")
        val exerciseIdx = levels.indexOf(exerciseLevel)
        val userIdx = levels.indexOf(userLevel)
        return exerciseIdx <= userIdx
    }
}
