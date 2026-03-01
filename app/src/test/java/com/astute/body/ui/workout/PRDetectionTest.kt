package com.astute.body.ui.workout

import com.astute.body.ui.home.ExerciseLogEntry
import com.astute.body.ui.home.ExercisePerformance
import com.astute.body.ui.home.PRType
import com.astute.body.ui.home.detectNewPRs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PRDetectionTest {

    private fun makeEntry(
        weight: Double = 100.0,
        reps: Int = 10,
        exerciseId: String = "bench_press",
        exerciseName: String = "Bench Press"
    ) = ExerciseLogEntry(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        muscleGroup = "Chest",
        sets = 3,
        reps = reps,
        weight = weight
    )

    private fun makePerformance(
        maxWeight: Double = 100.0,
        maxReps: Int = 10,
        lastWeight: Double? = 95.0,
        lastReps: Int? = 8
    ) = ExercisePerformance(
        lastWeight = lastWeight,
        lastReps = lastReps,
        maxWeight = maxWeight,
        maxReps = maxReps,
        maxWeightDate = 1000L,
        maxRepsDate = 2000L
    )

    @Test
    fun `no PR when weight and reps are below max`() {
        val entry = makeEntry(weight = 90.0, reps = 8)
        val performance = makePerformance(maxWeight = 100.0, maxReps = 10)
        val prs = detectNewPRs(entry, performance)
        assertTrue(prs.isEmpty())
    }

    @Test
    fun `no PR when weight and reps equal max`() {
        val entry = makeEntry(weight = 100.0, reps = 10)
        val performance = makePerformance(maxWeight = 100.0, maxReps = 10)
        val prs = detectNewPRs(entry, performance)
        assertTrue(prs.isEmpty())
    }

    @Test
    fun `weight PR detected when exceeding max weight`() {
        val entry = makeEntry(weight = 110.0, reps = 8)
        val performance = makePerformance(maxWeight = 100.0, maxReps = 10)
        val prs = detectNewPRs(entry, performance)
        assertEquals(1, prs.size)
        assertEquals(PRType.WEIGHT, prs[0].type)
        assertEquals(100.0, prs[0].oldValue, 0.001)
        assertEquals(110.0, prs[0].newValue, 0.001)
    }

    @Test
    fun `reps PR detected when exceeding max reps`() {
        val entry = makeEntry(weight = 90.0, reps = 12)
        val performance = makePerformance(maxWeight = 100.0, maxReps = 10)
        val prs = detectNewPRs(entry, performance)
        assertEquals(1, prs.size)
        assertEquals(PRType.REPS, prs[0].type)
        assertEquals(10.0, prs[0].oldValue, 0.001)
        assertEquals(12.0, prs[0].newValue, 0.001)
    }

    @Test
    fun `both PRs detected when exceeding weight and reps`() {
        val entry = makeEntry(weight = 110.0, reps = 12)
        val performance = makePerformance(maxWeight = 100.0, maxReps = 10)
        val prs = detectNewPRs(entry, performance)
        assertEquals(2, prs.size)
        assertEquals(PRType.WEIGHT, prs[0].type)
        assertEquals(PRType.REPS, prs[1].type)
    }

    @Test
    fun `first time exercise with null performance triggers both PRs`() {
        val entry = makeEntry(weight = 50.0, reps = 8)
        val prs = detectNewPRs(entry, null)
        assertEquals(2, prs.size)
        assertEquals(PRType.WEIGHT, prs[0].type)
        assertEquals(0.0, prs[0].oldValue, 0.001)
        assertEquals(50.0, prs[0].newValue, 0.001)
        assertEquals(PRType.REPS, prs[1].type)
        assertEquals(0.0, prs[1].oldValue, 0.001)
        assertEquals(8.0, prs[1].newValue, 0.001)
    }

    @Test
    fun `zero weight first time does not trigger weight PR`() {
        val entry = makeEntry(weight = 0.0, reps = 10)
        val prs = detectNewPRs(entry, null)
        assertEquals(1, prs.size)
        assertEquals(PRType.REPS, prs[0].type)
    }

    @Test
    fun `PR entries have correct exercise info`() {
        val entry = makeEntry(
            weight = 200.0,
            reps = 15,
            exerciseId = "squat",
            exerciseName = "Barbell Squat"
        )
        val prs = detectNewPRs(entry, null)
        prs.forEach { pr ->
            assertEquals("squat", pr.exerciseId)
            assertEquals("Barbell Squat", pr.exerciseName)
        }
    }
}
