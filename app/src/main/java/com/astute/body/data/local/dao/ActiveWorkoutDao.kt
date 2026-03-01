package com.astute.body.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.astute.body.data.local.entity.ActiveWorkoutEntity

@Dao
interface ActiveWorkoutDao {

    @Query("SELECT * FROM active_workout WHERE id = 0")
    suspend fun get(): ActiveWorkoutEntity?

    @Upsert
    suspend fun upsert(entity: ActiveWorkoutEntity)

    @Query("DELETE FROM active_workout")
    suspend fun clear()
}
