package com.astute.body.di

import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.data.repository.WorkoutRepository
import com.astute.body.domain.scoring.MuscleGroupScorer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideMuscleGroupScorer(): MuscleGroupScorer = MuscleGroupScorer()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepository): IWorkoutRepository
}
