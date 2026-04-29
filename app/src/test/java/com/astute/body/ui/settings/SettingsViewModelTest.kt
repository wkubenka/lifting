package com.astute.body.ui.settings

import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.UserPreferencesEntity
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var prefsDao: FakeUserPreferencesDao
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        prefsDao = FakeUserPreferencesDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads preferences`() = runTest {
        prefsDao.prefs = UserPreferencesEntity(targetWorkoutSize = 10)

        viewModel = SettingsViewModel(prefsDao, prefsRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(10, state.preferences.targetWorkoutSize)
    }

    @Test
    fun `setWeightUnit updates and persists`() = runTest {
        viewModel = SettingsViewModel(prefsDao, prefsRepo)
        advanceUntilIdle()

        viewModel.setWeightUnit("kg")
        advanceUntilIdle()

        assertEquals("kg", viewModel.uiState.value.preferences.weightUnit)
        assertEquals("kg", prefsDao.prefs?.weightUnit)
    }

    @Test
    fun `setTargetWorkoutSize updates and persists`() = runTest {
        viewModel = SettingsViewModel(prefsDao, prefsRepo)
        advanceUntilIdle()

        viewModel.setTargetWorkoutSize(10)
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.preferences.targetWorkoutSize)
        assertEquals(10, prefsDao.prefs?.targetWorkoutSize)
    }

    @Test
    fun `rest timer durations are clamped to minimum of 10`() = runTest {
        viewModel = SettingsViewModel(prefsDao, prefsRepo)
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
