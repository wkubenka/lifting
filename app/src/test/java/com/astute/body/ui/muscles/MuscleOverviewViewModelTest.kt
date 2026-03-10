package com.astute.body.ui.muscles

import com.astute.body.domain.generator.FakeWorkoutRepository
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.ui.home.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MuscleOverviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeWorkoutRepository
    private lateinit var scorer: MuscleGroupScorer
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeWorkoutRepository()
        scorer = MuscleGroupScorer()
        clock = FakeClock()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `all 7 muscle groups are present in state`() = runTest {
        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(7, state.groups.size)
        assertEquals(
            MuscleGroup.entries.toSet(),
            state.groups.map { it.muscleGroup }.toSet()
        )
    }

    @Test
    fun `never trained group is OVERDUE with null lastTrainedMillis`() = runTest {
        // No lastTrainedMillis set for any group
        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val chest = viewModel.uiState.value.groups.find { it.muscleGroup == MuscleGroup.CHEST }!!
        assertEquals(FreshnessStatus.OVERDUE, chest.freshnessStatus)
        assertNull(chest.lastTrainedMillis)
        assertNull(chest.hoursSinceLastTrained)
    }

    @Test
    fun `recently trained group is RECOVERING`() = runTest {
        val now = System.currentTimeMillis()
        // Chest recovery is 48 hours; trained 12 hours ago → ratio 0.25 → RECOVERING
        repository.lastTrainedMillis = mapOf(
            MuscleGroup.CHEST to (now - 12 * 60 * 60 * 1000L)
        )

        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val chest = viewModel.uiState.value.groups.find { it.muscleGroup == MuscleGroup.CHEST }!!
        assertEquals(FreshnessStatus.RECOVERING, chest.freshnessStatus)
    }

    @Test
    fun `recovered group is READY`() = runTest {
        val now = System.currentTimeMillis()
        // Chest recovery is 48 hours; trained 60 hours ago → ratio 1.25 → READY
        repository.lastTrainedMillis = mapOf(
            MuscleGroup.CHEST to (now - 60 * 60 * 60 * 1000L)
        )

        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val chest = viewModel.uiState.value.groups.find { it.muscleGroup == MuscleGroup.CHEST }!!
        assertEquals(FreshnessStatus.READY, chest.freshnessStatus)
    }

    @Test
    fun `long since trained group is DUE`() = runTest {
        val now = System.currentTimeMillis()
        // Chest recovery is 48 hours; trained 120 hours ago → ratio 2.5 → DUE
        repository.lastTrainedMillis = mapOf(
            MuscleGroup.CHEST to (now - 120 * 60 * 60 * 1000L)
        )

        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val chest = viewModel.uiState.value.groups.find { it.muscleGroup == MuscleGroup.CHEST }!!
        assertEquals(FreshnessStatus.DUE, chest.freshnessStatus)
    }

    @Test
    fun `very long since trained group is OVERDUE`() = runTest {
        val now = System.currentTimeMillis()
        // Chest recovery is 48 hours; trained 200 hours ago → ratio 4.17 → OVERDUE
        repository.lastTrainedMillis = mapOf(
            MuscleGroup.CHEST to (now - 200 * 60 * 60 * 1000L)
        )

        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val chest = viewModel.uiState.value.groups.find { it.muscleGroup == MuscleGroup.CHEST }!!
        assertEquals(FreshnessStatus.OVERDUE, chest.freshnessStatus)
    }

    @Test
    fun `session counts match repository data`() = runTest {
        repository.sessionCounts = mapOf(
            MuscleGroup.CHEST to 3,
            MuscleGroup.BACK to 2,
            MuscleGroup.LEGS_PUSH to 1
        )

        val viewModel = MuscleOverviewViewModel(repository, scorer, clock)
        advanceUntilIdle()

        val groups = viewModel.uiState.value.groups
        assertEquals(3, groups.find { it.muscleGroup == MuscleGroup.CHEST }!!.sessionsLast14Days)
        assertEquals(2, groups.find { it.muscleGroup == MuscleGroup.BACK }!!.sessionsLast14Days)
        assertEquals(1, groups.find { it.muscleGroup == MuscleGroup.LEGS_PUSH }!!.sessionsLast14Days)
        assertEquals(0, groups.find { it.muscleGroup == MuscleGroup.ARMS }!!.sessionsLast14Days)
    }

    @Test
    fun `computeFreshnessStatus returns correct values at boundaries`() {
        assertEquals(FreshnessStatus.RECOVERING, MuscleOverviewViewModel.computeFreshnessStatus(0.0, false))
        assertEquals(FreshnessStatus.RECOVERING, MuscleOverviewViewModel.computeFreshnessStatus(0.99, false))
        assertEquals(FreshnessStatus.READY, MuscleOverviewViewModel.computeFreshnessStatus(1.0, false))
        assertEquals(FreshnessStatus.READY, MuscleOverviewViewModel.computeFreshnessStatus(1.99, false))
        assertEquals(FreshnessStatus.DUE, MuscleOverviewViewModel.computeFreshnessStatus(2.0, false))
        assertEquals(FreshnessStatus.DUE, MuscleOverviewViewModel.computeFreshnessStatus(3.99, false))
        assertEquals(FreshnessStatus.OVERDUE, MuscleOverviewViewModel.computeFreshnessStatus(4.0, false))
        assertEquals(FreshnessStatus.OVERDUE, MuscleOverviewViewModel.computeFreshnessStatus(10.0, false))
        assertEquals(FreshnessStatus.OVERDUE, MuscleOverviewViewModel.computeFreshnessStatus(0.0, true))
    }
}
