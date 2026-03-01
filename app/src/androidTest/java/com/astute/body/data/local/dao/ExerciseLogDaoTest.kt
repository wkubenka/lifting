package com.astute.body.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astute.body.data.local.AppDatabase
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExerciseLogDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var logDao: ExerciseLogDao
    private lateinit var sessionDao: WorkoutSessionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        logDao = database.exerciseLogDao()
        sessionDao = database.workoutSessionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun createSession(date: Long = 1000L): Long =
        sessionDao.insert(WorkoutSessionEntity(date = date, muscleGroups = listOf("Chest"), completed = true))

    private fun makeLog(
        sessionId: Long,
        exerciseId: String = "Bench_Press",
        sets: Int = 3,
        reps: Int = 10,
        weight: Double = 135.0
    ) = ExerciseLogEntity(
        sessionId = sessionId,
        exerciseId = exerciseId,
        muscleGroup = "Chest",
        sets = sets,
        reps = reps,
        weight = weight
    )

    @Test
    fun update_modifiesSetsRepsWeight() = runTest {
        val sessionId = createSession()
        logDao.insertAll(listOf(makeLog(sessionId, sets = 3, reps = 10, weight = 135.0)))

        val original = logDao.getBySessionId(sessionId).first()
        assertEquals(3, original.sets)
        assertEquals(135.0, original.weight, 0.01)

        val updated = original.copy(sets = 5, reps = 8, weight = 185.0)
        logDao.update(updated)

        val result = logDao.getBySessionId(sessionId).first()
        assertEquals(5, result.sets)
        assertEquals(8, result.reps)
        assertEquals(185.0, result.weight, 0.01)
    }

    @Test
    fun deleteById_removesSingleLogWithoutAffectingOthers() = runTest {
        val sessionId = createSession()
        logDao.insertAll(listOf(
            makeLog(sessionId, exerciseId = "Bench_Press"),
            makeLog(sessionId, exerciseId = "Incline_Press")
        ))

        val logs = logDao.getBySessionId(sessionId)
        assertEquals(2, logs.size)

        val toDelete = logs.find { it.exerciseId == "Bench_Press" }!!
        logDao.deleteById(toDelete.logId)

        val remaining = logDao.getBySessionId(sessionId)
        assertEquals(1, remaining.size)
        assertEquals("Incline_Press", remaining[0].exerciseId)
    }

    @Test
    fun getByExerciseId_returnsLogsAcrossSessions() = runTest {
        val session1 = createSession(date = 1000L)
        val session2 = createSession(date = 2000L)

        logDao.insertAll(listOf(
            makeLog(session1, exerciseId = "Bench_Press", weight = 135.0),
            makeLog(session1, exerciseId = "Squat", weight = 225.0)
        ))
        logDao.insertAll(listOf(
            makeLog(session2, exerciseId = "Bench_Press", weight = 155.0)
        ))

        val benchLogs = logDao.getByExerciseId("Bench_Press")
        assertEquals(2, benchLogs.size)
        assertTrue(benchLogs.any { it.weight == 135.0 })
        assertTrue(benchLogs.any { it.weight == 155.0 })
    }

    @Test
    fun getByExerciseId_returnsEmptyForUnknownExercise() = runTest {
        val sessionId = createSession()
        logDao.insertAll(listOf(makeLog(sessionId, exerciseId = "Bench_Press")))

        val result = logDao.getByExerciseId("Nonexistent_Exercise")
        assertTrue(result.isEmpty())
    }

    @Test
    fun getRecentExerciseIds_returnsDistinctIds() = runTest {
        val sessionId = createSession()
        logDao.insertAll(listOf(
            makeLog(sessionId, exerciseId = "Bench_Press"),
            makeLog(sessionId, exerciseId = "Bench_Press"),
            makeLog(sessionId, exerciseId = "Incline_Press")
        ))

        val recent = logDao.getRecentExerciseIds("Chest", limit = 10)
        assertEquals(2, recent.size)
        assertTrue(recent.contains("Bench_Press"))
        assertTrue(recent.contains("Incline_Press"))
    }
}
