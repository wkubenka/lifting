package com.astutebody.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.astutebody.app.data.local.entity.RecoveryConfigEntity

@Dao
interface RecoveryConfigDao {

    @Query("SELECT * FROM recovery_config WHERE muscleGroup = :muscleGroup")
    suspend fun getByMuscleGroup(muscleGroup: String): RecoveryConfigEntity?

    @Query("SELECT * FROM recovery_config")
    suspend fun getAll(): List<RecoveryConfigEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<RecoveryConfigEntity>)
}
