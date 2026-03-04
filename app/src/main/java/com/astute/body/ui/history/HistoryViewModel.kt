package com.astute.body.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.PersonalRecordRecalculator
import com.astute.body.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSessionId: Long? = null,
    val selectedSessionLogs: List<ExerciseLogEntity> = emptyList(),
    val editingLog: ExerciseLogEntity? = null,
    val showDeleteSessionConfirm: Long? = null,
    val weightUnit: String = "lbs",
    val volumeMultipliers: Map<String, Int> = emptyMap()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val exerciseDao: ExerciseDao,
    private val personalRecordRecalculator: PersonalRecordRecalculator,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.value = _uiState.value.copy(weightUnit = prefs.weightUnit)
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            workoutSessionDao.getAllOrderedByDate().collect { sessions ->
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    isLoading = false
                )
            }
        }
    }

    fun selectSession(sessionId: Long) {
        if (_uiState.value.selectedSessionId == sessionId) {
            _uiState.value = _uiState.value.copy(
                selectedSessionId = null,
                selectedSessionLogs = emptyList(),
                editingLog = null
            )
            return
        }

        viewModelScope.launch {
            val logs = exerciseLogDao.getBySessionId(sessionId)
            val exerciseIds = logs.map { it.exerciseId }.distinct()
            val exercises = exerciseDao.getByIds(exerciseIds)
            val multiplierMap = exercises.associate { it.id to it.volumeMultiplier }
            _uiState.value = _uiState.value.copy(
                selectedSessionId = sessionId,
                selectedSessionLogs = logs,
                editingLog = null,
                volumeMultipliers = multiplierMap
            )
        }
    }

    fun startEditingLog(log: ExerciseLogEntity) {
        _uiState.value = _uiState.value.copy(editingLog = log)
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingLog = null)
    }

    fun updateLog(log: ExerciseLogEntity, sets: Int, reps: Int, weight: Double) {
        viewModelScope.launch {
            val updated = log.copy(sets = sets, reps = reps, weight = weight)
            exerciseLogDao.update(updated)
            personalRecordRecalculator.recalculateForExercise(log.exerciseId)

            val sessionId = _uiState.value.selectedSessionId ?: return@launch
            val refreshedLogs = exerciseLogDao.getBySessionId(sessionId)
            _uiState.value = _uiState.value.copy(
                selectedSessionLogs = refreshedLogs,
                editingLog = null
            )
        }
    }

    fun confirmDeleteSession(sessionId: Long) {
        _uiState.value = _uiState.value.copy(showDeleteSessionConfirm = sessionId)
    }

    fun cancelDeleteSession() {
        _uiState.value = _uiState.value.copy(showDeleteSessionConfirm = null)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val logs = exerciseLogDao.getBySessionId(sessionId)
            val affectedExerciseIds = logs.map { it.exerciseId }.distinct()

            workoutSessionDao.deleteById(sessionId)

            for (exerciseId in affectedExerciseIds) {
                personalRecordRecalculator.recalculateForExercise(exerciseId)
            }

            _uiState.value = _uiState.value.copy(
                selectedSessionId = if (_uiState.value.selectedSessionId == sessionId) null else _uiState.value.selectedSessionId,
                selectedSessionLogs = if (_uiState.value.selectedSessionId == sessionId) emptyList() else _uiState.value.selectedSessionLogs,
                editingLog = if (_uiState.value.selectedSessionId == sessionId) null else _uiState.value.editingLog,
                showDeleteSessionConfirm = null
            )
        }
    }
}
