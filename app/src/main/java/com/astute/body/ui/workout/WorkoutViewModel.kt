package com.astute.body.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.data.repository.UserPreferencesRepository
import com.astute.body.domain.model.PlannedExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseLogEntry(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int,
    val reps: Int,
    val weight: Double
)

data class ExercisePerformance(
    val lastWeight: Double?,
    val lastReps: Int?,
    val maxWeight: Double,
    val maxReps: Int,
    val maxWeightDate: Long?,
    val maxRepsDate: Long?
)

data class NewPR(
    val exerciseId: String,
    val exerciseName: String,
    val type: PRType,
    val oldValue: Double,
    val newValue: Double
)

enum class PRType { WEIGHT, REPS }

data class WorkoutUiState(
    val exercises: List<PlannedExercise> = emptyList(),
    val currentIndex: Int = 0,
    val logEntries: List<ExerciseLogEntry> = emptyList(),
    val currentSets: Int = 3,
    val currentReps: Int = 10,
    val currentWeight: Double = 0.0,
    val setsCompleted: Int = 0,
    val timerSeconds: Int = 0,
    val timerRunning: Boolean = false,
    val timerTotal: Int = 0,
    val isComplete: Boolean = false,
    val isSaved: Boolean = false,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val previousPerformance: ExercisePerformance? = null,
    val newPRs: List<NewPR> = emptyList(),
    val weightUnit: String = "lbs",
    val timerFinished: Boolean = false,
    val newPRDetected: Boolean = false
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val activeWorkoutState: ActiveWorkoutState,
    private val repository: IWorkoutRepository,
    private val workoutSessionDao: WorkoutSessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val personalRecordDao: PersonalRecordDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var cachedPrefs: UserPreferencesEntity = UserPreferencesEntity()

    init {
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                cachedPrefs = prefs
                _uiState.value = _uiState.value.copy(weightUnit = prefs.weightUnit)
            }
        }
        loadWorkout()
    }

    private fun loadWorkout() {
        val plan = activeWorkoutState.workoutPlan ?: return
        val allExercises = plan.muscleGroupAllocations.flatMap { it.exercises }

        _uiState.value = _uiState.value.copy(
            exercises = allExercises,
            startTimeMillis = System.currentTimeMillis()
        )
        loadPreviousPerformance()
    }

    private fun loadPreviousPerformance() {
        val exercises = _uiState.value.exercises
        if (exercises.isEmpty()) return
        val current = exercises[_uiState.value.currentIndex]

        viewModelScope.launch {
            val record = personalRecordDao.getByExerciseId(current.exercise.id)
            _uiState.value = _uiState.value.copy(
                previousPerformance = record?.let {
                    ExercisePerformance(
                        lastWeight = it.lastWeight,
                        lastReps = it.lastReps,
                        maxWeight = it.maxWeight,
                        maxReps = it.maxReps,
                        maxWeightDate = it.maxWeightDate,
                        maxRepsDate = it.maxRepsDate
                    )
                }
            )
        }
    }

    fun updateSets(sets: Int) {
        _uiState.value = _uiState.value.copy(currentSets = sets.coerceAtLeast(1))
    }

    fun updateReps(reps: Int) {
        _uiState.value = _uiState.value.copy(currentReps = reps.coerceAtLeast(1))
    }

    fun updateWeight(weight: Double) {
        _uiState.value = _uiState.value.copy(currentWeight = weight.coerceAtLeast(0.0))
    }

    fun logSet() {
        val state = _uiState.value
        _uiState.value = state.copy(setsCompleted = state.setsCompleted + 1)
        startRestTimer()
    }

    fun completeExercise() {
        val state = _uiState.value
        if (state.exercises.isEmpty()) return

        val current = state.exercises[state.currentIndex]
        if (state.setsCompleted > 0) {
            val entry = ExerciseLogEntry(
                exerciseId = current.exercise.id,
                exerciseName = current.exercise.name,
                muscleGroup = current.muscleGroup.displayName,
                sets = state.setsCompleted,
                reps = state.currentReps,
                weight = state.currentWeight
            )

            val detectedPRs = detectNewPRs(entry, state.previousPerformance)

            _uiState.value = state.copy(
                logEntries = state.logEntries + entry,
                newPRs = state.newPRs + detectedPRs,
                newPRDetected = detectedPRs.isNotEmpty()
            )
        }

        advanceToNext()
    }

    companion object {
        fun getRestDuration(exercise: PlannedExercise, prefs: UserPreferencesEntity): Int {
            val isAbdominal = exercise.exercise.primaryMuscles.contains("abdominals")
            val isBodyweight = exercise.exercise.equipment == null ||
                    exercise.exercise.equipment == "body only"

            return when {
                isAbdominal && isBodyweight -> prefs.restBodyweightAb
                exercise.exercise.mechanic == "compound" -> prefs.restCompound
                else -> prefs.restIsolation
            }
        }

        fun detectNewPRs(entry: ExerciseLogEntry, performance: ExercisePerformance?): List<NewPR> {
            val prs = mutableListOf<NewPR>()
            val maxWeight = performance?.maxWeight ?: 0.0
            val maxReps = performance?.maxReps ?: 0

            if (entry.weight > maxWeight) {
                prs.add(NewPR(
                    exerciseId = entry.exerciseId,
                    exerciseName = entry.exerciseName,
                    type = PRType.WEIGHT,
                    oldValue = maxWeight,
                    newValue = entry.weight
                ))
            }
            if (entry.reps > maxReps) {
                prs.add(NewPR(
                    exerciseId = entry.exerciseId,
                    exerciseName = entry.exerciseName,
                    type = PRType.REPS,
                    oldValue = maxReps.toDouble(),
                    newValue = entry.reps.toDouble()
                ))
            }
            return prs
        }
    }

    fun skipExercise() {
        advanceToNext()
    }

    private fun advanceToNext() {
        stopTimer()
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1

        if (nextIndex >= state.exercises.size) {
            _uiState.value = state.copy(isComplete = true)
        } else {
            _uiState.value = state.copy(
                currentIndex = nextIndex,
                setsCompleted = 0,
                currentSets = 3,
                currentReps = 10,
                currentWeight = 0.0,
                previousPerformance = null
            )
            loadPreviousPerformance()
        }
    }

    fun finishEarly() {
        val state = _uiState.value
        val current = state.exercises.getOrNull(state.currentIndex)
        if (current != null && state.setsCompleted > 0) {
            val entry = ExerciseLogEntry(
                exerciseId = current.exercise.id,
                exerciseName = current.exercise.name,
                muscleGroup = current.muscleGroup.displayName,
                sets = state.setsCompleted,
                reps = state.currentReps,
                weight = state.currentWeight
            )

            val detectedPRs = detectNewPRs(entry, state.previousPerformance)

            _uiState.value = state.copy(
                logEntries = state.logEntries + entry,
                newPRs = state.newPRs + detectedPRs,
                isComplete = true
            )
        } else {
            _uiState.value = state.copy(isComplete = true)
        }
        stopTimer()
    }

    fun saveWorkout() {
        val state = _uiState.value
        if (state.logEntries.isEmpty()) return

        viewModelScope.launch {
            val muscleGroups = state.logEntries.map { it.muscleGroup }.distinct()
            val sessionId = workoutSessionDao.insert(
                WorkoutSessionEntity(
                    date = System.currentTimeMillis(),
                    muscleGroups = muscleGroups,
                    completed = true
                )
            )

            val logs = state.logEntries.map {
                ExerciseLogEntity(
                    sessionId = sessionId,
                    exerciseId = it.exerciseId,
                    muscleGroup = it.muscleGroup,
                    sets = it.sets,
                    reps = it.reps,
                    weight = it.weight
                )
            }
            exerciseLogDao.insertAll(logs)

            for (entry in state.logEntries) {
                val existing = personalRecordDao.getByExerciseId(entry.exerciseId)
                val now = System.currentTimeMillis()
                val newRecord = PersonalRecordEntity(
                    exerciseId = entry.exerciseId,
                    maxWeight = maxOf(existing?.maxWeight ?: 0.0, entry.weight),
                    maxWeightDate = if (entry.weight > (existing?.maxWeight ?: 0.0)) now else existing?.maxWeightDate,
                    maxReps = maxOf(existing?.maxReps ?: 0, entry.reps),
                    maxRepsDate = if (entry.reps > (existing?.maxReps ?: 0)) now else existing?.maxRepsDate,
                    lastWeight = entry.weight,
                    lastReps = entry.reps,
                    lastPerformed = now
                )
                personalRecordDao.upsert(newRecord)
            }

            _uiState.value = state.copy(isSaved = true)
        }
    }

    private fun startRestTimer() {
        val state = _uiState.value
        val current = state.exercises.getOrNull(state.currentIndex) ?: return
        val duration = getRestDuration(current, cachedPrefs)

        _uiState.value = state.copy(
            timerSeconds = duration,
            timerTotal = duration,
            timerRunning = true
        )
        startCountdown()
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerSeconds > 0 && _uiState.value.timerRunning) {
                delay(1000)
                val current = _uiState.value
                if (current.timerRunning) {
                    _uiState.value = current.copy(timerSeconds = current.timerSeconds - 1)
                }
            }
            if (_uiState.value.timerRunning) {
                _uiState.value = _uiState.value.copy(timerRunning = false, timerFinished = true)
            }
        }
    }

    fun clearTimerFinished() {
        _uiState.value = _uiState.value.copy(timerFinished = false)
    }

    fun clearNewPRDetected() {
        _uiState.value = _uiState.value.copy(newPRDetected = false)
    }

    fun skipTimer() {
        stopTimer()
    }

    fun extendTimer() {
        _uiState.value = _uiState.value.copy(
            timerSeconds = _uiState.value.timerSeconds + 15,
            timerTotal = _uiState.value.timerTotal + 15
        )
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(timerRunning = false, timerSeconds = 0)
    }
}
