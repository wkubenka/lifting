package com.astutebody.app.domain.scoring

import com.astutebody.app.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MuscleGroupScorerTest {

    private lateinit var scorer: MuscleGroupScorer

    @Before
    fun setup() {
        scorer = MuscleGroupScorer()
    }

    // Recovery score tests

    @Test
    fun `recoveryScore - null hours (never trained) returns 1`() {
        assertEquals(1.0, scorer.recoveryScore(null, 48), 0.01)
    }

    @Test
    fun `recoveryScore - 0 hours returns near zero`() {
        assertEquals(0.0, scorer.recoveryScore(0.0, 48), 0.01)
    }

    @Test
    fun `recoveryScore - exactly at min recovery returns about 0_67`() {
        val score = scorer.recoveryScore(48.0, 48)
        assertEquals(0.667, score, 0.01)
    }

    @Test
    fun `recoveryScore - at 1_5x recovery returns 1_0`() {
        val score = scorer.recoveryScore(72.0, 48)
        assertEquals(1.0, score, 0.01)
    }

    @Test
    fun `recoveryScore - beyond 1_5x recovery is still capped at 1_0`() {
        val score = scorer.recoveryScore(200.0, 48)
        assertEquals(1.0, score, 0.01)
    }

    @Test
    fun `recoveryScore - at half recovery returns about 0_33`() {
        val score = scorer.recoveryScore(24.0, 48)
        assertEquals(0.333, score, 0.01)
    }

    // Staleness score tests

    @Test
    fun `stalenessScore - null hours returns 1`() {
        assertEquals(1.0, scorer.stalenessScore(null, 100.0), 0.01)
    }

    @Test
    fun `stalenessScore - stalest group scores 1_0`() {
        assertEquals(1.0, scorer.stalenessScore(100.0, 100.0), 0.01)
    }

    @Test
    fun `stalenessScore - most recent scores lowest`() {
        val stalest = scorer.stalenessScore(100.0, 100.0)
        val recent = scorer.stalenessScore(10.0, 100.0)
        assertTrue(stalest > recent)
    }

    @Test
    fun `stalenessScore - logarithmic curve verified`() {
        // Difference between 2 and 4 days should be more significant than 10 and 12
        val diff2to4 = scorer.stalenessScore(96.0, 288.0) - scorer.stalenessScore(48.0, 288.0)
        val diff10to12 = scorer.stalenessScore(288.0, 288.0) - scorer.stalenessScore(240.0, 288.0)
        assertTrue("Logarithmic: short-term differences matter more", diff2to4 > diff10to12)
    }

    // Frequency balance score tests

    @Test
    fun `frequencyBalanceScore - 0 sessions returns 1_0`() {
        assertEquals(1.0, scorer.frequencyBalanceScore(0, 5), 0.01)
    }

    @Test
    fun `frequencyBalanceScore - max sessions returns 0_0`() {
        assertEquals(0.0, scorer.frequencyBalanceScore(5, 5), 0.01)
    }

    @Test
    fun `frequencyBalanceScore - half sessions returns 0_5`() {
        assertEquals(0.5, scorer.frequencyBalanceScore(3, 6), 0.01)
    }

    // Overlap penalty tests

    @Test
    fun `overlapPenalty - no overlap returns original score`() {
        val score = scorer.applyOverlapPenalty(80.0, MuscleGroup.BACK, listOf(MuscleGroup.CORE))
        assertEquals(80.0, score, 0.01)
    }

    @Test
    fun `overlapPenalty - chest selected penalizes arms for shared triceps`() {
        val original = 80.0
        val penalized = scorer.applyOverlapPenalty(
            original, MuscleGroup.ARMS, listOf(MuscleGroup.CHEST)
        )
        // Arms has {biceps, triceps, forearms}, Chest has {chest, triceps}
        // Shared: {triceps}, ratio = 1/3
        val expectedRatio = 1.0 / 3.0
        val expected = original * (1 - 0.3 * expectedRatio)
        assertEquals(expected, penalized, 0.01)
        assertTrue(penalized < original)
    }

    @Test
    fun `overlapPenalty - no selected groups returns original score`() {
        val score = scorer.applyOverlapPenalty(80.0, MuscleGroup.CHEST, emptyList())
        assertEquals(80.0, score, 0.01)
    }

    // Composite score tests

    @Test
    fun `scoreAll - new user with no history returns equal scores`() {
        val data = MuscleGroup.entries.map {
            MuscleGroupTrainingData(it, null, 0, 48)
        }
        val scores = scorer.scoreAll(data)
        assertEquals(7, scores.size)
        val firstScore = scores.first().score
        scores.forEach { assertEquals(firstScore, it.score, 0.01) }
    }

    @Test
    fun `scoreAll - hand calculated composite`() {
        // Single group: 48 hours since trained, 48h recovery, 2 sessions in 14d, max sessions = 4
        val data = listOf(
            MuscleGroupTrainingData(MuscleGroup.CHEST, 48.0, 2, 48),
            MuscleGroupTrainingData(MuscleGroup.BACK, 96.0, 1, 60),
            MuscleGroupTrainingData(MuscleGroup.CORE, 12.0, 4, 24)
        )
        val scores = scorer.scoreAll(data)

        // Back has been resting longest and trained least — should score highest
        val backScore = scores.find { it.muscleGroup == MuscleGroup.BACK }!!
        val chestScore = scores.find { it.muscleGroup == MuscleGroup.CHEST }!!
        val coreScore = scores.find { it.muscleGroup == MuscleGroup.CORE }!!

        assertTrue("Back should score higher than Chest", backScore.score > chestScore.score)
        assertTrue("Back should score higher than Core", backScore.score > coreScore.score)
    }

    @Test
    fun `scoreAll - empty input returns empty`() {
        assertEquals(emptyList<ScoredMuscleGroup>(), scorer.scoreAll(emptyList()))
    }
}
