package com.astute.body.domain.generator

import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.MuscleGroupAllocation
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.domain.model.WorkoutPlan
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.domain.scoring.MuscleGroupTrainingData
import com.astute.body.domain.scoring.ScoredMuscleGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutGenerator @Inject constructor(
    private val repository: IWorkoutRepository,
    private val scorer: MuscleGroupScorer
) {
    companion object {
        const val MIN_EXERCISES_PER_GROUP = 2
        const val SCORE_THRESHOLD_RATIO = 0.20
        const val MAX_GROUPS = 4
        const val MIN_GROUPS = 2
        const val FAVORITED_GROUP_BONUS = 0.05
    }

    suspend fun generate(): WorkoutPlan {
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

        val selectedGroups = selectGroups(boostedScores, targetSize)
        val allocations = allocateExercises(selectedGroups, targetSize)

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

    suspend fun regenerateAll(plan: WorkoutPlan): WorkoutPlan {
        val lockedGroups = plan.muscleGroupAllocations.filter { allocation ->
            allocation.exercises.any { it.isLocked }
        }

        if (lockedGroups.isEmpty()) return generate()

        var newPlan = generate()
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
        val now = System.currentTimeMillis()
        val configs = repository.getAllRecoveryConfigs()
        val configMap = configs.associateBy { it.muscleGroup }

        return MuscleGroup.entries.map { group ->
            val lastTrainedMillis = repository.getLastTrainedMillis(group)
            val hoursSinceTrained = lastTrainedMillis?.let {
                (now - it).toDouble() / (1000 * 60 * 60)
            }
            val sessions = repository.getSessionCountLast14Days(group)
            val minRecovery = configMap[group.displayName]?.minRecoveryHours ?: 48

            MuscleGroupTrainingData(group, hoursSinceTrained, sessions, minRecovery)
        }
    }

    private fun selectGroups(
        scores: List<ScoredMuscleGroup>,
        targetSize: Int
    ): List<ScoredMuscleGroup> {
        val sorted = scores.sortedByDescending { it.score }
        if (sorted.isEmpty()) return emptyList()

        val topScore = sorted.first().score
        val threshold = topScore * SCORE_THRESHOLD_RATIO

        val selected = mutableListOf<ScoredMuscleGroup>()
        var totalExercises = 0

        for (candidate in sorted) {
            if (selected.size >= MAX_GROUPS) break

            val adjustedScore = scorer.applyOverlapPenalty(
                candidate.score,
                candidate.muscleGroup,
                selected.map { it.muscleGroup }
            )

            if (selected.size >= MIN_GROUPS && adjustedScore < threshold) break
            if (selected.size >= MIN_GROUPS && totalExercises + MIN_EXERCISES_PER_GROUP > targetSize) break

            selected.add(candidate.copy(score = adjustedScore))
            totalExercises += MIN_EXERCISES_PER_GROUP
        }

        return selected
    }

    private fun allocateExercises(
        groups: List<ScoredMuscleGroup>,
        targetSize: Int
    ): List<Pair<ScoredMuscleGroup, Int>> {
        if (groups.isEmpty()) return emptyList()

        val totalScore = groups.sumOf { it.score }
        if (totalScore <= 0) {
            return groups.map { it to MIN_EXERCISES_PER_GROUP }
        }

        val allocations = groups.map { group ->
            val proportion = group.score / totalScore
            val raw = (proportion * targetSize).toInt().coerceAtLeast(MIN_EXERCISES_PER_GROUP)
            group to raw
        }.toMutableList()

        var total = allocations.sumOf { it.second }
        while (total > targetSize && allocations.any { it.second > MIN_EXERCISES_PER_GROUP }) {
            val idx = allocations.indices
                .filter { allocations[it].second > MIN_EXERCISES_PER_GROUP }
                .minByOrNull { allocations[it].first.score }
                ?: break
            allocations[idx] = allocations[idx].let { (g, c) -> g to (c - 1) }
            total--
        }

        while (total < targetSize) {
            val idx = allocations.indices.maxByOrNull { allocations[it].first.score } ?: break
            allocations[idx] = allocations[idx].let { (g, c) -> g to (c + 1) }
            total++
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

        // Sort candidates: favorited compound first, then favorited non-compound,
        // then non-favorited compound, then rest. Within each tier, prefer non-recent.
        val sorted = candidates.sortedWith(
            compareByDescending<ExerciseEntity> { it.id in favoritedIds }
                .thenByDescending { it.mechanic == "compound" }
                .thenBy { it.id in recentIds }
        )

        val selected = mutableListOf<ExerciseEntity>()
        selected.add(sorted.first())

        for (exercise in sorted.drop(1)) {
            if (selected.size >= count) break
            selected.add(exercise)
        }

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
