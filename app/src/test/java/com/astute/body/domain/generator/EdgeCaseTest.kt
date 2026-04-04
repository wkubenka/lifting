package com.astute.body.domain.generator

import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.ui.home.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class EdgeCaseTest {

    private lateinit var repository: FakeWorkoutRepository
    private lateinit var generator: WorkoutGenerator

    @Before
    fun setup() {
        repository = FakeWorkoutRepository()
        generator = WorkoutGenerator(repository, MuscleGroupScorer(), FakeClock())
        setupExercises()
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
    fun `new user with no training history generates a valid workout`() = runTest {
        // No lastTrainedMillis, no sessionCounts — all default to null/0
        val plan = generator.generate()

        assertNotNull(plan)
        assertTrue(plan.muscleGroupAllocations.isNotEmpty())
        assertTrue(plan.muscleGroupAllocations.all { it.exercises.isNotEmpty() })
    }

    @Test
    fun `all muscle groups recently trained still generates a workout`() = runTest {
        val now = System.currentTimeMillis()
        // All groups trained very recently (1 hour ago)
        repository.lastTrainedMillis = MuscleGroup.entries.associateWith { now - 3600_000L }
        repository.sessionCounts = MuscleGroup.entries.associateWith { 3 }

        val plan = generator.generate()

        assertNotNull(plan)
        assertTrue(plan.muscleGroupAllocations.isNotEmpty())
        assertTrue("Should still generate exercises", plan.muscleGroupAllocations.sumOf { it.exercises.size } >= 2)
    }

    @Test
    fun `limited exercises for a group produces a warning`() = runTest {
        // Give only 1 exercise for chest
        val limitedExercises = repository.exercises.toMutableMap()
        val chestMuscles = MuscleGroup.CHEST.datasetMuscles
        limitedExercises[chestMuscles] = listOf(
            makeExercise("bench_only", chestMuscles.toList().take(1), "compound")
        )
        repository.exercises = limitedExercises

        val plan = generator.generate()

        val chestAllocation = plan.muscleGroupAllocations.find { it.muscleGroup == MuscleGroup.CHEST }
        if (chestAllocation != null) {
            // If chest was selected and allocated more than 1 exercise, there should be a warning
            if (chestAllocation.exercises.size < 2) {
                assertNotNull("Expected a warning for limited exercises", chestAllocation.warning)
            }
        }
    }

    @Test
    fun `empty exercises for a group are filtered out of plan`() = runTest {
        // Remove all exercises for chest
        val limitedExercises = repository.exercises.toMutableMap()
        limitedExercises[MuscleGroup.CHEST.datasetMuscles] = emptyList()
        repository.exercises = limitedExercises

        val plan = generator.generate()

        val chestAllocation = plan.muscleGroupAllocations.find { it.muscleGroup == MuscleGroup.CHEST }
        if (chestAllocation != null) {
            assertTrue("Empty groups should be filtered out", chestAllocation.exercises.isNotEmpty())
        }
    }

    @Test
    fun `workout plan respects custom target workout size`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 5)

        val plan = generator.generate()
        val totalExercises = plan.muscleGroupAllocations.sumOf { it.exercises.size }

        assertEquals("Should have exactly 5 exercises", 5, totalExercises)
    }

    @Test
    fun `workout plan respects large target workout size`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 12)

        val plan = generator.generate()
        val totalExercises = plan.muscleGroupAllocations.sumOf { it.exercises.size }

        assertEquals("Should have exactly 12 exercises", 12, totalExercises)
    }

    @Test
    fun `targetSize less than 3 throws IllegalArgumentException`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 2)

        try {
            generator.generate()
            fail("Expected IllegalArgumentException for targetSize=2")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("3") == true)
        }
    }

    @Test
    fun `targetSize of 1 throws IllegalArgumentException`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 1)

        try {
            generator.generate()
            fail("Expected IllegalArgumentException for targetSize=1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("3") == true)
        }
    }

    @Test
    fun `new user generates equal scoring across muscle groups`() = runTest {
        // With no history, all groups should score equally (50.0)
        val scorer = MuscleGroupScorer()
        val trainingData = MuscleGroup.entries.map { group ->
            com.astute.body.domain.scoring.MuscleGroupTrainingData(
                muscleGroup = group,
                hoursSinceTrained = null,
                sessionsLast14Days = 0,
                minRecoveryHours = 48
            )
        }

        val scores = scorer.scoreAll(trainingData)
        assertTrue(scores.all { it.score == 50.0 })
    }
}
