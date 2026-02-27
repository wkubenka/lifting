package com.astutebody.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    suspend fun initialize() {
        DatabaseSeeder.seedExercises(context, database)
        DatabaseSeeder.seedRecoveryConfig(database)
    }
}
