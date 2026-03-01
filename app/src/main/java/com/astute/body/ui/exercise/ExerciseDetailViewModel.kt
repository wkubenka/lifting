package com.astute.body.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUiState(
    val exercise: ExerciseEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorited: Boolean = false,
    val isExcluded: Boolean = false
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userPreferencesDao: UserPreferencesDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    private val exerciseId: String

    init {
        exerciseId = checkNotNull(savedStateHandle.get<String>("exerciseId"))
        loadExercise(exerciseId)
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    isFavorited = exerciseId in prefs.favoritedExercises,
                    isExcluded = exerciseId in prefs.excludedExercises
                )
            }
        }
    }

    private fun loadExercise(exerciseId: String) {
        viewModelScope.launch {
            val exercise = exerciseDao.getById(exerciseId)
            _uiState.value = if (exercise != null) {
                _uiState.value.copy(exercise = exercise, isLoading = false)
            } else {
                _uiState.value.copy(isLoading = false, error = "Exercise not found")
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val prefs = userPreferencesDao.getOnce() ?: return@launch
            val favorited = prefs.favoritedExercises.toMutableList()
            val excluded = prefs.excludedExercises.toMutableList()
            if (exerciseId in favorited) {
                favorited.remove(exerciseId)
            } else {
                favorited.add(exerciseId)
                excluded.remove(exerciseId)
            }
            userPreferencesDao.upsert(prefs.copy(
                favoritedExercises = favorited,
                excludedExercises = excluded
            ))
        }
    }

    fun toggleExclude() {
        viewModelScope.launch {
            val prefs = userPreferencesDao.getOnce() ?: return@launch
            val excluded = prefs.excludedExercises.toMutableList()
            val favorited = prefs.favoritedExercises.toMutableList()
            if (exerciseId in excluded) {
                excluded.remove(exerciseId)
            } else {
                excluded.add(exerciseId)
                favorited.remove(exerciseId)
            }
            userPreferencesDao.upsert(prefs.copy(
                excludedExercises = excluded,
                favoritedExercises = favorited
            ))
        }
    }
}
