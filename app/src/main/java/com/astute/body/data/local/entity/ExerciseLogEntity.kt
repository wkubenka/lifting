package com.astute.body.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_logs",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val muscleGroup: String,
    val sets: Int,
    val reps: Int,
    val weight: Double
)
