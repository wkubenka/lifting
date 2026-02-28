package com.astute.body.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.astute.body.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC")
    fun getAllOrderedByDate(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE date >= :sinceEpochMillis ORDER BY date DESC")
    suspend fun getSessionsSince(sinceEpochMillis: Long): List<WorkoutSessionEntity>

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Query("DELETE FROM workout_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: Long)
}
