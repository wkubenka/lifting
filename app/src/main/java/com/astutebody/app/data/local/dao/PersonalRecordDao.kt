package com.astutebody.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.astutebody.app.data.local.entity.PersonalRecordEntity

@Dao
interface PersonalRecordDao {

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getByExerciseId(exerciseId: String): PersonalRecordEntity?

    @Upsert
    suspend fun upsert(record: PersonalRecordEntity)
}
