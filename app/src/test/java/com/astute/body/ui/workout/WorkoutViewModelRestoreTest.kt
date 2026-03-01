package com.astute.body.ui.workout

import com.astute.body.data.local.dao.ActiveWorkoutDao
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.UserPreferencesRepository
import com.astute.body.domain.generator.FakeWorkoutRepository
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
class WorkoutViewModelRestoreTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeWorkoutRepository
    private lateinit var workoutSessionDao: FakeWorkoutSessionDao
    private lateinit var exerciseLogDao: FakeExerciseLogDao
    private lateinit var personalRecordDao: FakePersonalRecordDao
    private lateinit var prefsDao: FakePrefsDao
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var activeWorkoutDao: FakeActiveWorkoutDao
    private lateinit var exerciseDao: FakeExerciseDao

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeWorkoutRepository()
        workoutSessionDao = FakeWorkoutSessionDao()
        exerciseLogDao = FakeExerciseLogDao()
        personalRecordDao = FakePersonalRecordDao()
        prefsDao = FakePrefsDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
        activeWorkoutDao = FakeActiveWorkoutDao()
        exerciseDao = FakeExerciseDao()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = WorkoutViewModel(
        repository, workoutSessionDao, exerciseLogDao,
        personalRecordDao, prefsRepo, activeWorkoutDao, exerciseDao
    )

    @Test
    fun `restoreWorkout restores state from persisted ActiveWorkoutEntity`() = runTest {
        // Set up exercises that the restore will look up
        exerciseDao.exerciseMap["bench_press"] = makeExercise("bench_press", "Bench Press", listOf("chest"))
        exerciseDao.exerciseMap["squat"] = makeExercise("squat", "Squat", listOf("quadriceps"))

        // Pre-populate active workout with serialized state at exercise index 1
        activeWorkoutDao.entity = ActiveWorkoutEntity(
            exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"},{"exerciseId":"squat","muscleGroup":"Legs (Push)"}]""",
            currentIndex = 1,
            logEntries = """[{"exerciseId":"bench_press","exerciseName":"Bench Press","muscleGroup":"Chest","sets":3,"reps":10,"weight":135.0}]""",
            setsCompleted = 2,
            currentSets = 4,
            currentReps = 8,
            currentWeight = 225.0,
            startedAtMillis = 5000L,
            newPRs = "[]"
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.exercises.size)
        assertEquals(1, state.currentIndex)
        assertEquals(1, state.logEntries.size)
        assertEquals("bench_press", state.logEntries[0].exerciseId)
        assertEquals(2, state.setsCompleted)
        assertEquals(4, state.currentSets)
        assertEquals(8, state.currentReps)
        assertEquals(225.0, state.currentWeight, 0.01)
        assertEquals(5000L, state.startTimeMillis)
    }

    @Test
    fun `saveWorkout clears persisted state`() = runTest {
        // Set up a workout with one exercise already logged
        exerciseDao.exerciseMap["bench_press"] = makeExercise("bench_press", "Bench Press", listOf("chest"))

        activeWorkoutDao.entity = ActiveWorkoutEntity(
            exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"}]""",
            currentIndex = 0,
            logEntries = """[{"exerciseId":"bench_press","exerciseName":"Bench Press","muscleGroup":"Chest","sets":3,"reps":10,"weight":135.0}]""",
            setsCompleted = 0,
            currentSets = 3,
            currentReps = 10,
            currentWeight = 0.0,
            startedAtMillis = 1000L,
            newPRs = "[]"
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Complete the workout (advance past last exercise to trigger isComplete)
        viewModel.completeExercise()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isComplete)

        viewModel.saveWorkout()
        advanceUntilIdle()

        assertNull(activeWorkoutDao.entity)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `discardWorkout clears persisted state`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeExercise("bench_press", "Bench Press", listOf("chest"))

        activeWorkoutDao.entity = ActiveWorkoutEntity(
            exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"}]""",
            currentIndex = 0,
            logEntries = "[]",
            setsCompleted = 0,
            currentSets = 3,
            currentReps = 10,
            currentWeight = 0.0,
            startedAtMillis = 1000L,
            newPRs = "[]"
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.discardWorkout()
        advanceUntilIdle()

        assertNull(activeWorkoutDao.entity)
    }

    private fun makeExercise(id: String, name: String, muscles: List<String>) = ExerciseEntity(
        id = id,
        name = name,
        force = "push",
        level = "intermediate",
        mechanic = "compound",
        equipment = "barbell",
        category = "strength",
        primaryMuscles = muscles,
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        images = emptyList()
    )
}

private class FakeWorkoutSessionDao : WorkoutSessionDao {
    val sessions = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())

    override fun getAllOrderedByDate(): Flow<List<WorkoutSessionEntity>> = sessions

    override suspend fun getById(sessionId: Long): WorkoutSessionEntity? =
        sessions.value.find { it.sessionId == sessionId }

    override suspend fun getSessionsSince(sinceEpochMillis: Long): List<WorkoutSessionEntity> =
        sessions.value.filter { it.date >= sinceEpochMillis }

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

    override suspend fun update(log: ExerciseLogEntity) {}

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

private class FakePrefsDao : UserPreferencesDao {
    var prefs: UserPreferencesEntity? = UserPreferencesEntity()

    override fun get(): Flow<UserPreferencesEntity?> = MutableStateFlow(prefs)

    override suspend fun getOnce(): UserPreferencesEntity? = prefs

    override suspend fun upsert(preferences: UserPreferencesEntity) {
        prefs = preferences
    }
}

private class FakeActiveWorkoutDao : ActiveWorkoutDao {
    var entity: ActiveWorkoutEntity? = null

    override suspend fun get(): ActiveWorkoutEntity? = entity

    override suspend fun upsert(entity: ActiveWorkoutEntity) {
        this.entity = entity
    }

    override suspend fun clear() {
        entity = null
    }
}

private class FakeExerciseDao : ExerciseDao {
    val exerciseMap = mutableMapOf<String, ExerciseEntity>()

    override fun getAll(): Flow<List<ExerciseEntity>> =
        MutableStateFlow(exerciseMap.values.toList())

    override suspend fun getById(id: String): ExerciseEntity? = exerciseMap[id]

    override suspend fun getByIds(ids: List<String>): List<ExerciseEntity> =
        ids.mapNotNull { exerciseMap[it] }

    override suspend fun getByMuscle(muscle: String): List<ExerciseEntity> =
        exerciseMap.values.filter { muscle in it.primaryMuscles }

    override suspend fun getDistinctEquipment(): List<String> =
        exerciseMap.values.mapNotNull { it.equipment }.distinct()

    override suspend fun count(): Int = exerciseMap.size

    override suspend fun insertAll(exercises: List<ExerciseEntity>) {
        exercises.forEach { exerciseMap[it.id] = it }
    }
}
