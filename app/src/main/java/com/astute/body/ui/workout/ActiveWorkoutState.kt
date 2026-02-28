package com.astute.body.ui.workout

import com.astute.body.domain.model.WorkoutPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveWorkoutState @Inject constructor() {
    var workoutPlan: WorkoutPlan? = null
}
