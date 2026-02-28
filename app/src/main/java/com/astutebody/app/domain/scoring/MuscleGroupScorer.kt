package com.astutebody.app.domain.scoring

import com.astutebody.app.domain.model.MuscleGroup
import kotlin.math.ln
import kotlin.math.min

data class MuscleGroupTrainingData(
    val muscleGroup: MuscleGroup,
    val hoursSinceTrained: Double?,
    val sessionsLast14Days: Int,
    val minRecoveryHours: Int
)

data class ScoredMuscleGroup(
    val muscleGroup: MuscleGroup,
    val score: Double,
    val recoveryScore: Double,
    val stalenessScore: Double,
    val frequencyBalanceScore: Double
)

class MuscleGroupScorer {

    companion object {
        const val RECOVERY_WEIGHT = 0.35
        const val STALENESS_WEIGHT = 0.30
        const val FREQUENCY_BALANCE_WEIGHT = 0.25
        const val OVERLAP_PENALTY_FACTOR = 0.3
    }

    fun scoreAll(trainingData: List<MuscleGroupTrainingData>): List<ScoredMuscleGroup> {
        if (trainingData.isEmpty()) return emptyList()

        val allHaveNoHistory = trainingData.all { it.hoursSinceTrained == null }
        if (allHaveNoHistory) {
            return trainingData.map {
                ScoredMuscleGroup(it.muscleGroup, 50.0, 1.0, 1.0, 1.0)
            }
        }

        val maxHours = trainingData.mapNotNull { it.hoursSinceTrained }.maxOrNull() ?: 1.0
        val maxSessions = trainingData.maxOf { it.sessionsLast14Days }.coerceAtLeast(1)

        return trainingData.map { data ->
            val recovery = recoveryScore(data.hoursSinceTrained, data.minRecoveryHours)
            val staleness = stalenessScore(data.hoursSinceTrained, maxHours)
            val frequencyBalance = frequencyBalanceScore(data.sessionsLast14Days, maxSessions)

            val rawScore = (recovery * RECOVERY_WEIGHT +
                    staleness * STALENESS_WEIGHT +
                    frequencyBalance * FREQUENCY_BALANCE_WEIGHT) * 100

            ScoredMuscleGroup(data.muscleGroup, rawScore, recovery, staleness, frequencyBalance)
        }
    }

    fun applyOverlapPenalty(
        score: Double,
        candidate: MuscleGroup,
        alreadySelected: List<MuscleGroup>
    ): Double {
        val sharedRatio = MuscleGroup.sharedMuscleRatio(candidate, alreadySelected)
        return score * (1 - OVERLAP_PENALTY_FACTOR * sharedRatio)
    }

    fun recoveryScore(hoursSinceTrained: Double?, minRecoveryHours: Int): Double {
        if (hoursSinceTrained == null) return 1.0
        val ratio = hoursSinceTrained / minRecoveryHours
        return min(ratio, 1.5) / 1.5
    }

    fun stalenessScore(hoursSinceTrained: Double?, maxHoursAcrossAllGroups: Double): Double {
        if (hoursSinceTrained == null) return 1.0
        if (maxHoursAcrossAllGroups <= 0) return 1.0
        return ln(hoursSinceTrained + 1) / ln(maxHoursAcrossAllGroups + 1)
    }

    fun frequencyBalanceScore(groupSessionsLast14d: Int, maxSessionsLast14d: Int): Double {
        if (maxSessionsLast14d <= 0) return 1.0
        return 1.0 - (groupSessionsLast14d.toDouble() / maxSessionsLast14d)
    }
}
