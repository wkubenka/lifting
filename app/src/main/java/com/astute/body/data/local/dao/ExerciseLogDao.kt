package com.astute.body.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.astute.body.data.local.entity.ExerciseLogEntity

@Dao
interface ExerciseLogDao {

    @Query("SELECT * FROM exercise_logs WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: Long): List<ExerciseLogEntity>

    @Query("""
        SELECT DISTINCT exerciseId FROM exercise_logs
        WHERE muscleGroup = :muscleGroup
        ORDER BY logId DESC
        LIMIT :limit
    """)
    suspend fun getRecentExerciseIds(muscleGroup: String, limit: Int): List<String>

    @Query("SELECT * FROM exercise_logs WHERE exerciseId = :exerciseId ORDER BY logId DESC")
    suspend fun getByExerciseId(exerciseId: String): List<ExerciseLogEntity>

    @Insert
    suspend fun insertAll(logs: List<ExerciseLogEntity>)
}
