package com.astutebody.app.ui.workout

import com.astutebody.app.domain.model.WorkoutPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveWorkoutState @Inject constructor() {
    var workoutPlan: WorkoutPlan? = null
}
