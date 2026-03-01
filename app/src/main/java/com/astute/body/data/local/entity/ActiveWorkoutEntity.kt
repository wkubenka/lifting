package com.astute.body.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(
    @PrimaryKey val id: Int = 0,
    val exerciseRefs: String,
    val currentIndex: Int,
    val logEntries: String,
    val setsCompleted: Int,
    val currentSets: Int,
    val currentReps: Int,
    val currentWeight: Double,
    val startedAtMillis: Long,
    val newPRs: String
)
