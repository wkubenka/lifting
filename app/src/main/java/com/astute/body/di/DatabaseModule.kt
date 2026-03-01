package com.astute.body.di

import android.content.Context
import androidx.room.Room
import com.astute.body.data.local.AppDatabase
import com.astute.body.data.local.AppDatabase.Companion.MIGRATION_1_2
import com.astute.body.data.local.AppDatabase.Companion.MIGRATION_2_3
import com.astute.body.data.local.AppDatabase.Companion.MIGRATION_3_4
import com.astute.body.data.local.dao.ActiveWorkoutDao
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.RecoveryConfigDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "astutebody.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideExerciseDao(database: AppDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideWorkoutSessionDao(database: AppDatabase): WorkoutSessionDao = database.workoutSessionDao()

    @Provides
    fun provideExerciseLogDao(database: AppDatabase): ExerciseLogDao = database.exerciseLogDao()

    @Provides
    fun providePersonalRecordDao(database: AppDatabase): PersonalRecordDao = database.personalRecordDao()

    @Provides
    fun provideUserPreferencesDao(database: AppDatabase): UserPreferencesDao = database.userPreferencesDao()

    @Provides
    fun provideRecoveryConfigDao(database: AppDatabase): RecoveryConfigDao = database.recoveryConfigDao()

    @Provides
    fun provideActiveWorkoutDao(database: AppDatabase): ActiveWorkoutDao = database.activeWorkoutDao()
}
