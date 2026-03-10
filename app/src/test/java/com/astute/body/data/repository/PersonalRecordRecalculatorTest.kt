package com.astute.body.data.repository

import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PersonalRecordRecalculatorTest {

    private lateinit var logDao: FakeExerciseLogDao
    private lateinit var personalRecordDao: FakePersonalRecordDao
    private lateinit var sessionDao: FakeWorkoutSessionDao
    private lateinit var recalculator: PersonalRecordRecalculator

    @Before
    fun setup() {
        logDao = FakeExerciseLogDao()
        personalRecordDao = FakePersonalRecordDao()
        sessionDao = FakeWorkoutSessionDao()
        recalculator = PersonalRecordRecalculator(logDao, personalRecordDao, sessionDao)
    }

    @Test
    fun `recalculate with multiple logs finds correct max weight`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0),
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 8, weight = 185.0),
            ExerciseLogEntity(logId = 3, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 12, weight = 115.0)
        )

        recalculator.recalculateForExercise("Bench_Press")

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(185.0, pr!!.maxWeight, 0.01)
    }

    @Test
    fun `recalculate with multiple logs finds correct max reps`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0),
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 8, weight = 185.0),
            ExerciseLogEntity(logId = 3, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 15, weight = 95.0)
        )

        recalculator.recalculateForExercise("Bench_Press")

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(15, pr!!.maxReps)
    }

    @Test
    fun `recalculate updates lastPerformed to most recent session date`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true),
            WorkoutSessionEntity(sessionId = 2, date = 5000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 185.0)
        )
        logDao.logsBySession[2L] = listOf(
            ExerciseLogEntity(logId = 2, sessionId = 2, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 8, weight = 135.0)
        )

        recalculator.recalculateForExercise("Bench_Press")

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(5000L, pr!!.lastPerformed)
        assertEquals(135.0, pr.lastWeight, 0.01)
        assertEquals(8, pr.lastReps)
    }

    @Test
    fun `recalculate after deleting PR-holding log lowers PR`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        // Simulate: the 185lb log was already deleted, only 135lb remains
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 8, weight = 135.0)
        )
        // Old PR was 185 from a now-deleted log
        personalRecordDao.records["Bench_Press"] = PersonalRecordEntity(
            exerciseId = "Bench_Press", maxWeight = 185.0, maxReps = 10
        )

        recalculator.recalculateForExercise("Bench_Press")

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(135.0, pr!!.maxWeight, 0.01)
        assertEquals(8, pr.maxReps)
    }

    @Test
    fun `recalculate after editing log to exceed current PR updates PR`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        // Log was edited to 225lb (above the old 185 PR)
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 225.0)
        )
        personalRecordDao.records["Bench_Press"] = PersonalRecordEntity(
            exerciseId = "Bench_Press", maxWeight = 185.0, maxReps = 10
        )

        recalculator.recalculateForExercise("Bench_Press")

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(225.0, pr!!.maxWeight, 0.01)
    }

    @Test
    fun `recalculate with no remaining logs deletes PR`() = runTest {
        personalRecordDao.records["Bench_Press"] = PersonalRecordEntity(
            exerciseId = "Bench_Press", maxWeight = 185.0, maxReps = 10
        )
        // No logs remain for this exercise

        recalculator.recalculateForExercise("Bench_Press")

        assertNull(personalRecordDao.records["Bench_Press"])
    }

    @Test
    fun `recalculate tracks max weight date correctly across sessions`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true),
            WorkoutSessionEntity(sessionId = 2, date = 2000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 185.0)
        )
        logDao.logsBySession[2L] = listOf(
            ExerciseLogEntity(logId = 2, sessionId = 2, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 12, weight = 135.0)
        )

        recalculator.recalculateForExercise("Bench_Press")

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(1000L, pr!!.maxWeightDate)
        assertEquals(2000L, pr.maxRepsDate)
    }
}

private class FakeWorkoutSessionDao : WorkoutSessionDao {
    val sessions = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())

    override fun getAllOrderedByDate(): Flow<List<WorkoutSessionEntity>> = sessions

    override suspend fun getById(sessionId: Long): WorkoutSessionEntity? =
        sessions.value.find { it.sessionId == sessionId }

    override suspend fun getSessionsSince(sinceEpochMillis: Long): List<WorkoutSessionEntity> =
        sessions.value.filter { it.date >= sinceEpochMillis }

    override suspend fun getLastTrainedDate(muscleGroup: String): Long? =
        sessions.value.filter { muscleGroup in it.muscleGroups }.maxByOrNull { it.date }?.date

    override suspend fun getSessionCountSince(muscleGroup: String, sinceEpochMillis: Long): Int =
        sessions.value.count { muscleGroup in it.muscleGroups && it.date >= sinceEpochMillis }

    override suspend fun insert(session: WorkoutSessionEntity): Long {
        val id = (sessions.value.maxOfOrNull { it.sessionId } ?: 0) + 1
        sessions.value = sessions.value + session.copy(sessionId = id)
        return id
    }

    override suspend fun deleteById(sessionId: Long) {
        sessions.value = sessions.value.filter { it.sessionId != sessionId }
    }
}

private class FakeExerciseLogDao : ExerciseLogDao {
    val logsBySession = mutableMapOf<Long, List<ExerciseLogEntity>>()

    override suspend fun getBySessionId(sessionId: Long): List<ExerciseLogEntity> =
        logsBySession[sessionId] ?: emptyList()

    override suspend fun getByExerciseId(exerciseId: String): List<ExerciseLogEntity> =
        logsBySession.values.flatten().filter { it.exerciseId == exerciseId }

    override suspend fun getRecentExerciseIds(muscleGroup: String, limit: Int): List<String> =
        emptyList()

    override suspend fun insertAll(logs: List<ExerciseLogEntity>) {
        logs.groupBy { it.sessionId }.forEach { (sessionId, sessionLogs) ->
            logsBySession[sessionId] = (logsBySession[sessionId] ?: emptyList()) + sessionLogs
        }
    }

    override suspend fun update(log: ExerciseLogEntity) {
        logsBySession.forEach { (sessionId, logs) ->
            val index = logs.indexOfFirst { it.logId == log.logId }
            if (index >= 0) {
                logsBySession[sessionId] = logs.toMutableList().apply { set(index, log) }
            }
        }
    }

    override suspend fun deleteById(logId: Long) {
        logsBySession.forEach { (sessionId, logs) ->
            logsBySession[sessionId] = logs.filter { it.logId != logId }
        }
    }
}

private class FakePersonalRecordDao : PersonalRecordDao {
    val records = mutableMapOf<String, PersonalRecordEntity>()

    override suspend fun getByExerciseId(exerciseId: String): PersonalRecordEntity? =
        records[exerciseId]

    override suspend fun upsert(record: PersonalRecordEntity) {
        records[record.exerciseId] = record
    }

    override suspend fun deleteByExerciseId(exerciseId: String) {
        records.remove(exerciseId)
    }
}
