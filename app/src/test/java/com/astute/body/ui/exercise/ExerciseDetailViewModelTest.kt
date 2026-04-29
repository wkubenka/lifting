package com.astute.body.ui.exercise

import androidx.lifecycle.SavedStateHandle
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.ExerciseEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var exerciseDao: DetailFakeExerciseDao
    private lateinit var prefsDao: DetailFakePrefsDao
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        exerciseDao = DetailFakeExerciseDao()
        prefsDao = DetailFakePrefsDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createSavedStateHandle(exerciseId: String): SavedStateHandle =
        SavedStateHandle(mapOf("exerciseId" to exerciseId))

    private fun createViewModel(exerciseId: String): ExerciseDetailViewModel =
        ExerciseDetailViewModel(createSavedStateHandle(exerciseId), exerciseDao, prefsRepo, prefsDao)

    @Test
    fun `loading valid exerciseId populates exercise in state`() = runTest {
        val exercise = makeExercise("Bench Press", id = "bench_press")
        exerciseDao.exerciseMap["bench_press"] = exercise

        val viewModel = createViewModel("bench_press")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.exercise)
        assertEquals("Bench Press", state.exercise!!.name)
        assertNull(state.error)
    }

    @Test
    fun `loading invalid exerciseId sets error state`() = runTest {
        val viewModel = createViewModel("nonexistent")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.exercise)
        assertEquals("Exercise not found", state.error)
    }

    @Test
    fun `favorite and excluded flags reflect preferences`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeExercise("Bench Press", id = "bench_press")
        prefsDao.prefs = UserPreferencesEntity(favoritedExercises = listOf("bench_press"))

        val viewModel = createViewModel("bench_press")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isFavorited)
        assertFalse(state.isExcluded)
    }

    @Test
    fun `toggleFavorite adds to favorites`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeExercise("Bench Press", id = "bench_press")
        prefsDao.prefs = UserPreferencesEntity()

        val viewModel = createViewModel("bench_press")
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(prefsDao.prefs!!.favoritedExercises.contains("bench_press"))
    }

    @Test
    fun `toggleFavorite removes from excluded`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeExercise("Bench Press", id = "bench_press")
        prefsDao.prefs = UserPreferencesEntity(excludedExercises = listOf("bench_press"))

        val viewModel = createViewModel("bench_press")
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        val prefs = prefsDao.prefs!!
        assertTrue(prefs.favoritedExercises.contains("bench_press"))
        assertFalse(prefs.excludedExercises.contains("bench_press"))
    }

    @Test
    fun `toggleExclude adds to excluded and removes from favorites`() = runTest {
        exerciseDao.exerciseMap["bench_press"] = makeExercise("Bench Press", id = "bench_press")
        prefsDao.prefs = UserPreferencesEntity(favoritedExercises = listOf("bench_press"))

        val viewModel = createViewModel("bench_press")
        advanceUntilIdle()

        viewModel.toggleExclude()
        advanceUntilIdle()

        val prefs = prefsDao.prefs!!
        assertTrue(prefs.excludedExercises.contains("bench_press"))
        assertFalse(prefs.favoritedExercises.contains("bench_press"))
    }

    private fun makeExercise(name: String, id: String = name.lowercase().replace(" ", "_")) =
        ExerciseEntity(
            id = id,
            name = name,
            force = "push",
            level = "intermediate",
            mechanic = "compound",
            equipment = "barbell",
            category = "strength",
            primaryMuscles = listOf("chest"),
            secondaryMuscles = emptyList(),
            instructions = listOf("Step 1", "Step 2")
        )
}

private class DetailFakeExerciseDao : ExerciseDao {
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

private class DetailFakePrefsDao : UserPreferencesDao {
    private val flow = MutableStateFlow<UserPreferencesEntity?>(UserPreferencesEntity())

    var prefs: UserPreferencesEntity?
        get() = flow.value
        set(value) { flow.value = value }

    override fun get(): Flow<UserPreferencesEntity?> = flow

    override suspend fun getOnce(): UserPreferencesEntity? = prefs

    override suspend fun upsert(preferences: UserPreferencesEntity) {
        prefs = preferences
    }
}
