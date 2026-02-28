package com.astute.body.ui.settings

import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var prefsDao: FakeUserPreferencesDao
    private lateinit var exerciseDao: FakeExerciseDao
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        prefsDao = FakeUserPreferencesDao()
        exerciseDao = FakeExerciseDao()
        exerciseDao.equipment = listOf("barbell", "dumbbell", "cable", "body only", "machine")
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads preferences and equipment list`() = runTest {
        prefsDao.prefs = UserPreferencesEntity(
            experienceLevel = "intermediate",
            availableEquipment = listOf("barbell", "dumbbell"),
            targetWorkoutSize = 10
        )

        viewModel = SettingsViewModel(prefsDao, exerciseDao)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("intermediate", state.preferences.experienceLevel)
        assertEquals(listOf("barbell", "dumbbell"), state.preferences.availableEquipment)
        assertEquals(10, state.preferences.targetWorkoutSize)
        assertEquals(5, state.allEquipment.size)
    }

    @Test
    fun `setExperienceLevel updates and persists`() = runTest {
        viewModel = SettingsViewModel(prefsDao, exerciseDao)
        advanceUntilIdle()

        viewModel.setExperienceLevel("expert")
        advanceUntilIdle()

        assertEquals("expert", viewModel.uiState.value.preferences.experienceLevel)
        assertEquals("expert", prefsDao.prefs?.experienceLevel)
    }

    @Test
    fun `setWeightUnit updates and persists`() = runTest {
        viewModel = SettingsViewModel(prefsDao, exerciseDao)
        advanceUntilIdle()

        viewModel.setWeightUnit("kg")
        advanceUntilIdle()

        assertEquals("kg", viewModel.uiState.value.preferences.weightUnit)
        assertEquals("kg", prefsDao.prefs?.weightUnit)
    }

    @Test
    fun `setTargetWorkoutSize updates and persists`() = runTest {
        viewModel = SettingsViewModel(prefsDao, exerciseDao)
        advanceUntilIdle()

        viewModel.setTargetWorkoutSize(10)
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.preferences.targetWorkoutSize)
        assertEquals(10, prefsDao.prefs?.targetWorkoutSize)
    }

    @Test
    fun `toggleEquipment adds and removes equipment`() = runTest {
        viewModel = SettingsViewModel(prefsDao, exerciseDao)
        advanceUntilIdle()

        viewModel.toggleEquipment("barbell")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preferences.availableEquipment.contains("barbell"))

        viewModel.toggleEquipment("dumbbell")
        advanceUntilIdle()
        assertEquals(listOf("barbell", "dumbbell"), viewModel.uiState.value.preferences.availableEquipment)

        viewModel.toggleEquipment("barbell")
        advanceUntilIdle()
        assertEquals(listOf("dumbbell"), viewModel.uiState.value.preferences.availableEquipment)
    }

    @Test
    fun `rest timer durations are clamped to minimum of 10`() = runTest {
        viewModel = SettingsViewModel(prefsDao, exerciseDao)
        advanceUntilIdle()

        viewModel.setRestCompound(5)
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.preferences.restCompound)

        viewModel.setRestIsolation(3)
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.preferences.restIsolation)

        viewModel.setRestBodyweightAb(0)
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.preferences.restBodyweightAb)
    }
}

private class FakeUserPreferencesDao : UserPreferencesDao {
    var prefs: UserPreferencesEntity? = UserPreferencesEntity()

    override fun get(): Flow<UserPreferencesEntity?> = MutableStateFlow(prefs)

    override suspend fun getOnce(): UserPreferencesEntity? = prefs

    override suspend fun upsert(preferences: UserPreferencesEntity) {
        prefs = preferences
    }
}

private class FakeExerciseDao : ExerciseDao {
    var equipment: List<String> = emptyList()

    override fun getAll(): Flow<List<ExerciseEntity>> = MutableStateFlow(emptyList())

    override suspend fun getById(id: String): ExerciseEntity? = null

    override suspend fun getByMuscle(muscle: String): List<ExerciseEntity> = emptyList()

    override suspend fun getDistinctEquipment(): List<String> = equipment

    override suspend fun count(): Int = 0

    override suspend fun insertAll(exercises: List<ExerciseEntity>) {}
}
