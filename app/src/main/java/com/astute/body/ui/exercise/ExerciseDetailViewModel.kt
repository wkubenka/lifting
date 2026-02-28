package com.astute.body.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.ui.navigation.ExerciseDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUiState(
    val exercise: ExerciseEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        val route = savedStateHandle.toRoute<ExerciseDetailRoute>()
        loadExercise(route.exerciseId)
    }

    private fun loadExercise(exerciseId: String) {
        viewModelScope.launch {
            val exercise = exerciseDao.getById(exerciseId)
            _uiState.value = if (exercise != null) {
                ExerciseDetailUiState(exercise = exercise, isLoading = false)
            } else {
                ExerciseDetailUiState(isLoading = false, error = "Exercise not found")
            }
        }
    }
}
