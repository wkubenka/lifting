package com.astutebody.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astutebody.app.data.local.dao.ExerciseLogDao
import com.astutebody.app.data.local.dao.WorkoutSessionDao
import com.astutebody.app.data.local.entity.ExerciseLogEntity
import com.astutebody.app.data.local.entity.WorkoutSessionEntity
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
    private val exerciseLogDao: ExerciseLogDao
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
            workoutSessionDao.deleteById(sessionId)
            if (_uiState.value.selectedSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(
                    selectedSessionId = null,
                    selectedSessionLogs = emptyList()
                )
            }
        }
    }
}
