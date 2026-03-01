package com.astute.body.ui.muscles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.scoring.MuscleGroupScorer
import com.astute.body.domain.scoring.MuscleGroupTrainingData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MuscleGroupStatus(
    val muscleGroup: MuscleGroup,
    val lastTrainedMillis: Long?,
    val hoursSinceLastTrained: Double?,
    val recoveryRatio: Double,
    val freshnessStatus: FreshnessStatus,
    val sessionsLast14Days: Int,
    val currentScore: Double
)

enum class FreshnessStatus { RECOVERING, READY, DUE, OVERDUE }

data class MuscleOverviewUiState(
    val groups: List<MuscleGroupStatus> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class MuscleOverviewViewModel @Inject constructor(
    private val repository: IWorkoutRepository,
    private val scorer: MuscleGroupScorer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MuscleOverviewUiState())
    val uiState: StateFlow<MuscleOverviewUiState> = _uiState.asStateFlow()

    init {
        loadOverview()
    }

    fun refresh() {
        loadOverview()
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val now = System.currentTimeMillis()
            val trainingDataList = mutableListOf<MuscleGroupTrainingData>()
            val statusList = mutableListOf<MuscleGroupStatus>()

            for (group in MuscleGroup.entries) {
                val lastTrained = repository.getLastTrainedMillis(group)
                val sessionsLast14Days = repository.getSessionCountLast14Days(group)
                val config = repository.getRecoveryConfig(group)
                val minRecoveryHours = config?.minRecoveryHours ?: 48

                val hoursSince = if (lastTrained != null) {
                    (now - lastTrained) / (1000.0 * 60 * 60)
                } else null

                trainingDataList.add(
                    MuscleGroupTrainingData(group, hoursSince, sessionsLast14Days, minRecoveryHours)
                )

                val recoveryRatio = if (hoursSince != null) {
                    hoursSince / minRecoveryHours
                } else Double.MAX_VALUE

                val freshnessStatus = computeFreshnessStatus(recoveryRatio, lastTrained == null)

                statusList.add(
                    MuscleGroupStatus(
                        muscleGroup = group,
                        lastTrainedMillis = lastTrained,
                        hoursSinceLastTrained = hoursSince,
                        recoveryRatio = recoveryRatio,
                        freshnessStatus = freshnessStatus,
                        sessionsLast14Days = sessionsLast14Days,
                        currentScore = 0.0 // filled below
                    )
                )
            }

            val scores = scorer.scoreAll(trainingDataList)
            val scoreMap = scores.associate { it.muscleGroup to it.score }

            val finalList = statusList.map { status ->
                status.copy(currentScore = scoreMap[status.muscleGroup] ?: 0.0)
            }

            _uiState.value = MuscleOverviewUiState(groups = finalList, isLoading = false)
        }
    }

    companion object {
        fun computeFreshnessStatus(recoveryRatio: Double, neverTrained: Boolean): FreshnessStatus {
            if (neverTrained) return FreshnessStatus.OVERDUE
            return when {
                recoveryRatio < 1.0 -> FreshnessStatus.RECOVERING
                recoveryRatio < 2.0 -> FreshnessStatus.READY
                recoveryRatio < 4.0 -> FreshnessStatus.DUE
                else -> FreshnessStatus.OVERDUE
            }
        }
    }
}
