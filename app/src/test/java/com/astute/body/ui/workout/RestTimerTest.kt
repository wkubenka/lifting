package com.astute.body.ui.workout

import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.domain.generator.FakeWorkoutRepository.Companion.makeExercise
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.ui.home.getRestDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerTest {

    private val defaultPrefs = UserPreferencesEntity(
        restCompound = 60,
        restIsolation = 45,
        restBodyweightAb = 30
    )

    @Test
    fun `compound exercise gets compound rest duration`() {
        val exercise = PlannedExercise(
            exercise = makeExercise("Bench_Press", listOf("chest"), mechanic = "compound", equipment = "barbell"),
            muscleGroup = MuscleGroup.CHEST
        )
        assertEquals(60, getRestDuration(exercise, defaultPrefs))
    }

    @Test
    fun `isolation exercise gets isolation rest duration`() {
        val exercise = PlannedExercise(
            exercise = makeExercise("Bicep_Curl", listOf("biceps"), mechanic = "isolation", equipment = "dumbbell"),
            muscleGroup = MuscleGroup.ARMS
        )
        assertEquals(45, getRestDuration(exercise, defaultPrefs))
    }

    @Test
    fun `bodyweight abdominal exercise gets ab rest duration`() {
        val exercise = PlannedExercise(
            exercise = makeExercise("Crunch", listOf("abdominals"), mechanic = "isolation", equipment = "body only"),
            muscleGroup = MuscleGroup.CORE
        )
        assertEquals(30, getRestDuration(exercise, defaultPrefs))
    }

    @Test
    fun `abdominal exercise with equipment gets regular duration`() {
        val exercise = PlannedExercise(
            exercise = makeExercise("Cable_Crunch", listOf("abdominals"), mechanic = "isolation", equipment = "cable"),
            muscleGroup = MuscleGroup.CORE
        )
        assertEquals(45, getRestDuration(exercise, defaultPrefs))
    }

    @Test
    fun `null equipment abdominal exercise gets ab rest duration`() {
        val exercise = PlannedExercise(
            exercise = makeExercise("Plank", listOf("abdominals"), mechanic = null, equipment = null),
            muscleGroup = MuscleGroup.CORE
        )
        assertEquals(30, getRestDuration(exercise, defaultPrefs))
    }

    @Test
    fun `custom rest durations are respected`() {
        val customPrefs = defaultPrefs.copy(restCompound = 90, restIsolation = 60, restBodyweightAb = 20)
        val compound = PlannedExercise(
            exercise = makeExercise("Squat", listOf("quadriceps"), mechanic = "compound"),
            muscleGroup = MuscleGroup.LEGS_PUSH
        )
        assertEquals(90, getRestDuration(compound, customPrefs))
    }
}
