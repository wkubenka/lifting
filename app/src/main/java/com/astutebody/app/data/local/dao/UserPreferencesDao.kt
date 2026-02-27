package com.astutebody.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.astutebody.app.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferencesDao {

    @Query("SELECT * FROM user_preferences WHERE id = 0")
    fun get(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 0")
    suspend fun getOnce(): UserPreferencesEntity?

    @Upsert
    suspend fun upsert(preferences: UserPreferencesEntity)
}
