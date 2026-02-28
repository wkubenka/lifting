package com.astute.body.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val date: Long,
    val muscleGroups: List<String>,
    val completed: Boolean = false
)
