package com.astute.body.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_config")
data class RecoveryConfigEntity(
    @PrimaryKey val muscleGroup: String,
    val minRecoveryHours: Int
)
