package com.astutebody.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_records")
data class PersonalRecordEntity(
    @PrimaryKey val exerciseId: String,
    val maxWeight: Double = 0.0,
    val maxWeightDate: Long? = null,
    val maxReps: Int = 0,
    val maxRepsDate: Long? = null,
    val lastWeight: Double = 0.0,
    val lastReps: Int = 0,
    val lastPerformed: Long? = null
)
