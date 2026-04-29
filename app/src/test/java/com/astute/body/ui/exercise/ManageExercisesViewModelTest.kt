package com.astute.body.ui.exercise

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageExercisesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var exerciseDao: ManageFakeExerciseDao
    private lateinit var prefsDao: ManageFakePrefsDao
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var viewModel: ManageExercisesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        exerciseDao = ManageFakeExerciseDao()
        prefsDao = ManageFakePrefsDao()
        prefsRepo = UserPreferencesRepository(prefsDao)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ManageExercisesViewModel(exerciseDao, prefsRepo, prefsDao)

    @Test
    fun `init loads exercises sorted by name`() = runTest {
        exerciseDao.allExercises.value = listOf(
            makeExercise("Squat"),
            makeExercise("Bench Press"),
            makeExercise("Deadlift")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.allExercises.size)
        assertEquals("Bench Press", state.allExercises[0].name)
        assertEquals("Deadlift", state.allExercises[1].name)
        assertEquals("Squat", state.allExercises[2].name)
    }

    @Test
    fun `init loads favorites and excluded from preferences`() = runTest {
        exerciseDao.allExercises.value = listOf(makeExercise("Bench Press"))
        prefsDao.prefs = UserPreferencesEntity(
            favoritedExercises = listOf("bench_press"),
            excludedExercises = listOf("squat")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.favoritedIds.contains("bench_press"))
        assertTrue(state.excludedIds.contains("squat"))
    }

    @Test
    fun `setSearchQuery filters exercises by name case-insensitive`() = runTest {
        exerciseDao.allExercises.value = listOf(
            makeExercise("Bench Press"),
            makeExercise("Incline Bench Press"),
            makeExercise("Squat")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("bench")

        val filtered = viewModel.uiState.value.filteredExercises
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.name.contains("Bench", ignoreCase = true) })
    }

    @Test
    fun `setSearchQuery with blank shows all exercises`() = runTest {
        exerciseDao.allExercises.value = listOf(
            makeExercise("Bench Press"),
            makeExercise("Squat")
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("bench")
        assertEquals(1, viewModel.uiState.value.filteredExercises.size)

        viewModel.setSearchQuery("")
        assertEquals(2, viewModel.uiState.value.filteredExercises.size)
    }

    @Test
    fun `setFilterTab FAVORITES shows only favorited exercises`() = runTest {
        exerciseDao.allExercises.value = listOf(
            makeExercise("Bench Press", id = "bench_press"),
            makeExercise("Squat", id = "squat")
        )
        prefsDao.prefs = UserPreferencesEntity(favoritedExercises = listOf("bench_press"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilterTab(FilterTab.FAVORITES)

        val filtered = viewModel.uiState.value.filteredExercises
        assertEquals(1, filtered.size)
        assertEquals("bench_press", filtered[0].id)
    }

    @Test
    fun `setFilterTab EXCLUDED shows only excluded exercises`() = runTest {
        exerciseDao.allExercises.value = listOf(
            makeExercise("Bench Press", id = "bench_press"),
            makeExercise("Squat", id = "squat")
        )
        prefsDao.prefs = UserPreferencesEntity(excludedExercises = listOf("squat"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilterTab(FilterTab.EXCLUDED)

        val filtered = viewModel.uiState.value.filteredExercises
        assertEquals(1, filtered.size)
        assertEquals("squat", filtered[0].id)
    }

    @Test
    fun `toggleFavorite adds to favorites and removes from excluded`() = runTest {
        exerciseDao.allExercises.value = listOf(makeExercise("Squat", id = "squat"))
        prefsDao.prefs = UserPreferencesEntity(excludedExercises = listOf("squat"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleFavorite("squat")
        advanceUntilIdle()

        val prefs = prefsDao.prefs!!
        assertTrue(prefs.favoritedExercises.contains("squat"))
        assertFalse(prefs.excludedExercises.contains("squat"))
    }

    @Test
    fun `toggleFavorite removes when already favorited`() = runTest {
        exerciseDao.allExercises.value = listOf(makeExercise("Bench Press", id = "bench_press"))
        prefsDao.prefs = UserPreferencesEntity(favoritedExercises = listOf("bench_press"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleFavorite("bench_press")
        advanceUntilIdle()

        assertFalse(prefsDao.prefs!!.favoritedExercises.contains("bench_press"))
    }

    @Test
    fun `toggleExcluded adds to excluded and removes from favorites`() = runTest {
        exerciseDao.allExercises.value = listOf(makeExercise("Bench Press", id = "bench_press"))
        prefsDao.prefs = UserPreferencesEntity(favoritedExercises = listOf("bench_press"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleExcluded("bench_press")
        advanceUntilIdle()

        val prefs = prefsDao.prefs!!
        assertTrue(prefs.excludedExercises.contains("bench_press"))
        assertFalse(prefs.favoritedExercises.contains("bench_press"))
    }

    @Test
    fun `toggleExcluded removes when already excluded`() = runTest {
        exerciseDao.allExercises.value = listOf(makeExercise("Squat", id = "squat"))
        prefsDao.prefs = UserPreferencesEntity(excludedExercises = listOf("squat"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleExcluded("squat")
        advanceUntilIdle()

        assertFalse(prefsDao.prefs!!.excludedExercises.contains("squat"))
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
            instructions = emptyList()
        )
}

private class ManageFakeExerciseDao : ExerciseDao {
    val allExercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())

    override fun getAll(): Flow<List<ExerciseEntity>> = allExercises

    override suspend fun getById(id: String): ExerciseEntity? =
        allExercises.value.find { it.id == id }

    override suspend fun getByIds(ids: List<String>): List<ExerciseEntity> =
        allExercises.value.filter { it.id in ids }

    override suspend fun getByMuscle(muscle: String): List<ExerciseEntity> =
        allExercises.value.filter { muscle in it.primaryMuscles }

    override suspend fun getDistinctEquipment(): List<String> =
        allExercises.value.mapNotNull { it.equipment }.distinct()

    override suspend fun count(): Int = allExercises.value.size

    override suspend fun insertAll(exercises: List<ExerciseEntity>) {
        allExercises.value = allExercises.value + exercises
    }
}

private class ManageFakePrefsDao : UserPreferencesDao {
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
