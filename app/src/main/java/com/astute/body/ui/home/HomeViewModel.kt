package com.astute.body.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.dao.ActiveWorkoutDao
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.data.repository.UserPreferencesRepository
import com.astute.body.domain.generator.WorkoutGenerator
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.domain.model.WorkoutPlan
import com.astute.body.ui.workout.ExerciseLogEntry
import com.astute.body.ui.workout.NewPR
import com.astute.body.ui.workout.PlannedExerciseRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HomeUiState(
    val workoutPlan: WorkoutPlan? = null,
    val isLoading: Boolean = true,
    val needsSetup: Boolean = false,
    val favoritedIds: Set<String> = emptySet(),
    val hasExcludedInPlan: Boolean = false,
    val hasActiveWorkout: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val generator: WorkoutGenerator,
    private val repository: IWorkoutRepository,
    private val activeWorkoutDao: ActiveWorkoutDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        generateWorkout()
        viewModelScope.launch {
            val active = activeWorkoutDao.get()
            _uiState.value = _uiState.value.copy(hasActiveWorkout = active != null)
        }
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                val favoritedIds = prefs.favoritedExercises.toSet()
                val excludedIds = prefs.excludedExercises.toSet()
                val plan = _uiState.value.workoutPlan
                val hasExcluded = plan != null && plan.muscleGroupAllocations
                    .flatMap { it.exercises }
                    .any { it.exercise.id in excludedIds }
                _uiState.value = _uiState.value.copy(
                    favoritedIds = favoritedIds,
                    hasExcludedInPlan = hasExcluded
                )
            }
        }
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
            _uiState.value = _uiState.value.copy(workoutPlan = plan, isLoading = false)
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

    fun startWorkout(onReady: () -> Unit) {
        val plan = _uiState.value.workoutPlan ?: return
        val allExercises = plan.muscleGroupAllocations.flatMap { it.exercises }
        val refs = allExercises.map {
            PlannedExerciseRef(it.exercise.id, it.muscleGroup.displayName)
        }

        viewModelScope.launch {
            activeWorkoutDao.upsert(
                ActiveWorkoutEntity(
                    exerciseRefs = json.encodeToString(refs),
                    currentIndex = 0,
                    logEntries = json.encodeToString(emptyList<ExerciseLogEntry>()),
                    setsCompleted = 0,
                    currentSets = 3,
                    currentReps = 10,
                    currentWeight = 0.0,
                    startedAtMillis = System.currentTimeMillis(),
                    newPRs = json.encodeToString(emptyList<NewPR>())
                )
            )
            onReady()
        }
    }

    fun discardActiveWorkout() {
        viewModelScope.launch {
            activeWorkoutDao.clear()
            _uiState.value = _uiState.value.copy(hasActiveWorkout = false)
        }
    }

    fun onSetupComplete() {
        _uiState.value = _uiState.value.copy(needsSetup = false)
        generateWorkout()
    }
}
