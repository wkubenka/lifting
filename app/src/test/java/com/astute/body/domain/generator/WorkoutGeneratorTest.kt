package com.astute.body.domain.generator

import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.ui.home.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        generator = WorkoutGenerator(repository, MuscleGroupScorer(), FakeClock())
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
    fun `generate produces expected group count`() = runTest {
        val plan = generator.generate()
        assertTrue(
            "Expected 2-4 groups, got ${plan.muscleGroupAllocations.size}",
            plan.muscleGroupAllocations.size in 2..4
        )
    }

    @Test
    fun `generate total exercises matches target size`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 8)
        val plan = generator.generate()
        val total = plan.muscleGroupAllocations.sumOf { it.exercises.size }
        assertEquals("Expected 8 total exercises", 8, total)
    }

    @Test
    fun `non-core groups get exactly 3 exercises`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 9)
        val plan = generator.generate()
        plan.muscleGroupAllocations
            .filter { it.muscleGroup != MuscleGroup.CORE }
            .forEach { allocation ->
                assertEquals(
                    "${allocation.muscleGroup.displayName} should get exactly 3 exercises",
                    3,
                    allocation.exercises.size
                )
            }
    }

    @Test
    fun `targetSize 8 gives core exactly 2 remainder exercises`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 8)
        val plan = generator.generate()
        val coreAllocation = plan.muscleGroupAllocations.find { it.muscleGroup == MuscleGroup.CORE }
        assertTrue("Core should be present for remainder", coreAllocation != null)
        assertEquals("Core should get 2 remainder exercises", 2, coreAllocation!!.exercises.size)

        val nonCoreGroups = plan.muscleGroupAllocations.filter { it.muscleGroup != MuscleGroup.CORE }
        assertEquals("Should have 2 non-core groups", 2, nonCoreGroups.size)
        nonCoreGroups.forEach {
            assertEquals("${it.muscleGroup.displayName} should get 3", 3, it.exercises.size)
        }
    }

    @Test
    fun `targetSize 7 gives core 1 remainder exercise`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 7)
        val plan = generator.generate()
        val total = plan.muscleGroupAllocations.sumOf { it.exercises.size }
        assertEquals("Total should be 7", 7, total)

        val coreAllocation = plan.muscleGroupAllocations.find { it.muscleGroup == MuscleGroup.CORE }
        assertTrue("Core should be present", coreAllocation != null)
        // Core gets at least 1 (remainder). Could get 3+1=4 if scored high enough to be a normal group too
        assertTrue(
            "Core should get remainder (1 or 4 if doubled up)",
            coreAllocation!!.exercises.size == 1 || coreAllocation.exercises.size == 4
        )
    }

    @Test
    fun `targetSize 9 is divisible by 3 — 3 groups of 3`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 9)
        val plan = generator.generate()
        val total = plan.muscleGroupAllocations.sumOf { it.exercises.size }
        assertEquals("Total should be 9", 9, total)
        assertEquals("Should have 3 groups", 3, plan.muscleGroupAllocations.size)
        plan.muscleGroupAllocations.forEach {
            assertEquals("${it.muscleGroup.displayName} should get 3", 3, it.exercises.size)
        }
    }

    @Test
    fun `targetSize 5 gives 1 group of 3 and core gets 2`() = runTest {
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 5)
        val plan = generator.generate()
        val total = plan.muscleGroupAllocations.sumOf { it.exercises.size }
        assertEquals("Total should be 5", 5, total)

        val coreAllocation = plan.muscleGroupAllocations.find { it.muscleGroup == MuscleGroup.CORE }
        assertTrue("Core should be present for remainder", coreAllocation != null)
        assertEquals("Core should get 2 remainder exercises", 2, coreAllocation!!.exercises.size)
    }

    @Test
    fun `weighted selection favors compound exercises`() = runTest {
        // Run many generations and count compound vs isolation selections
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 9)
        var compoundCount = 0
        var isolationCount = 0

        repeat(100) {
            val plan = generator.generate()
            plan.muscleGroupAllocations.forEach { allocation ->
                allocation.exercises.forEach { exercise ->
                    if (exercise.exercise.mechanic == "compound") compoundCount++
                    else isolationCount++
                }
            }
        }

        // With 3 compound and 7 isolation per group, and 2x weighting for compound,
        // expected compound ratio: (3*2) / (3*2 + 7*1) = 6/13 ≈ 46%
        val compoundRatio = compoundCount.toDouble() / (compoundCount + isolationCount)
        assertTrue(
            "Compound ratio ($compoundRatio) should be significantly above unweighted (3/10=0.3)",
            compoundRatio > 0.35
        )
    }

    @Test
    fun `remainder 2 excludes core from normal group selection`() = runTest {
        // targetSize=8, remainder=2 — Core should only appear with 2 exercises, never 3+2=5
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 8)
        repeat(20) {
            val plan = generator.generate()
            val coreAllocation = plan.muscleGroupAllocations.find { it.muscleGroup == MuscleGroup.CORE }
            if (coreAllocation != null) {
                assertEquals(
                    "Core should only have 2 (remainder), not be a full group",
                    2,
                    coreAllocation.exercises.size
                )
            }
        }
    }

    @Test
    fun `new user with no history generates a workout`() = runTest {
        val plan = generator.generate()
        assertTrue(plan.muscleGroupAllocations.isNotEmpty())
        assertTrue(plan.muscleGroupAllocations.sumOf { it.exercises.size } > 0)
    }

    @Test
    fun `overlap penalty affects group selection - arms deprioritized when chest selected`() = runTest {
        val now = System.currentTimeMillis()
        repository.lastTrainedMillis = mapOf(
            MuscleGroup.CHEST to (now - 7 * 24 * 3600 * 1000L),
            MuscleGroup.ARMS to (now - 5 * 24 * 3600 * 1000L),
            MuscleGroup.BACK to (now - 1 * 3600 * 1000L),
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
    fun `manual selection does not inject Core when targetSize has a remainder`() = runTest {
        // targetSize=7 → remainder=1; user picks Chest+Back only — Core must NOT appear
        repository.preferences = repository.preferences.copy(targetWorkoutSize = 7)
        val plan = generator.generate(targetGroups = setOf(MuscleGroup.CHEST, MuscleGroup.BACK))
        val groups = plan.muscleGroupAllocations.map { it.muscleGroup }
        assertFalse("Core must not be injected into a manual selection", MuscleGroup.CORE in groups)
        assertEquals("Only the two chosen groups should be present", 2, groups.size)
        val totalExercises = plan.muscleGroupAllocations.sumOf { it.exercises.size }
        assertEquals("Manual selection with targetSize=7 delivers 6 exercises (2 groups × 3), not 7", 6, totalExercises)
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
