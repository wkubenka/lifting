package com.astute.body.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val showDeleteLogConfirm: Long? = null,
    val weightUnit: String = "lbs"
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
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
            _uiState.value = _uiState.value.copy(
                selectedSessionId = sessionId,
                selectedSessionLogs = logs,
                editingLog = null
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

    fun confirmDeleteLog(logId: Long) {
        _uiState.value = _uiState.value.copy(showDeleteLogConfirm = logId)
    }

    fun cancelDeleteLog() {
        _uiState.value = _uiState.value.copy(showDeleteLogConfirm = null)
    }

    fun deleteLog(logId: Long, exerciseId: String) {
        viewModelScope.launch {
            exerciseLogDao.deleteById(logId)
            personalRecordRecalculator.recalculateForExercise(exerciseId)

            val sessionId = _uiState.value.selectedSessionId ?: return@launch
            val refreshedLogs = exerciseLogDao.getBySessionId(sessionId)

            if (refreshedLogs.isEmpty()) {
                workoutSessionDao.deleteById(sessionId)
                _uiState.value = _uiState.value.copy(
                    selectedSessionId = null,
                    selectedSessionLogs = emptyList(),
                    showDeleteLogConfirm = null,
                    editingLog = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    selectedSessionLogs = refreshedLogs,
                    showDeleteLogConfirm = null
                )
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val logs = exerciseLogDao.getBySessionId(sessionId)
            val affectedExerciseIds = logs.map { it.exerciseId }.distinct()

            workoutSessionDao.deleteById(sessionId)

            for (exerciseId in affectedExerciseIds) {
                personalRecordRecalculator.recalculateForExercise(exerciseId)
            }

            if (_uiState.value.selectedSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(
                    selectedSessionId = null,
                    selectedSessionLogs = emptyList(),
                    editingLog = null
                )
            }
        }
    }
}
