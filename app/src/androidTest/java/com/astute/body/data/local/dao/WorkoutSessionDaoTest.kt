package com.astute.body.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astute.body.data.local.AppDatabase
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutSessionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var sessionDao: WorkoutSessionDao
    private lateinit var logDao: ExerciseLogDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        sessionDao = database.workoutSessionDao()
        logDao = database.exerciseLogDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runTest {
        val session = WorkoutSessionEntity(date = 1000L, muscleGroups = listOf("Chest", "Arms"))
        val id = sessionDao.insert(session)
        val result = sessionDao.getById(id)
        assertEquals(listOf("Chest", "Arms"), result?.muscleGroups)
    }

    @Test
    fun getAllOrderedByDate_returnsDescending() = runTest {
        sessionDao.insert(WorkoutSessionEntity(date = 100L, muscleGroups = listOf("Chest")))
        sessionDao.insert(WorkoutSessionEntity(date = 300L, muscleGroups = listOf("Back")))
        sessionDao.insert(WorkoutSessionEntity(date = 200L, muscleGroups = listOf("Arms")))

        val sessions = sessionDao.getAllOrderedByDate().first()
        assertEquals(3, sessions.size)
        assertEquals(300L, sessions[0].date)
        assertEquals(200L, sessions[1].date)
        assertEquals(100L, sessions[2].date)
    }

    @Test
    fun getSessionsSince_filtersCorrectly() = runTest {
        sessionDao.insert(WorkoutSessionEntity(date = 100L, muscleGroups = listOf("Chest")))
        sessionDao.insert(WorkoutSessionEntity(date = 300L, muscleGroups = listOf("Back")))
        sessionDao.insert(WorkoutSessionEntity(date = 500L, muscleGroups = listOf("Arms")))

        val recent = sessionDao.getSessionsSince(200L)
        assertEquals(2, recent.size)
    }

    @Test
    fun cascadeDelete_removesLogs() = runTest {
        val sessionId = sessionDao.insert(
            WorkoutSessionEntity(date = 1000L, muscleGroups = listOf("Chest"))
        )
        logDao.insertAll(listOf(
            ExerciseLogEntity(
                sessionId = sessionId,
                exerciseId = "Bench_Press",
                muscleGroup = "Chest",
                sets = 3,
                reps = 10,
                weight = 135.0
            )
        ))

        assertEquals(1, logDao.getBySessionId(sessionId).size)
        sessionDao.deleteById(sessionId)
        assertNull(sessionDao.getById(sessionId))
        assertTrue(logDao.getBySessionId(sessionId).isEmpty())
    }
}
