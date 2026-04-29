package com.astute.body.data.local

import android.content.Context
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.model.ExerciseJson
import kotlinx.serialization.json.Json

object DatabaseSeeder {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedExercises(context: Context, database: AppDatabase) {
        val dao = database.exerciseDao()
        if (dao.count() > 0) return

        val jsonString = context.assets.open("exercises.json")
            .bufferedReader()
            .use { it.readText() }

        val exercises = json.decodeFromString<List<ExerciseJson>>(jsonString)

        val entities = exercises.map { it.toEntity() }
        dao.insertAll(entities)
    }

    suspend fun seedRecoveryConfig(database: AppDatabase) {
        val dao = database.recoveryConfigDao()
        val defaults = listOf(
            RecoveryConfigEntity("Chest", 48),
            RecoveryConfigEntity("Back", 60),
            RecoveryConfigEntity("Shoulders", 48),
            RecoveryConfigEntity("Arms", 36),
            RecoveryConfigEntity("Legs (Push)", 60),
            RecoveryConfigEntity("Legs (Pull)", 60),
            RecoveryConfigEntity("Core", 24)
        )
        dao.insertAll(defaults)
    }

    private fun ExerciseJson.toEntity() = ExerciseEntity(
        id = id,
        name = name,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        category = category,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        instructions = instructions,
        volumeMultiplier = volumeMultiplier
    )
}
