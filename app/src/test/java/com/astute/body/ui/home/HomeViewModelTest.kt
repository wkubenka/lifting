package com.astute.body.ui.home

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
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.generator.WorkoutGenerator
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.scoring.MuscleGroupScorer
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeWorkoutRepository
    private lateinit var generator: WorkoutGenerator
    private lateinit var prefsDao: FakePrefsDao
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var activeWorkoutDao: FakeActiveWorkoutDao
    private lateinit var workoutSessionDao: FakeWorkoutSessionDao
    private lateinit var exerciseLogDao: FakeExerciseLogDao
    private lateinit var personalRecordDao: FakePersonalRecordDao
    private lateinit var exerciseDao: FakeExerciseDao
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeWorkoutRepository()
        setupExercises()
        generator = WorkoutGenerator(repository, MuscleGroupScorer())
        prefsDao = FakePrefsDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
        activeWorkoutDao = FakeActiveWorkoutDao()
        workoutSessionDao = FakeWorkoutSessionDao()
        exerciseLogDao = FakeExerciseLogDao()
        personalRecordDao = FakePersonalRecordDao()
        exerciseDao = FakeExerciseDao()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun setupExercises() {
        repository.exercises = MuscleGroup.entries.associate { group ->
            group.datasetMuscles to (1..10).map { i ->
                makeExercise(
                    "${group.name.lowercase()}_$i",
                    group.datasetMuscles.toList().take(1),
                    if (i <= 3) "compound" else "isolation"
                )
            }
        }
    }

    private fun createViewModel(): HomeViewModel {
        viewModel = HomeViewModel(
            generator, repository, activeWorkoutDao, prefsRepo,
            workoutSessionDao, exerciseLogDao, personalRecordDao, exerciseDao
        )
        return viewModel
    }

    // --- Planning mode tests ---

    @Test
    fun `init generates workout plan`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.workoutPlan)
        assertTrue(state.workoutPlan!!.muscleGroupAllocations.isNotEmpty())
    }

    @Test
    fun `needsSetup when no equipment configured`() = runTest {
        repository.preferences = UserPreferencesEntity(availableEquipment = emptyList())
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.needsSetup)
        assertFalse(state.isLoading)
    }

    @Test
    fun `swapExercise updates plan with different exercise`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val plan = viewModel.uiState.value.workoutPlan!!
        val firstExercise = plan.muscleGroupAllocations.first().exercises.first()
        val originalId = firstExercise.exercise.id

        viewModel.swapExercise(firstExercise)
        advanceUntilIdle()

        val newPlan = viewModel.uiState.value.workoutPlan!!
        val newFirstExercise = newPlan.muscleGroupAllocations.first().exercises.first()
        assertNotEquals(originalId, newFirstExercise.exercise.id)
    }

    @Test
    fun `regenerateAll produces a new plan`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.regenerateAll()
        advanceUntilIdle()

        val newPlan = viewModel.uiState.value.workoutPlan!!
        assertNotNull(newPlan)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `onSetupComplete clears needsSetup and generates workout`() = runTest {
        repository.preferences = UserPreferencesEntity(availableEquipment = emptyList())
        createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsSetup)

        repository.preferences = UserPreferencesEntity(
            availableEquipment = listOf("barbell", "dumbbell", "body only"),
            experienceLevel = "intermediate"
        )
        viewModel.onSetupComplete()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsSetup)
        assertNotNull(viewModel.uiState.value.workoutPlan)
    }

    // --- Workout lifecycle tests ---

    @Test
    fun `startWorkout sets workoutMode to ACTIVE`() = runTest {
        createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.workoutPlan)
        assertEquals(WorkoutMode.PLANNING, viewModel.uiState.value.workoutMode)

        viewModel.startWorkout()
        advanceUntilIdle()

        assertEquals(WorkoutMode.ACTIVE, viewModel.uiState.value.workoutMode)
        assertNotNull(activeWorkoutDao.entity)
        assertEquals(0, activeWorkoutDao.entity!!.currentIndex)
    }

    @Test
    fun `restoreWorkout restores state from persisted ActiveWorkoutEntity`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeTestExercise("bench_press", "Bench Press", listOf("chest"))
        exerciseDao.exerciseMap["squat"] = makeTestExercise("squat", "Squat", listOf("quadriceps"))

        activeWorkoutDao.entity = ActiveWorkoutEntity(
            exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"},{"exerciseId":"squat","muscleGroup":"Legs (Push)"}]""",
            currentIndex = 1,
            logEntries = """[{"exerciseId":"bench_press","exerciseName":"Bench Press","muscleGroup":"Chest","sets":3,"reps":10,"weight":135.0}]""",
            setsCompleted = 2,
            currentSets = 4,
            currentReps = 8,
            currentWeight = 225.0,
            startedAtMillis = 5000L,
            newPRs = "[]",
            currentExerciseSets = """[{"reps":8,"weight":225.0},{"reps":8,"weight":225.0}]"""
        )

        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WorkoutMode.ACTIVE, state.workoutMode)
        assertEquals(2, state.flatExercises.size)
        assertEquals(1, state.currentIndex)
        assertEquals(1, state.logEntries.size)
        assertEquals("bench_press", state.logEntries[0].exerciseId)
        assertEquals(2, state.setsCompleted)
        assertEquals(4, state.currentSets)
        assertEquals(8, state.currentReps)
        assertEquals(225.0, state.currentWeight, 0.01)
        assertEquals(5000L, state.startTimeMillis)
        assertEquals(2, state.currentExerciseSets.size)
    }

    @Test
    fun `logSet adds set and increments setsCompleted`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        viewModel.updateReps(10)
        viewModel.updateWeight(135.0)
        viewModel.logSet()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentExerciseSets.size)
        assertEquals(10, state.currentExerciseSets[0].reps)
        assertEquals(135.0, state.currentExerciseSets[0].weight, 0.01)
        assertEquals(1, state.setsCompleted)
    }

    @Test
    fun `completeExercise advances to next exercise`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        viewModel.updateReps(10)
        viewModel.updateWeight(135.0)
        viewModel.logSet()
        advanceUntilIdle()

        viewModel.skipTimer()
        val exercisesBefore = viewModel.uiState.value.flatExercises.size
        viewModel.completeExercise()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        if (exercisesBefore > 1) {
            assertEquals(1, state.currentIndex)
            assertEquals(1, state.logEntries.size)
            assertEquals(WorkoutMode.ACTIVE, state.workoutMode)
        } else {
            assertEquals(WorkoutMode.COMPLETE, state.workoutMode)
        }
    }

    @Test
    fun `skipExercise advances without creating log entry`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        viewModel.skipExercise()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.logEntries.isEmpty())
        if (state.flatExercises.size > 1) {
            assertEquals(1, state.currentIndex)
        }
    }

    @Test
    fun `finishEarly sets workoutMode to COMPLETE`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        viewModel.finishEarly()
        advanceUntilIdle()

        assertEquals(WorkoutMode.COMPLETE, viewModel.uiState.value.workoutMode)
    }

    @Test
    fun `saveWorkout clears active workout and resets to PLANNING`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeTestExercise("bench_press", "Bench Press", listOf("chest"))

        activeWorkoutDao.entity = ActiveWorkoutEntity(
            exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"}]""",
            currentIndex = 0,
            logEntries = """[{"exerciseId":"bench_press","exerciseName":"Bench Press","muscleGroup":"Chest","sets":3,"reps":10,"weight":135.0}]""",
            setsCompleted = 0,
            currentSets = 3,
            currentReps = 10,
            currentWeight = 0.0,
            startedAtMillis = 1000L,
            newPRs = "[]",
            currentExerciseSets = "[]"
        )

        createViewModel()
        advanceUntilIdle()

        // Complete the workout
        viewModel.completeExercise()
        advanceUntilIdle()

        assertEquals(WorkoutMode.COMPLETE, viewModel.uiState.value.workoutMode)

        viewModel.saveWorkout()
        advanceUntilIdle()

        assertEquals(WorkoutMode.PLANNING, viewModel.uiState.value.workoutMode)
        assertNull(activeWorkoutDao.entity)
        assertTrue(viewModel.uiState.value.logEntries.isEmpty())
    }

    @Test
    fun `discardWorkout clears DB and resets to PLANNING`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeTestExercise("bench_press", "Bench Press", listOf("chest"))

        activeWorkoutDao.entity = ActiveWorkoutEntity(
            exerciseRefs = """[{"exerciseId":"bench_press","muscleGroup":"Chest"}]""",
            currentIndex = 0,
            logEntries = "[]",
            setsCompleted = 0,
            currentSets = 3,
            currentReps = 10,
            currentWeight = 0.0,
            startedAtMillis = 1000L,
            newPRs = "[]",
            currentExerciseSets = "[]"
        )

        createViewModel()
        advanceUntilIdle()

        assertEquals(WorkoutMode.ACTIVE, viewModel.uiState.value.workoutMode)

        viewModel.discardWorkout()
        advanceUntilIdle()

        assertEquals(WorkoutMode.PLANNING, viewModel.uiState.value.workoutMode)
        assertNull(activeWorkoutDao.entity)
    }

    @Test
    fun `moveExercise during active workout only allows reordering upcoming`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        if (state.flatExercises.size >= 3) {
            val originalSecond = state.flatExercises[1].exercise.id

            // Try to move current exercise (index 0) — should be blocked
            viewModel.moveExercise(0, 1)
            assertEquals(state.flatExercises[0].exercise.id, viewModel.uiState.value.flatExercises[0].exercise.id)

            // Move upcoming exercises (index 1 to 2) — should also be blocked since index 1 is right after current
            // But moving 2 to 1 should be blocked (toIndex <= currentIndex not applicable since current is 0)
            // Move index 2 down (should work if there's a 3rd+)
            if (state.flatExercises.size >= 4) {
                val thirdId = state.flatExercises[2].exercise.id
                viewModel.moveExercise(2, 3)
                advanceUntilIdle()
                assertEquals(thirdId, viewModel.uiState.value.flatExercises[3].exercise.id)
            }
        }
    }

    // --- Helper ---

    private fun makeTestExercise(id: String, name: String, muscles: List<String>) = ExerciseEntity(
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

// --- Fake DAOs ---

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
