package com.astute.body.domain.generator

import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.domain.model.MuscleGroup

class FakeWorkoutRepository : IWorkoutRepository {
    var lastTrainedMillis: Map<MuscleGroup, Long?> = emptyMap()
    var sessionCounts: Map<MuscleGroup, Int> = emptyMap()
    var recentExerciseIds: Map<MuscleGroup, List<String>> = emptyMap()
    var exercises: Map<Set<String>, List<ExerciseEntity>> = emptyMap()
    var recoveryConfigs: List<RecoveryConfigEntity> = defaultRecoveryConfigs()
    var preferences: UserPreferencesEntity = UserPreferencesEntity(
        availableEquipment = listOf("barbell", "dumbbell", "body only"),
        experienceLevel = "intermediate"
    )

    override suspend fun getLastTrainedMillis(muscleGroup: MuscleGroup): Long? {
        return lastTrainedMillis[muscleGroup]
    }

    override suspend fun getSessionCountLast14Days(muscleGroup: MuscleGroup): Int {
        return sessionCounts[muscleGroup] ?: 0
    }

    override suspend fun getRecentExerciseIds(muscleGroup: MuscleGroup, limit: Int): List<String> {
        return recentExerciseIds[muscleGroup]?.take(limit) ?: emptyList()
    }

    override suspend fun getExercisesForMuscles(
        muscles: Set<String>,
        equipment: List<String>,
        level: String
    ): List<ExerciseEntity> {
        return exercises[muscles] ?: emptyList()
    }

    override suspend fun getExercisesForMusclesRelaxed(
        muscles: Set<String>,
        equipment: List<String>
    ): List<ExerciseEntity> {
        return exercises[muscles] ?: emptyList()
    }

    override suspend fun getRecoveryConfig(muscleGroup: MuscleGroup): RecoveryConfigEntity? {
        return recoveryConfigs.find { it.muscleGroup == muscleGroup.displayName }
    }

    override suspend fun getAllRecoveryConfigs(): List<RecoveryConfigEntity> {
        return recoveryConfigs
    }

    override suspend fun getUserPreferences(): UserPreferencesEntity {
        return preferences
    }

    companion object {
        fun defaultRecoveryConfigs() = listOf(
            RecoveryConfigEntity("Chest", 48),
            RecoveryConfigEntity("Back", 60),
            RecoveryConfigEntity("Shoulders", 48),
            RecoveryConfigEntity("Arms", 36),
            RecoveryConfigEntity("Legs (Push)", 60),
            RecoveryConfigEntity("Legs (Pull)", 60),
            RecoveryConfigEntity("Core", 24)
        )

        fun makeExercise(
            id: String,
            primaryMuscles: List<String> = listOf("chest"),
            mechanic: String? = "compound",
            equipment: String? = "barbell",
            level: String = "intermediate"
        ) = ExerciseEntity(
            id = id,
            name = id.replace("_", " "),
            force = "push",
            level = level,
            mechanic = mechanic,
            equipment = equipment,
            category = "strength",
            primaryMuscles = primaryMuscles,
            secondaryMuscles = emptyList(),
            instructions = emptyList(),
            images = emptyList()
        )
    }
}
