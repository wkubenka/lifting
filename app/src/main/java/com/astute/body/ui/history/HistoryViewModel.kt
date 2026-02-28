package com.astute.body.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.PersonalRecordRecalculator
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
    val selectedSessionLogs: List<ExerciseLogEntity> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val personalRecordRecalculator: PersonalRecordRecalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
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
                selectedSessionLogs = emptyList()
            )
            return
        }

        viewModelScope.launch {
            val logs = exerciseLogDao.getBySessionId(sessionId)
            _uiState.value = _uiState.value.copy(
                selectedSessionId = sessionId,
                selectedSessionLogs = logs
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            // Collect affected exercise IDs before cascade delete removes the logs
            val logs = exerciseLogDao.getBySessionId(sessionId)
            val affectedExerciseIds = logs.map { it.exerciseId }.distinct()

            workoutSessionDao.deleteById(sessionId)

            // Recalculate PRs for all exercises that had logs in this session
            for (exerciseId in affectedExerciseIds) {
                personalRecordRecalculator.recalculateForExercise(exerciseId)
            }

            if (_uiState.value.selectedSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(
                    selectedSessionId = null,
                    selectedSessionLogs = emptyList()
                )
            }
        }
    }
}
