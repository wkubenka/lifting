package com.astute.body.ui.home

import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.repository.UserPreferencesRepository
import com.astute.body.domain.AppClock
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
    private lateinit var clock: FakeClock
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeWorkoutRepository()
        clock = FakeClock()
        setupExercises()
        generator = WorkoutGenerator(repository, MuscleGroupScorer(), clock)
        prefsDao = FakePrefsDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
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
        viewModel = HomeViewModel(generator, repository, prefsRepo, clock)
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
        assertNotNull(repository.activeWorkout)
        assertEquals(0, repository.activeWorkout!!.currentIndex)
    }

    @Test
    fun `restoreWorkout restores state from persisted ActiveWorkoutEntity`() = runTest {
        // Add exercises to the repository so they can be found by ID
        val benchPress = makeTestExercise("bench_press", "Bench Press", listOf("chest"))
        val squat = makeTestExercise("squat", "Squat", listOf("quadriceps"))
        repository.exercises = repository.exercises + mapOf(
            setOf("chest") to listOf(benchPress),
            setOf("quadriceps") to listOf(squat)
        )

        repository.activeWorkout = ActiveWorkoutEntity(
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
    fun `logSet preserves half-pound decimal weights`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        viewModel.updateReps(8)
        viewModel.updateWeight(132.5)
        viewModel.logSet()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentExerciseSets.size)
        assertEquals(132.5, state.currentExerciseSets[0].weight, 0.001)

        viewModel.updateWeight(137.5)
        viewModel.logSet()
        advanceUntilIdle()

        val updated = viewModel.uiState.value
        assertEquals(2, updated.currentExerciseSets.size)
        assertEquals(137.5, updated.currentExerciseSets[1].weight, 0.001)
    }

    @Test
    fun `completeExercise creates separate log entries for different weights`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        // Log sets with varying weight and reps
        viewModel.updateReps(8)
        viewModel.updateWeight(135.0)
        viewModel.logSet()
        viewModel.skipTimer()

        viewModel.updateReps(8)
        viewModel.updateWeight(135.0)
        viewModel.logSet()
        viewModel.skipTimer()

        viewModel.updateReps(6)
        viewModel.updateWeight(155.0)
        viewModel.logSet()
        viewModel.skipTimer()
        advanceUntilIdle()

        viewModel.completeExercise()
        advanceUntilIdle()

        val entries = viewModel.uiState.value.logEntries
        assertEquals(2, entries.size)

        val light = entries.find { it.weight == 135.0 }!!
        assertEquals(2, light.sets)
        assertEquals(8, light.reps)

        val heavy = entries.find { it.weight == 155.0 }!!
        assertEquals(1, heavy.sets)
        assertEquals(6, heavy.reps)
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
        val benchPress = makeTestExercise("bench_press", "Bench Press", listOf("chest"))
        repository.exercises = repository.exercises + mapOf(
            setOf("chest") to listOf(benchPress)
        )

        repository.activeWorkout = ActiveWorkoutEntity(
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
        assertNull(repository.activeWorkout)
        assertTrue(viewModel.uiState.value.logEntries.isEmpty())
    }

    @Test
    fun `discardWorkout clears DB and resets to PLANNING`() = runTest {
        val benchPress = makeTestExercise("bench_press", "Bench Press", listOf("chest"))
        repository.exercises = repository.exercises + mapOf(
            setOf("chest") to listOf(benchPress)
        )

        repository.activeWorkout = ActiveWorkoutEntity(
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
        assertNull(repository.activeWorkout)
    }

    @Test
    fun `moveExercise during active workout only allows reordering upcoming`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        if (state.flatExercises.size >= 3) {
            // Try to move current exercise (index 0) — should be blocked
            viewModel.moveExercise(0, 1)
            assertEquals(state.flatExercises[0].exercise.id, viewModel.uiState.value.flatExercises[0].exercise.id)

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
        instructions = emptyList()
    )
}

// --- Fakes ---

private class FakePrefsDao : UserPreferencesDao {
    var prefs: UserPreferencesEntity? = UserPreferencesEntity()

    override fun get(): Flow<UserPreferencesEntity?> = MutableStateFlow(prefs)

    override suspend fun getOnce(): UserPreferencesEntity? = prefs

    override suspend fun upsert(preferences: UserPreferencesEntity) {
        prefs = preferences
    }
}

class FakeClock(var currentTimeMillis: Long = System.currentTimeMillis()) : AppClock {
    override fun now(): Long = currentTimeMillis
}
