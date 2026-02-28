package com.astute.body.ui.home

import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.repository.UserPreferencesRepository
import com.astute.body.domain.generator.FakeWorkoutRepository
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.generator.WorkoutGenerator
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.ui.workout.ActiveWorkoutState
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
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeWorkoutRepository()
        setupExercises()
        generator = WorkoutGenerator(repository, MuscleGroupScorer())
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

    @Test
    fun `init generates workout plan`() = runTest {
        viewModel = HomeViewModel(generator, repository, ActiveWorkoutState(), prefsRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.workoutPlan)
        assertTrue(state.workoutPlan!!.muscleGroupAllocations.isNotEmpty())
    }

    @Test
    fun `needsSetup when no equipment configured`() = runTest {
        repository.preferences = UserPreferencesEntity(availableEquipment = emptyList())
        viewModel = HomeViewModel(generator, repository, ActiveWorkoutState(), prefsRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.needsSetup)
        assertFalse(state.isLoading)
    }

    @Test
    fun `swapExercise updates plan with different exercise`() = runTest {
        viewModel = HomeViewModel(generator, repository, ActiveWorkoutState(), prefsRepo)
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
        viewModel = HomeViewModel(generator, repository, ActiveWorkoutState(), prefsRepo)
        advanceUntilIdle()

        val originalPlan = viewModel.uiState.value.workoutPlan!!

        viewModel.regenerateAll()
        advanceUntilIdle()

        val newPlan = viewModel.uiState.value.workoutPlan!!
        assertNotNull(newPlan)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `onSetupComplete clears needsSetup and generates workout`() = runTest {
        repository.preferences = UserPreferencesEntity(availableEquipment = emptyList())
        viewModel = HomeViewModel(generator, repository, ActiveWorkoutState(), prefsRepo)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsSetup)

        // Simulate user completing settings
        repository.preferences = UserPreferencesEntity(
            availableEquipment = listOf("barbell", "dumbbell", "body only"),
            experienceLevel = "intermediate"
        )
        viewModel.onSetupComplete()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsSetup)
        assertNotNull(viewModel.uiState.value.workoutPlan)
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
