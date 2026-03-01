package com.astute.body.ui.history

import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.PersonalRecordRecalculator
import com.astute.body.data.repository.UserPreferencesRepository
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
import org.junit.Assert.assertNotNull
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
    private lateinit var prefsDao: FakePrefsDao
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionDao = FakeWorkoutSessionDao()
        logDao = FakeExerciseLogDao()
        personalRecordDao = FakePersonalRecordDao()
        recalculator = PersonalRecordRecalculator(logDao, personalRecordDao, sessionDao)
        prefsDao = FakePrefsDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HistoryViewModel(sessionDao, logDao, recalculator, prefsRepo)

    @Test
    fun `sessions are loaded on init`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 2000L, muscleGroups = listOf("Chest", "Back"), completed = true),
            WorkoutSessionEntity(sessionId = 2, date = 1000L, muscleGroups = listOf("Legs"), completed = true)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.sessions.size)
        assertEquals(1L, state.sessions[0].sessionId)
        assertEquals(2L, state.sessions[1].sessionId)
    }

    @Test
    fun `empty state when no sessions`() = runTest {
        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

        viewModel = createViewModel()
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

    @Test
    fun `updateLog persists changes and refreshes displayed logs`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        val originalLog = ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0)
        logDao.logsBySession[1L] = listOf(originalLog)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        viewModel.updateLog(originalLog, sets = 4, reps = 8, weight = 155.0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedSessionLogs.size)
        val updatedLog = state.selectedSessionLogs[0]
        assertEquals(4, updatedLog.sets)
        assertEquals(8, updatedLog.reps)
        assertEquals(155.0, updatedLog.weight, 0.01)
        assertNull(state.editingLog)
    }

    @Test
    fun `updateLog triggers PR recalculation`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        val originalLog = ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0)
        logDao.logsBySession[1L] = listOf(originalLog)
        personalRecordDao.records["Bench_Press"] = PersonalRecordEntity(
            exerciseId = "Bench_Press", maxWeight = 135.0, maxReps = 10
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        viewModel.updateLog(originalLog, sets = 3, reps = 10, weight = 185.0)
        advanceUntilIdle()

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(185.0, pr!!.maxWeight, 0.01)
    }

    @Test
    fun `deleteLog removes log and refreshes displayed logs`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0),
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Fly", muscleGroup = "Chest", sets = 3, reps = 12, weight = 30.0)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        viewModel.deleteLog(1L, "Bench_Press")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedSessionLogs.size)
        assertEquals("Fly", state.selectedSessionLogs[0].exerciseId)
        assertEquals(1L, state.selectedSessionId)
    }

    @Test
    fun `deleteLog triggers PR recalculation`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 185.0),
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 8, weight = 135.0)
        )
        personalRecordDao.records["Bench_Press"] = PersonalRecordEntity(
            exerciseId = "Bench_Press", maxWeight = 185.0, maxReps = 10
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        // Delete the PR-holding log
        viewModel.deleteLog(1L, "Bench_Press")
        advanceUntilIdle()

        val pr = personalRecordDao.records["Bench_Press"]
        assertNotNull(pr)
        assertEquals(135.0, pr!!.maxWeight, 0.01)
        assertEquals(8, pr.maxReps)
    }

    @Test
    fun `deleteLog removes empty session when last log deleted`() = runTest {
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0)
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        viewModel.deleteLog(1L, "Bench_Press")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedSessionId)
        assertTrue(state.selectedSessionLogs.isEmpty())
        assertTrue(sessionDao.deletedIds.contains(1L))
    }

    @Test
    fun `deleteSession recalculates PRs for all affected exercises`() = runTest {
        // Session 1 has the PR-holding logs (higher values)
        sessionDao.sessions.value = listOf(
            WorkoutSessionEntity(sessionId = 1, date = 1000L, muscleGroups = listOf("Chest", "Legs"), completed = true),
            WorkoutSessionEntity(sessionId = 2, date = 2000L, muscleGroups = listOf("Chest", "Legs"), completed = true)
        )
        logDao.logsBySession[1L] = listOf(
            ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 185.0),
            ExerciseLogEntity(logId = 2, sessionId = 1, exerciseId = "Squat", muscleGroup = "Legs", sets = 3, reps = 8, weight = 315.0)
        )
        // Session 2 has lower values
        logDao.logsBySession[2L] = listOf(
            ExerciseLogEntity(logId = 3, sessionId = 2, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 8, weight = 135.0),
            ExerciseLogEntity(logId = 4, sessionId = 2, exerciseId = "Squat", muscleGroup = "Legs", sets = 3, reps = 6, weight = 225.0)
        )
        personalRecordDao.records["Bench_Press"] = PersonalRecordEntity(
            exerciseId = "Bench_Press", maxWeight = 185.0, maxReps = 10
        )
        personalRecordDao.records["Squat"] = PersonalRecordEntity(
            exerciseId = "Squat", maxWeight = 315.0, maxReps = 8
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectSession(1L)
        advanceUntilIdle()

        // Delete the session with the PR-holding logs
        viewModel.deleteSession(1L)
        advanceUntilIdle()

        // PRs should be recalculated to session 2's lower values
        val benchPR = personalRecordDao.records["Bench_Press"]
        assertNotNull(benchPR)
        assertEquals(135.0, benchPR!!.maxWeight, 0.01)
        assertEquals(8, benchPR.maxReps)

        val squatPR = personalRecordDao.records["Squat"]
        assertNotNull(squatPR)
        assertEquals(225.0, squatPR!!.maxWeight, 0.01)
        assertEquals(6, squatPR.maxReps)
    }

    @Test
    fun `startEditingLog and cancelEdit manage editing state correctly`() = runTest {
        val log = ExerciseLogEntity(logId = 1, sessionId = 1, exerciseId = "Bench_Press", muscleGroup = "Chest", sets = 3, reps = 10, weight = 135.0)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startEditingLog(log)
        assertEquals(log, viewModel.uiState.value.editingLog)

        viewModel.cancelEdit()
        assertNull(viewModel.uiState.value.editingLog)
    }
}

private class FakePrefsDao : UserPreferencesDao {
    var prefs: UserPreferencesEntity? = UserPreferencesEntity()

    override fun get(): Flow<UserPreferencesEntity?> = MutableStateFlow(prefs)

    override suspend fun getOnce(): UserPreferencesEntity? = prefs

    override suspend fun upsert(preferences: UserPreferencesEntity) {
        prefs = preferences
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
