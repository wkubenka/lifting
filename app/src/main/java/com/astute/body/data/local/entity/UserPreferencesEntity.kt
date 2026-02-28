package com.astute.body.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val experienceLevel: String = "beginner",
    val availableEquipment: List<String> = emptyList(),
    val excludedExercises: List<String> = emptyList(),
    val favoritedExercises: List<String> = emptyList(),
    val weightUnit: String = "lbs",
    val targetWorkoutSize: Int = 8,
    val restCompound: Int = 60,
    val restIsolation: Int = 45,
    val restBodyweightAb: Int = 30
)
