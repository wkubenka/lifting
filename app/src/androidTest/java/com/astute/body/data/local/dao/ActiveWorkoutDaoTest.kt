package com.astute.body.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astute.body.data.local.AppDatabase
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ActiveWorkoutDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ActiveWorkoutDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.activeWorkoutDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun makeEntity(
        currentIndex: Int = 0,
        setsCompleted: Int = 0,
        currentSets: Int = 3,
        currentReps: Int = 10,
        currentWeight: Double = 135.0
    ) = ActiveWorkoutEntity(
        exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"}]""",
        currentIndex = currentIndex,
        logEntries = "[]",
        setsCompleted = setsCompleted,
        currentSets = currentSets,
        currentReps = currentReps,
        currentWeight = currentWeight,
        startedAtMillis = 1000L,
        newPRs = "[]"
    )

    @Test
    fun saveAndRetrieve_roundTrips() = runTest {
        val entity = makeEntity(currentIndex = 2, setsCompleted = 1, currentWeight = 185.0)
        dao.upsert(entity)

        val result = dao.get()
        assertNotNull(result)
        assertEquals(2, result!!.currentIndex)
        assertEquals(1, result.setsCompleted)
        assertEquals(185.0, result.currentWeight, 0.01)
        assertEquals(3, result.currentSets)
        assertEquals(10, result.currentReps)
        assertEquals(1000L, result.startedAtMillis)
    }

    @Test
    fun get_returnsNullWhenEmpty() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun clear_removesActiveWorkout() = runTest {
        dao.upsert(makeEntity())
        assertNotNull(dao.get())

        dao.clear()
        assertNull(dao.get())
    }

    @Test
    fun upsert_overwritesPreviousState() = runTest {
        dao.upsert(makeEntity(currentIndex = 0, currentWeight = 135.0))

        val first = dao.get()
        assertEquals(0, first!!.currentIndex)
        assertEquals(135.0, first.currentWeight, 0.01)

        dao.upsert(makeEntity(currentIndex = 3, currentWeight = 225.0))

        val second = dao.get()
        assertEquals(3, second!!.currentIndex)
        assertEquals(225.0, second.currentWeight, 0.01)
    }
}
