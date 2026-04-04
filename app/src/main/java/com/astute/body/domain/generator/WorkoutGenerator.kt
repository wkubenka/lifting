package com.astute.body.domain.generator

import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.domain.AppClock
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.MuscleGroupAllocation
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.domain.model.WorkoutPlan
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.domain.scoring.MuscleGroupTrainingData
import com.astute.body.domain.scoring.ScoredMuscleGroup
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.random.Random

@Singleton
class WorkoutGenerator @Inject constructor(
    private val repository: IWorkoutRepository,
    private val scorer: MuscleGroupScorer,
    private val clock: AppClock
) {
    companion object {
        const val EXERCISES_PER_GROUP = 3
        const val SCORE_THRESHOLD_RATIO = 0.20
        const val MAX_GROUPS = 4
        const val FAVORITED_GROUP_BONUS = 0.05
        const val COMPOUND_WEIGHT = 2.0
        const val ISOLATION_WEIGHT = 1.0
    }

    suspend fun generate(targetGroups: Set<MuscleGroup>? = null): WorkoutPlan {
        val prefs = repository.getUserPreferences()
        val targetSize = prefs.targetWorkoutSize
        val userExcludedIds = prefs.excludedExercises.toSet()
        val favoritedIds = prefs.favoritedExercises.toSet()

        val trainingData = buildTrainingData()
        val scores = scorer.scoreAll(trainingData)

        // Apply favorited group bonus: groups containing favorited exercises get +5%
        val favoritedGroups = findGroupsWithFavoritedExercises(favoritedIds)
        val boostedScores = scores.map { scored ->
            if (scored.muscleGroup in favoritedGroups) {
                scored.copy(score = scored.score * (1 + FAVORITED_GROUP_BONUS))
            } else scored
        }

        val selectedGroups = if (targetGroups != null) {
            // Use user-selected groups, preserving scores for allocation
            boostedScores.filter { it.muscleGroup in targetGroups }
                .ifEmpty { boostedScores.take(2) }
        } else {
            selectGroups(boostedScores, targetSize)
        }
        val allocations = allocateExercises(selectedGroups, targetSize, isManualSelection = targetGroups != null)

        val muscleGroupAllocations = allocations.map { (scored, count) ->
            val exercises = selectExercisesForGroup(
                scored.muscleGroup,
                count,
                prefs.availableEquipment,
                prefs.experienceLevel,
                userExcludedIds = userExcludedIds,
                favoritedIds = favoritedIds
            )
            val warning = when {
                exercises.isEmpty() -> "No exercises found for ${scored.muscleGroup.displayName}"
                exercises.size < count -> "Limited exercises available — showing ${exercises.size} of $count requested"
                else -> null
            }
            MuscleGroupAllocation(scored.muscleGroup, scored.score, exercises, warning)
        }

        return WorkoutPlan(muscleGroupAllocations.filter { it.exercises.isNotEmpty() })
    }

    suspend fun swapExercise(plan: WorkoutPlan, exerciseToReplace: PlannedExercise): WorkoutPlan {
        val prefs = repository.getUserPreferences()
        val userExcludedIds = prefs.excludedExercises.toSet()
        val allocation = plan.muscleGroupAllocations.find {
            it.muscleGroup == exerciseToReplace.muscleGroup
        } ?: return plan

        val currentIds = allocation.exercises.map { it.exercise.id }.toSet()
        val candidates = getFilteredExercises(
            exerciseToReplace.muscleGroup,
            prefs.availableEquipment,
            prefs.experienceLevel
        ).filter { it.id !in currentIds && it.id !in userExcludedIds }

        val replacement = candidates.firstOrNull() ?: return plan
        val newExercises = allocation.exercises.map {
            if (it.exercise.id == exerciseToReplace.exercise.id) {
                PlannedExercise(replacement, exerciseToReplace.muscleGroup)
            } else it
        }

        val newAllocations = plan.muscleGroupAllocations.map {
            if (it.muscleGroup == exerciseToReplace.muscleGroup) {
                it.copy(exercises = newExercises)
            } else it
        }
        return plan.copy(muscleGroupAllocations = newAllocations)
    }

    suspend fun regenerateGroup(plan: WorkoutPlan, muscleGroup: MuscleGroup): WorkoutPlan {
        val prefs = repository.getUserPreferences()
        val userExcludedIds = prefs.excludedExercises.toSet()
        val favoritedIds = prefs.favoritedExercises.toSet()
        val allocation = plan.muscleGroupAllocations.find {
            it.muscleGroup == muscleGroup
        } ?: return plan

        val lockedExercises = allocation.exercises.filter { it.isLocked }
        val neededCount = allocation.exercises.size - lockedExercises.size
        val excludeIds = lockedExercises.map { it.exercise.id }.toSet()

        val newExercises = selectExercisesForGroup(
            muscleGroup,
            neededCount,
            prefs.availableEquipment,
            prefs.experienceLevel,
            excludeIds,
            userExcludedIds = userExcludedIds,
            favoritedIds = favoritedIds
        )

        val combined = lockedExercises + newExercises
        val newAllocations = plan.muscleGroupAllocations.map {
            if (it.muscleGroup == muscleGroup) it.copy(exercises = combined) else it
        }
        return plan.copy(muscleGroupAllocations = newAllocations)
    }

    suspend fun regenerateAll(plan: WorkoutPlan, targetGroups: Set<MuscleGroup>? = null): WorkoutPlan {
        val lockedGroups = plan.muscleGroupAllocations.filter { allocation ->
            allocation.exercises.any { it.isLocked }
        }

        if (lockedGroups.isEmpty()) return generate(targetGroups)

        var newPlan = generate(targetGroups)
        for (lockedAllocation in lockedGroups) {
            val matchingAllocation = newPlan.muscleGroupAllocations.find {
                it.muscleGroup == lockedAllocation.muscleGroup
            }
            if (matchingAllocation != null) {
                val locked = lockedAllocation.exercises.filter { it.isLocked }
                val unlocked = matchingAllocation.exercises.filter { newEx ->
                    locked.none { it.exercise.id == newEx.exercise.id }
                }.take(matchingAllocation.exercises.size - locked.size)

                val combined = locked + unlocked
                newPlan = newPlan.copy(
                    muscleGroupAllocations = newPlan.muscleGroupAllocations.map {
                        if (it.muscleGroup == lockedAllocation.muscleGroup) {
                            it.copy(exercises = combined)
                        } else it
                    }
                )
            }
        }
        return newPlan
    }

    private suspend fun buildTrainingData(): List<MuscleGroupTrainingData> {
        val now = clock.now()
        val configs = repository.getAllRecoveryConfigs()
        val configMap = configs.associateBy { it.muscleGroup }

        return MuscleGroup.entries.map { group ->
            val lastTrainedMillis = repository.getLastTrainedMillis(group)
            val hoursSinceTrained = lastTrainedMillis?.let {
                (now - it).toDouble() / (1000 * 60 * 60)
            }
            val sessions = repository.getSessionCountLast14Days(group, now)
            val minRecovery = configMap[group.displayName]?.minRecoveryHours ?: 48

            MuscleGroupTrainingData(group, hoursSinceTrained, sessions, minRecovery)
        }
    }

    private fun selectGroups(
        scores: List<ScoredMuscleGroup>,
        targetSize: Int
    ): List<ScoredMuscleGroup> {
        val remainder = targetSize % 3
        val maxGroups = (targetSize / 3).coerceAtMost(MAX_GROUPS)

        // When remainder == 2, Core is reserved for remainder only — exclude from normal selection
        val excludeCore = remainder == 2
        val eligible = scores
            .filter { !excludeCore || it.muscleGroup != MuscleGroup.CORE }
            .sortedByDescending { it.score }
        if (eligible.isEmpty()) return emptyList()

        val topScore = eligible.first().score
        val threshold = topScore * SCORE_THRESHOLD_RATIO

        val selected = mutableListOf<ScoredMuscleGroup>()

        for (candidate in eligible) {
            if (selected.size >= maxGroups) break

            // Hard constraint: never select both leg groups
            val isLeg = candidate.muscleGroup == MuscleGroup.LEGS_PUSH ||
                    candidate.muscleGroup == MuscleGroup.LEGS_PULL
            val hasLeg = selected.any {
                it.muscleGroup == MuscleGroup.LEGS_PUSH || it.muscleGroup == MuscleGroup.LEGS_PULL
            }
            if (isLeg && hasLeg) continue

            val adjustedScore = scorer.applyOverlapPenalty(
                candidate.score,
                candidate.muscleGroup,
                selected.map { it.muscleGroup }
            )

            if (selected.size >= 2 && adjustedScore < threshold) break

            selected.add(candidate.copy(score = adjustedScore))
        }

        return selected
    }

    private fun allocateExercises(
        groups: List<ScoredMuscleGroup>,
        targetSize: Int,
        isManualSelection: Boolean = false
    ): List<Pair<ScoredMuscleGroup, Int>> {
        if (groups.isEmpty()) return emptyList()

        val remainder = targetSize % 3

        // Each selected group gets exactly 3 exercises
        val allocations = groups.map { it to EXERCISES_PER_GROUP }.toMutableList()

        // Add Core remainder when targetSize is not divisible by 3
        if (remainder > 0) {
            val coreAlready = allocations.indexOfFirst { it.first.muscleGroup == MuscleGroup.CORE }
            if (coreAlready >= 0) {
                // Core was manually selected or auto-selected as a normal group.
                // Only inflate it with remainder during auto-selection — if the user
                // explicitly chose Core, leave it at its normal 3-exercise allocation.
                if (!isManualSelection) {
                    allocations[coreAlready] = allocations[coreAlready].let { (g, c) -> g to c + remainder }
                }
            } else {
                // Add Core as a new group with just the remainder exercises
                val coreScore = ScoredMuscleGroup(MuscleGroup.CORE, 0.0, 0.0, 0.0, 0.0)
                allocations.add(coreScore to remainder)
            }
        }

        return allocations
    }

    private suspend fun selectExercisesForGroup(
        muscleGroup: MuscleGroup,
        count: Int,
        equipment: List<String>,
        level: String,
        excludeIds: Set<String> = emptySet(),
        userExcludedIds: Set<String> = emptySet(),
        favoritedIds: Set<String> = emptySet()
    ): List<PlannedExercise> {
        val allExcluded = excludeIds + userExcludedIds

        var candidates = getFilteredExercises(muscleGroup, equipment, level)
            .filter { it.id !in allExcluded }

        if (candidates.size < count) {
            candidates = repository.getExercisesForMusclesRelaxed(
                muscleGroup.datasetMuscles, equipment
            ).filter { it.id !in allExcluded }
        }

        if (candidates.isEmpty()) return emptyList()

        val recentIds = repository.getRecentExerciseIds(muscleGroup, 20).toSet()

        // Sort candidates into priority tiers: favorited+not-recent > favorited > not-recent > other
        // Within each tier, use weighted random sampling: compound = 2x chance vs isolation
        val selected = candidates
            .groupBy { Pair(it.id in favoritedIds, it.id !in recentIds) }
            .toSortedMap(compareByDescending<Pair<Boolean, Boolean>> { it.first }
                .thenByDescending { it.second })
            .flatMap { (_, exercises) -> weightedShuffle(exercises) }
            .take(count)

        return selected.map { PlannedExercise(it, muscleGroup) }
    }

    private suspend fun findGroupsWithFavoritedExercises(
        favoritedIds: Set<String>
    ): Set<MuscleGroup> {
        if (favoritedIds.isEmpty()) return emptySet()
        val exercises = repository.getExercisesByIds(favoritedIds.toList())
        val groups = mutableSetOf<MuscleGroup>()
        for (exercise in exercises) {
            for (group in MuscleGroup.entries) {
                if (exercise.primaryMuscles.any { it in group.datasetMuscles }) {
                    groups.add(group)
                }
            }
        }
        return groups
    }

    /**
     * Weighted shuffle: compound exercises get [COMPOUND_WEIGHT] (2x) chance,
     * isolation/null get [ISOLATION_WEIGHT] (1x). Uses the classic weighted
     * reservoir sampling trick: sort by random^(1/weight) descending.
     */
    private fun weightedShuffle(exercises: List<ExerciseEntity>): List<ExerciseEntity> {
        return exercises.map { exercise ->
            val weight = if (exercise.mechanic == "compound") COMPOUND_WEIGHT else ISOLATION_WEIGHT
            val key = Random.nextDouble().pow(1.0 / weight)
            exercise to key
        }.sortedByDescending { it.second }.map { it.first }
    }

    private suspend fun getFilteredExercises(
        muscleGroup: MuscleGroup,
        equipment: List<String>,
        level: String
    ): List<ExerciseEntity> {
        return repository.getExercisesForMuscles(
            muscleGroup.datasetMuscles, equipment, level
        )
    }
}
