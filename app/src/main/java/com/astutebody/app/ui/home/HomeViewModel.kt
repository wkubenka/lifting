package com.astutebody.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astutebody.app.data.repository.IWorkoutRepository
import com.astutebody.app.domain.generator.WorkoutGenerator
import com.astutebody.app.domain.model.MuscleGroup
import com.astutebody.app.domain.model.PlannedExercise
import com.astutebody.app.domain.model.WorkoutPlan
import com.astutebody.app.ui.workout.ActiveWorkoutState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val workoutPlan: WorkoutPlan? = null,
    val isLoading: Boolean = true,
    val needsSetup: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val generator: WorkoutGenerator,
    private val repository: IWorkoutRepository,
    private val activeWorkoutState: ActiveWorkoutState
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        generateWorkout()
    }

    fun generateWorkout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val prefs = repository.getUserPreferences()
            if (prefs.availableEquipment.isEmpty()) {
                _uiState.value = HomeUiState(isLoading = false, needsSetup = true)
                return@launch
            }

            val plan = generator.generate()
            _uiState.value = HomeUiState(workoutPlan = plan, isLoading = false)
        }
    }

    fun swapExercise(exercise: PlannedExercise) {
        val currentPlan = _uiState.value.workoutPlan ?: return
        viewModelScope.launch {
            val newPlan = generator.swapExercise(currentPlan, exercise)
            _uiState.value = _uiState.value.copy(workoutPlan = newPlan)
        }
    }

    fun regenerateGroup(muscleGroup: MuscleGroup) {
        val currentPlan = _uiState.value.workoutPlan ?: return
        viewModelScope.launch {
            val newPlan = generator.regenerateGroup(currentPlan, muscleGroup)
            _uiState.value = _uiState.value.copy(workoutPlan = newPlan)
        }
    }

    fun regenerateAll() {
        val currentPlan = _uiState.value.workoutPlan ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val newPlan = generator.regenerateAll(currentPlan)
            _uiState.value = _uiState.value.copy(workoutPlan = newPlan, isLoading = false)
        }
    }

    fun startWorkout() {
        activeWorkoutState.workoutPlan = _uiState.value.workoutPlan
    }

    fun onSetupComplete() {
        _uiState.value = _uiState.value.copy(needsSetup = false)
        generateWorkout()
    }
}
