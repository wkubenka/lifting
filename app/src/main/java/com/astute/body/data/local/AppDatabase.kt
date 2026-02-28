package com.astute.body.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.RecoveryConfigDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity

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
