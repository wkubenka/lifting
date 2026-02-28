package com.astute.body.ui.exercise

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilterTab { ALL, FAVORITES, EXCLUDED }

data class ManageExercisesUiState(
    val allExercises: List<ExerciseEntity> = emptyList(),
    val searchQuery: String = "",
    val filterTab: FilterTab = FilterTab.ALL,
    val favoritedIds: Set<String> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
    val isLoading: Boolean = true
) {
    val filteredExercises: List<ExerciseEntity>
        get() {
            val searched = if (searchQuery.isBlank()) allExercises
            else allExercises.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
            return when (filterTab) {
                FilterTab.ALL -> searched
                FilterTab.FAVORITES -> searched.filter { it.id in favoritedIds }
                FilterTab.EXCLUDED -> searched.filter { it.id in excludedIds }
            }
        }
}

@HiltViewModel
class ManageExercisesViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userPreferencesDao: UserPreferencesDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageExercisesUiState())
    val uiState: StateFlow<ManageExercisesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val exercises = exerciseDao.getAll().first()
            _uiState.value = _uiState.value.copy(
                allExercises = exercises.sortedBy { it.name },
                isLoading = false
            )
        }
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    favoritedIds = prefs.favoritedExercises.toSet(),
                    excludedIds = prefs.excludedExercises.toSet()
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setFilterTab(tab: FilterTab) {
        _uiState.value = _uiState.value.copy(filterTab = tab)
    }

    fun toggleFavorite(exerciseId: String) {
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

    fun toggleExcluded(exerciseId: String) {
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
