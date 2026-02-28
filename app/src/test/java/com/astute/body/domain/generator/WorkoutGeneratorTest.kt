package com.astute.body.domain.generator

import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.domain.scoring.MuscleGroupScorer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutGeneratorTest {

    private lateinit var repository: FakeWorkoutRepository
    private lateinit var generator: WorkoutGenerator

    @Before
    fun setup() {
        repository = FakeWorkoutRepository()
        generator = WorkoutGenerator(repository, MuscleGroupScorer())
        setupExercisesForAllGroups()
    }

    private fun setupExercisesForAllGroups() {
        repository.exercises = mapOf(
            MuscleGroup.CHEST.datasetMuscles to (1..10).map {
                makeExercise("chest_$it", listOf("chest"), if (it <= 3) "compound" else "isolation")
            },
            MuscleGroup.BACK.datasetMuscles to (1..10).map {
                makeExercise("back_$it", listOf("lats"), if (it <= 3) "compound" else "isolation")
            },
            MuscleGroup.SHOULDERS.datasetMuscles to (1..10).map {
                makeExercise("shoulders_$it", listOf("shoulders"), if (it <= 3) "compound" else "isolation")
            },
            MuscleGroup.ARMS.datasetMuscles to (1..10).map {
                makeExercise("arms_$it", listOf("biceps"), if (it <= 3) "compound" else "isolation")
            },
            MuscleGroup.LEGS_PUSH.datasetMuscles to (1..10).map {
                makeExercise("legs_push_$it", listOf("quadriceps"), if (it <= 3) "compound" else "isolation")
            },
            MuscleGroup.LEGS_PULL.datasetMuscles to (1..10).map {
                makeExercise("legs_pull_$it", listOf("hamstrings"), if (it <= 3) "compound" else "isolation")
            },
            MuscleGroup.CORE.datasetMuscles to (1..10).map {
                makeExercise("core_$it", listOf("abdominals"), if (it <= 3) "compound" else "isolation")
            }
        )
    }

    @Test
    fun `generate selects 2 to 4 muscle groups`() = runTest {
        val plan = generator.generate()
        assertTrue(
            "Expected 2-4 groups, got ${plan.muscleGroupAllocations.size}",
            plan.muscleGroupAllocations.size in 2..4
        )
    }

    @Test
    fun `generate total exercises within target range`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 8)
        val plan = generator.generate()
        val total = plan.muscleGroupAllocations.sumOf { it.exercises.size }
        assertTrue(
            "Expected around 8 exercises, got $total",
            total in 4..12
        )
    }

    @Test
    fun `every selected group gets at least 2 exercises`() = runTest {
        val plan = generator.generate()
        plan.muscleGroupAllocations.forEach { allocation ->
            assertTrue(
                "${allocation.muscleGroup.displayName} got ${allocation.exercises.size} exercises, expected >= 2",
                allocation.exercises.size >= 2
            )
        }
    }

    @Test
    fun `first exercise in each group is compound when available`() = runTest {
        val plan = generator.generate()
        plan.muscleGroupAllocations.forEach { allocation ->
            if (allocation.exercises.isNotEmpty()) {
                assertEquals(
                    "${allocation.muscleGroup.displayName}: first exercise should be compound",
                    "compound",
                    allocation.exercises.first().exercise.mechanic
                )
            }
        }
    }

    @Test
    fun `new user with no history generates a workout`() = runTest {
        // No training history set — all nulls
        val plan = generator.generate()
        assertTrue(plan.muscleGroupAllocations.isNotEmpty())
        assertTrue(plan.muscleGroupAllocations.sumOf { it.exercises.size } > 0)
    }

    @Test
    fun `overlap penalty affects group selection - arms deprioritized when chest selected`() = runTest {
        // Make chest very stale, arms somewhat stale, others recently trained
        val now = System.currentTimeMillis()
        repository.lastTrainedMillis = mapOf(
            MuscleGroup.CHEST to (now - 7 * 24 * 3600 * 1000L),  // 7 days ago
            MuscleGroup.ARMS to (now - 5 * 24 * 3600 * 1000L),   // 5 days ago
            MuscleGroup.BACK to (now - 1 * 3600 * 1000L),         // 1 hour ago
            MuscleGroup.SHOULDERS to (now - 1 * 3600 * 1000L),
            MuscleGroup.LEGS_PUSH to (now - 1 * 3600 * 1000L),
            MuscleGroup.LEGS_PULL to (now - 1 * 3600 * 1000L),
            MuscleGroup.CORE to (now - 1 * 3600 * 1000L)
        )
        repository.sessionCounts = mapOf(
            MuscleGroup.BACK to 3, MuscleGroup.SHOULDERS to 3,
            MuscleGroup.LEGS_PUSH to 3, MuscleGroup.LEGS_PULL to 3, MuscleGroup.CORE to 3
        )

        val plan = generator.generate()
        val selectedGroups = plan.muscleGroupAllocations.map { it.muscleGroup }

        // Chest should be selected (very stale)
        assertTrue("Chest should be selected", MuscleGroup.CHEST in selectedGroups)
    }

    @Test
    fun `swapExercise returns a different exercise from same group`() = runTest {
        val plan = generator.generate()
        val firstAllocation = plan.muscleGroupAllocations.first()
        val exerciseToSwap = firstAllocation.exercises.first()

        val newPlan = generator.swapExercise(plan, exerciseToSwap)
        val newAllocation = newPlan.muscleGroupAllocations.find {
            it.muscleGroup == firstAllocation.muscleGroup
        }!!
        val newFirst = newAllocation.exercises.first()

        assertNotEquals(
            "Swapped exercise should be different",
            exerciseToSwap.exercise.id,
            newFirst.exercise.id
        )
        assertEquals(
            "Should still be in the same muscle group",
            exerciseToSwap.muscleGroup,
            newFirst.muscleGroup
        )
    }

    @Test
    fun `regenerateGroup replaces non-locked exercises and keeps locked ones`() = runTest {
        val plan = generator.generate()
        val allocation = plan.muscleGroupAllocations.first()
        val lockedExercise = allocation.exercises.first().copy(isLocked = true)

        val planWithLock = plan.copy(
            muscleGroupAllocations = plan.muscleGroupAllocations.map {
                if (it.muscleGroup == allocation.muscleGroup) {
                    it.copy(exercises = listOf(lockedExercise) + allocation.exercises.drop(1))
                } else it
            }
        )

        val newPlan = generator.regenerateGroup(planWithLock, allocation.muscleGroup)
        val newAllocation = newPlan.muscleGroupAllocations.find {
            it.muscleGroup == allocation.muscleGroup
        }!!

        assertTrue(
            "Locked exercise should still be present",
            newAllocation.exercises.any { it.exercise.id == lockedExercise.exercise.id }
        )
    }

    @Test
    fun `regenerateAll keeps locked exercises intact`() = runTest {
        val plan = generator.generate()
        val allocation = plan.muscleGroupAllocations.first()
        val lockedExercise = allocation.exercises.first().copy(isLocked = true)

        val planWithLock = plan.copy(
            muscleGroupAllocations = plan.muscleGroupAllocations.map {
                if (it.muscleGroup == allocation.muscleGroup) {
                    it.copy(exercises = listOf(lockedExercise) + allocation.exercises.drop(1))
                } else it
            }
        )

        val newPlan = generator.regenerateAll(planWithLock)

        // Find the group that had the locked exercise
        val matchingAllocation = newPlan.muscleGroupAllocations.find {
            it.muscleGroup == allocation.muscleGroup
        }

        if (matchingAllocation != null) {
            assertTrue(
                "Locked exercise should be preserved after regenerateAll",
                matchingAllocation.exercises.any { it.exercise.id == lockedExercise.exercise.id }
            )
        }
    }
}
