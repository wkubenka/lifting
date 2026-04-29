package com.astute.body.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferencesEntity = UserPreferencesEntity(),
    val isLoading: Boolean = true,
    val favoriteCount: Int = 0,
    val excludedCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesDao: UserPreferencesDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    favoriteCount = prefs.favoritedExercises.size,
                    excludedCount = prefs.excludedExercises.size
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = userPreferencesDao.getOnce() ?: UserPreferencesEntity()
            _uiState.value = SettingsUiState(
                preferences = prefs,
                isLoading = false
            )
        }
    }

    fun setWeightUnit(unit: String) {
        updateAndSave { it.copy(weightUnit = unit) }
    }

    fun setTargetWorkoutSize(size: Int) {
        updateAndSave { it.copy(targetWorkoutSize = size) }
    }

    fun setRestCompound(seconds: Int) {
        updateAndSave { it.copy(restCompound = seconds.coerceAtLeast(10)) }
    }

    fun setRestIsolation(seconds: Int) {
        updateAndSave { it.copy(restIsolation = seconds.coerceAtLeast(10)) }
    }

    fun setRestBodyweightAb(seconds: Int) {
        updateAndSave { it.copy(restBodyweightAb = seconds.coerceAtLeast(10)) }
    }

    private fun updateAndSave(transform: (UserPreferencesEntity) -> UserPreferencesEntity) {
        val updated = transform(_uiState.value.preferences)
        _uiState.value = _uiState.value.copy(preferences = updated)
        viewModelScope.launch {
            userPreferencesDao.upsert(updated)
        }
    }
}
