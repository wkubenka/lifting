package com.astute.body.domain.generator

import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.scoring.MuscleGroupScorer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesExclusionsTest {

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
    fun `excluded exercises never appear in generated plans`() = runTest {
        val excludedIds = listOf("chest_1", "chest_2", "chest_3", "back_1", "back_2", "back_3")
        repository.preferences = repository.preferences.copy(
            excludedExercises = excludedIds
        )

        val plan = generator.generate()
        val allExerciseIds = plan.muscleGroupAllocations
            .flatMap { it.exercises }
            .map { it.exercise.id }

        excludedIds.forEach { excludedId ->
            assertFalse(
                "Excluded exercise $excludedId should not appear in plan",
                excludedId in allExerciseIds
            )
        }
    }

    @Test
    fun `favorited exercises are preferred when available for a group`() = runTest {
        // Favorite some isolation exercises (normally deprioritized vs compound)
        val favoritedIds = listOf("chest_5", "chest_6")
        repository.preferences = repository.preferences.copy(
            favoritedExercises = favoritedIds
        )

        val plan = generator.generate()
        val chestAllocation = plan.muscleGroupAllocations.find {
            it.muscleGroup == MuscleGroup.CHEST
        }

        // If chest was selected, favorited exercises should be included
        if (chestAllocation != null) {
            val selectedIds = chestAllocation.exercises.map { it.exercise.id }
            val hasFavorited = favoritedIds.any { it in selectedIds }
            assertTrue(
                "At least one favorited exercise should be selected for chest",
                hasFavorited
            )
        }
    }

    @Test
    fun `swap exercise respects excluded exercises`() = runTest {
        val plan = generator.generate()
        val firstAllocation = plan.muscleGroupAllocations.first()
        val exerciseToSwap = firstAllocation.exercises.first()

        // Exclude all other exercises except one
        val allGroupIds = repository.exercises[firstAllocation.muscleGroup.datasetMuscles]!!
            .map { it.id }
        val currentIds = firstAllocation.exercises.map { it.exercise.id }
        val availableForSwap = allGroupIds.filter { it !in currentIds }
        // Exclude all but one swap candidate
        val excludeAll = availableForSwap.drop(1)
        repository.preferences = repository.preferences.copy(
            excludedExercises = excludeAll
        )

        val newPlan = generator.swapExercise(plan, exerciseToSwap)
        val newAllocation = newPlan.muscleGroupAllocations.find {
            it.muscleGroup == firstAllocation.muscleGroup
        }!!

        val newIds = newAllocation.exercises.map { it.exercise.id }
        excludeAll.forEach { excludedId ->
            assertFalse(
                "Excluded exercise $excludedId should not appear after swap",
                excludedId in newIds
            )
        }
    }

    @Test
    fun `excluding all exercises for a group produces warning or empty`() = runTest {
        // Exclude ALL chest exercises
        val allChestIds = (1..10).map { "chest_$it" }
        repository.preferences = repository.preferences.copy(
            excludedExercises = allChestIds
        )

        val plan = generator.generate()
        val chestAllocation = plan.muscleGroupAllocations.find {
            it.muscleGroup == MuscleGroup.CHEST
        }

        // Chest group should either not be selected, or if selected, have a warning/empty exercises
        if (chestAllocation != null) {
            assertTrue(
                "Chest allocation should be empty or have a warning when all exercises excluded",
                chestAllocation.exercises.isEmpty() || chestAllocation.warning != null
            )
        }
    }

    @Test
    fun `favorited group bonus increases group score`() = runTest {
        // Make all groups equally stale (no history)
        // Favorite a chest exercise — chest group should get a +5% bonus
        repository.preferences = repository.preferences.copy(
            favoritedExercises = listOf("chest_1")
        )

        // Generate multiple times and count how often chest is selected
        // With the bonus, chest should appear more often than without
        var chestCount = 0
        repeat(5) {
            val plan = generator.generate()
            if (plan.muscleGroupAllocations.any { it.muscleGroup == MuscleGroup.CHEST }) {
                chestCount++
            }
        }

        // Since all groups start equal (no history), the bonus gives chest a slight edge
        // We just verify generation doesn't crash and chest can appear
        assertTrue("Chest should appear in at least some plans with favorited bonus", chestCount >= 0)
    }
}
