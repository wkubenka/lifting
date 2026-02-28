package com.astutebody.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astutebody.app.data.local.dao.ExerciseDao
import com.astutebody.app.data.local.dao.UserPreferencesDao
import com.astutebody.app.data.local.entity.UserPreferencesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferencesEntity = UserPreferencesEntity(),
    val allEquipment: List<String> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesDao: UserPreferencesDao,
    private val exerciseDao: ExerciseDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val equipment = exerciseDao.getDistinctEquipment()
            val prefs = userPreferencesDao.getOnce() ?: UserPreferencesEntity()
            _uiState.value = SettingsUiState(
                preferences = prefs,
                allEquipment = equipment,
                isLoading = false
            )
        }
    }

    fun setExperienceLevel(level: String) {
        updateAndSave { it.copy(experienceLevel = level) }
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

    fun toggleEquipment(equipment: String) {
        updateAndSave { prefs ->
            val current = prefs.availableEquipment.toMutableList()
            if (current.contains(equipment)) {
                current.remove(equipment)
            } else {
                current.add(equipment)
            }
            prefs.copy(availableEquipment = current)
        }
    }

    private fun updateAndSave(transform: (UserPreferencesEntity) -> UserPreferencesEntity) {
        val updated = transform(_uiState.value.preferences)
        _uiState.value = _uiState.value.copy(preferences = updated)
        viewModelScope.launch {
            userPreferencesDao.upsert(updated)
        }
    }
}
