package com.astute.body.ui.history

import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.PersonalRecordRecalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionDao: FakeWorkoutSessionDao
    private lateinit var logDao: FakeExerciseLogDao
    private lateinit var personalRecordDao: FakePersonalRecordDao
    private lateinit var recalculator: PersonalRecordRecalculator
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionDao = FakeWorkoutSessionDao()
        logDao = FakeExerciseLogDao()
        personalRecordDao = FakePersonalRecordDao()
        recalculator = PersonalRecordRecalculator(logDao, personalRecordDao, sessionDao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sessions are loaded on init`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 2000L, muscleGroups = listOf("Chest", "Back"), completed = true),
            WorkoutSessionEntity(sessionId = 2, date = 1000L, muscleGroups = listOf("Legs"), completed = true)
        )

        viewModel = HistoryViewModel(sessionDao, logDao, recalculator)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.sessions.size)
        assertEquals(1L, state.sessions[0].sessionId)
        assertEquals(2L, state.sessions[1].sessionId)
    }

    @Test
    fun `empty state when no sessions`() = runTest {
        viewModel = HistoryViewModel(sessionDao, logDao, recalculator)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.sessions.isEmpty())
        assertNull(state.selectedSessionId)
    }

    @Test
    fun `selectSession loads exercise logs`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0),
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Incline_Dumbbell_Press", muscleGroup = "Chest", sets = 3, reps = 12, weight = 50.0)
        )

        viewModel = HistoryViewModel(sessionDao, logDao, recalculator)
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1L, state.selectedSessionId)
        assertEquals(2, state.selectedSessionLogs.size)
        assertEquals("Bench_Press", state.selectedSessionLogs[0].exerciseId)
    }

    @Test
    fun `selectSession toggles off when same session selected`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0)
        )

        viewModel = HistoryViewModel(sessionDao, logDao, recalculator)
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.selectedSessionId)

        viewModel.selectSession(1L)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedSessionId)
        assertTrue(viewModel.uiState.value.selectedSessionLogs.isEmpty())
    }

    @Test
    fun `deleteSession removes session and clears selection`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )

        viewModel = HistoryViewModel(sessionDao, logDao, recalculator)
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        viewModel.deleteSession(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedSessionId)
        assertTrue(state.selectedSessionLogs.isEmpty())
        assertTrue(sessionDao.deletedIds.contains(1L))
    }
}

private class FakeWorkoutSessionDao : WorkoutSessionDao {
    val sessions = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())
    val deletedIds = mutableListOf<Long>()

    override fun getAllOrderedByDate(): Flow<List<WorkoutSessionEntity>> = sessions

    override suspend fun getById(sessionId: Long): WorkoutSessionEntity? {
        return sessions.value.find { it.sessionId == sessionId }
    }

    override suspend fun getSessionsSince(sinceEpochMillis: Long): List<WorkoutSessionEntity> {
        return sessions.value.filter { it.date >= sinceEpochMillis }
    }

    override suspend fun insert(session: WorkoutSessionEntity): Long {
        val id = (sessions.value.maxOfOrNull { it.sessionId } ?: 0) + 1
        sessions.value = sessions.value + session.copy(sessionId = id)
        return id
    }

    override suspend fun deleteById(sessionId: Long) {
        deletedIds.add(sessionId)
        sessions.value = sessions.value.filter { it.sessionId != sessionId }
    }
}

private class FakeExerciseLogDao : ExerciseLogDao {
    val logsBySession = mutableMapOf<Long, List<ExerciseLogEntity>>()

    override suspend fun getBySessionId(sessionId: Long): List<ExerciseLogEntity> {
        return logsBySession[sessionId] ?: emptyList()
    }

    override suspend fun getByExerciseId(exerciseId: String): List<ExerciseLogEntity> {
        return logsBySession.values.flatten().filter { it.exerciseId == exerciseId }
    }

    override suspend fun getRecentExerciseIds(muscleGroup: String, limit: Int): List<String> {
        return emptyList()
    }

    override suspend fun insertAll(logs: List<ExerciseLogEntity>) {
        logs.groupBy { it.sessionId }.forEach { (sessionId, sessionLogs) ->
            logsBySession[sessionId] = (logsBySession[sessionId] ?: emptyList()) + sessionLogs
        }
    }
}

private class FakePersonalRecordDao : PersonalRecordDao {
    val records = mutableMapOf<String, PersonalRecordEntity>()

    override suspend fun getByExerciseId(exerciseId: String): PersonalRecordEntity? {
        return records[exerciseId]
    }

    override suspend fun upsert(record: PersonalRecordEntity) {
        records[record.exerciseId] = record
    }

    override suspend fun deleteByExerciseId(exerciseId: String) {
        records.remove(exerciseId)
    }
}
