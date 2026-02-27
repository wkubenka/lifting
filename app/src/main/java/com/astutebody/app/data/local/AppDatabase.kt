package com.astutebody.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.astutebody.app.data.local.dao.ExerciseDao
import com.astutebody.app.data.local.dao.ExerciseLogDao
import com.astutebody.app.data.local.dao.PersonalRecordDao
import com.astutebody.app.data.local.dao.RecoveryConfigDao
import com.astutebody.app.data.local.dao.UserPreferencesDao
import com.astutebody.app.data.local.dao.WorkoutSessionDao
import com.astutebody.app.data.local.entity.ExerciseEntity
import com.astutebody.app.data.local.entity.ExerciseLogEntity
import com.astutebody.app.data.local.entity.PersonalRecordEntity
import com.astutebody.app.data.local.entity.RecoveryConfigEntity
import com.astutebody.app.data.local.entity.UserPreferencesEntity
import com.astutebody.app.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        ExerciseLogEntity::class,
        PersonalRecordEntity::class,
        UserPreferencesEntity::class,
        RecoveryConfigEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun recoveryConfigDao(): RecoveryConfigDao
}
