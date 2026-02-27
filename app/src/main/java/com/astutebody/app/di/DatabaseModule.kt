package com.astutebody.app.di

import android.content.Context
import androidx.room.Room
import com.astutebody.app.data.local.AppDatabase
import com.astutebody.app.data.local.dao.ExerciseDao
import com.astutebody.app.data.local.dao.ExerciseLogDao
import com.astutebody.app.data.local.dao.PersonalRecordDao
import com.astutebody.app.data.local.dao.RecoveryConfigDao
import com.astutebody.app.data.local.dao.UserPreferencesDao
import com.astutebody.app.data.local.dao.WorkoutSessionDao
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
        ).build()
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
}
