package com.astute.body.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astute.body.data.local.entity.ExerciseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DatabaseSeederTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun seedExercises_populatesDatabase() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        DatabaseSeeder.seedExercises(context, database)

        val count = database.exerciseDao().count()
        assertTrue("Expected 800+ exercises, got $count", count >= 800)
    }

    @Test
    fun seedExercises_knownExerciseIsQueryable() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        DatabaseSeeder.seedExercises(context, database)

        val exercise = database.exerciseDao().getById("Barbell_Curl")
        assertNotNull("Barbell_Curl should exist", exercise)
        assertTrue(
            "Barbell_Curl should target biceps",
            exercise!!.primaryMuscles.contains("biceps")
        )
    }

    @Test
    fun seedExercises_isIdempotent() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        DatabaseSeeder.seedExercises(context, database)
        val countFirst = database.exerciseDao().count()

        DatabaseSeeder.seedExercises(context, database)
        val countSecond = database.exerciseDao().count()

        assertEquals(countFirst, countSecond)
    }

    @Test
    fun seedRecoveryConfig_populatesAllGroups() = runTest {
        DatabaseSeeder.seedRecoveryConfig(database)

        val configs = database.recoveryConfigDao().getAll()
        assertEquals(7, configs.size)

        val chest = database.recoveryConfigDao().getByMuscleGroup("Chest")
        assertEquals(48, chest!!.minRecoveryHours)

        val core = database.recoveryConfigDao().getByMuscleGroup("Core")
        assertEquals(24, core!!.minRecoveryHours)
    }
}
