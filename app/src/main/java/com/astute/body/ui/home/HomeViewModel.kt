package com.astute.body.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity
import com.astute.body.data.repository.IWorkoutRepository
import com.astute.body.data.repository.UserPreferencesRepository
import com.astute.body.domain.AppClock
import com.astute.body.domain.generator.WorkoutGenerator
import com.astute.body.domain.model.MuscleGroup
import com.astute.body.domain.model.PlannedExercise
import com.astute.body.domain.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class HomeUiState(
    // Planning state
    val workoutPlan: WorkoutPlan? = null,
    val flatExercises: List<PlannedExercise> = emptyList(),
    val isLoading: Boolean = true,
    val favoritedIds: Set<String> = emptySet(),
    val hasExcludedInPlan: Boolean = false,
    val selectedMuscleGroups: Set<MuscleGroup> = emptySet(),

    // Workout mode
    val workoutMode: WorkoutMode = WorkoutMode.PLANNING,

    // Active workout state
    val currentIndex: Int = 0,
    val logEntries: List<ExerciseLogEntry> = emptyList(),
    val currentSets: Int = 3,
    val currentReps: Int = 10,
    val currentWeight: Double = 0.0,
    val setsCompleted: Int = 0,
    val currentExerciseSets: List<SetEntry> = emptyList(),
    val editingSetIndex: Int? = null,
    val timerSeconds: Int = 0,
    val timerRunning: Boolean = false,
    val timerTotal: Int = 0,
    val startTimeMillis: Long = 0L,
    val previousPerformance: ExercisePerformance? = null,
    val newPRs: List<NewPR> = emptyList(),
    val weightUnit: String = "lbs",
    val timerFinished: Boolean = false,
    val newPRDetected: Boolean = false,
    val allExerciseSets: Map<String, List<SetEntry>> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val generator: WorkoutGenerator,
    private val repository: IWorkoutRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: AppClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var timerJob: Job? = null
    private var cachedPrefs: UserPreferencesEntity = UserPreferencesEntity()

    init {
        // Restore active workout first, only generate if none exists
        viewModelScope.launch {
            val restored = tryRestoreWorkout()
            if (!restored) {
                generateWorkoutInternal()
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                cachedPrefs = prefs
                val state = _uiState.value
                _uiState.value = state.copy(
                    favoritedIds = prefs.favoritedExercises.toSet(),
                    hasExcludedInPlan = checkForExcludedExercises(state.workoutPlan),
                    weightUnit = prefs.weightUnit
                )
            }
        }
    }

    private fun checkForExcludedExercises(plan: WorkoutPlan?): Boolean {
        val excludedIds = cachedPrefs.excludedExercises.toSet()
        plan ?: return false
        return plan.muscleGroupAllocations
            .flatMap { it.exercises }
            .any { it.exercise.id in excludedIds }
    }

    // --- Planning methods ---

    fun generateWorkout() {
        viewModelScope.launch { generateWorkoutInternal() }
    }

    private suspend fun generateWorkoutInternal() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        val targetGroups = _uiState.value.selectedMuscleGroups.ifEmpty { null }
        val plan = generator.generate(targetGroups)
        _uiState.value = _uiState.value.copy(
            workoutPlan = plan,
            flatExercises = plan.flatExercisesSortedByEquipment(),
            isLoading = false,
            hasExcludedInPlan = checkForExcludedExercises(plan)
        )
    }

    fun swapExercise(exercise: PlannedExercise) {
        val currentPlan = _uiState.value.workoutPlan ?: return
        viewModelScope.launch {
            val newPlan = generator.swapExercise(currentPlan, exercise)
            if (newPlan === currentPlan) return@launch // no candidate found
            val state = _uiState.value

            if (state.workoutMode == WorkoutMode.ACTIVE) {
                // Find the replacement by comparing old vs new plan allocation
                val newAllocation = newPlan.muscleGroupAllocations
                    .find { it.muscleGroup == exercise.muscleGroup } ?: return@launch
                val usedIds = state.flatExercises.map { it.exercise.id }.toSet()
                val replacement = newAllocation.exercises
                    .find { it.exercise.id !in usedIds } ?: return@launch

                // In-place swap in flatExercises preserving order
                val newFlatExercises = state.flatExercises.map {
                    if (it.exercise.id == exercise.exercise.id) replacement else it
                }

                val isCurrentExercise = state.flatExercises.getOrNull(state.currentIndex)
                    ?.exercise?.id == exercise.exercise.id

                if (isCurrentExercise) stopTimer()

                _uiState.value = state.copy(
                    workoutPlan = newPlan,
                    flatExercises = newFlatExercises,
                    hasExcludedInPlan = checkForExcludedExercises(newPlan),
                    currentExerciseSets = if (isCurrentExercise) emptyList() else state.currentExerciseSets,
                    setsCompleted = if (isCurrentExercise) 0 else state.setsCompleted,
                    currentReps = if (isCurrentExercise) 10 else state.currentReps,
                    currentWeight = if (isCurrentExercise) 0.0 else state.currentWeight,
                    editingSetIndex = if (isCurrentExercise) null else state.editingSetIndex,
                    previousPerformance = if (isCurrentExercise) null else state.previousPerformance
                )
                persistState()
                if (isCurrentExercise) loadPreviousPerformance()
            } else {
                _uiState.value = state.copy(
                    workoutPlan = newPlan,
                    flatExercises = newPlan.flatExercisesSortedByEquipment(),
                    hasExcludedInPlan = checkForExcludedExercises(newPlan)
                )
            }
        }
    }

    fun regenerateGroup(muscleGroup: MuscleGroup) {
        val currentPlan = _uiState.value.workoutPlan ?: return
        viewModelScope.launch {
            val newPlan = generator.regenerateGroup(currentPlan, muscleGroup)
            _uiState.value = _uiState.value.copy(
                workoutPlan = newPlan,
                flatExercises = newPlan.flatExercisesSortedByEquipment(),
                hasExcludedInPlan = checkForExcludedExercises(newPlan)
            )
        }
    }

    fun regenerateAll() {
        val currentPlan = _uiState.value.workoutPlan ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val targetGroups = _uiState.value.selectedMuscleGroups.ifEmpty { null }
            val newPlan = generator.regenerateAll(currentPlan, targetGroups)
            _uiState.value = _uiState.value.copy(
                workoutPlan = newPlan,
                flatExercises = newPlan.flatExercisesSortedByEquipment(),
                isLoading = false,
                hasExcludedInPlan = checkForExcludedExercises(newPlan)
            )
        }
    }

    fun toggleMuscleGroup(group: MuscleGroup) {
        val current = _uiState.value.selectedMuscleGroups
        _uiState.value = _uiState.value.copy(
            selectedMuscleGroups = if (group in current) current - group else current + group
        )
        generateWorkout()
    }

    fun clearMuscleGroupSelection() {
        _uiState.value = _uiState.value.copy(selectedMuscleGroups = emptySet())
        generateWorkout()
    }

    fun moveExercise(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        val exercises = state.flatExercises.toMutableList()
        if (fromIndex !in exercises.indices || toIndex !in exercises.indices) return
        // During active workout, only allow reordering upcoming exercises
        if (state.workoutMode == WorkoutMode.ACTIVE) {
            if (fromIndex <= state.currentIndex || toIndex <= state.currentIndex) return
        }
        val item = exercises.removeAt(fromIndex)
        exercises.add(toIndex, item)
        _uiState.value = state.copy(flatExercises = exercises)
        if (state.workoutMode == WorkoutMode.ACTIVE) {
            persistState()
        }
    }

    // --- Workout lifecycle ---

    fun startWorkout() {
        val exercises = _uiState.value.flatExercises
        if (exercises.isEmpty()) return
        val refs = exercises.map {
            PlannedExerciseRef(it.exercise.id, it.muscleGroup.displayName)
        }

        val now = clock.now()
        viewModelScope.launch {
            repository.saveActiveWorkout(
                ActiveWorkoutEntity(
                    exerciseRefs = json.encodeToString(refs),
                    currentIndex = 0,
                    logEntries = json.encodeToString(emptyList<ExerciseLogEntry>()),
                    setsCompleted = 0,
                    currentSets = 3,
                    currentReps = 10,
                    currentWeight = 0.0,
                    startedAtMillis = now,
                    newPRs = json.encodeToString(emptyList<NewPR>()),
                    currentExerciseSets = json.encodeToString(emptyList<SetEntry>()),
                    allExerciseSets = json.encodeToString(emptyMap<String, List<SetEntry>>())
                )
            )
            _uiState.value = _uiState.value.copy(
                workoutMode = WorkoutMode.ACTIVE,
                currentIndex = 0,
                logEntries = emptyList(),
                currentSets = 3,
                currentReps = 10,
                currentWeight = 0.0,
                setsCompleted = 0,
                currentExerciseSets = emptyList(),
                startTimeMillis = now,
                newPRs = emptyList(),
                previousPerformance = null,
                allExerciseSets = emptyMap()
            )
            loadPreviousPerformance()
        }
    }

    private suspend fun tryRestoreWorkout(): Boolean {
        val saved = repository.getActiveWorkout() ?: return false
        val refs: List<PlannedExerciseRef> = json.decodeFromString(saved.exerciseRefs)
        val logEntries: List<ExerciseLogEntry> = json.decodeFromString(saved.logEntries)
        val newPRs: List<NewPR> = json.decodeFromString(saved.newPRs)
        val savedSets: List<SetEntry> = json.decodeFromString(saved.currentExerciseSets)
        val savedAllSets: Map<String, List<SetEntry>> = try {
            json.decodeFromString(saved.allExerciseSets)
        } catch (_: Exception) { emptyMap() }

        val exerciseIds = refs.map { it.exerciseId }
        val exerciseMap = repository.getExercisesByIds(exerciseIds).associateBy { it.id }
        val exercises = refs.mapNotNull { ref ->
            val entity = exerciseMap[ref.exerciseId] ?: return@mapNotNull null
            val muscleGroup = MuscleGroup.fromDisplayName(ref.muscleGroup) ?: return@mapNotNull null
            PlannedExercise(exercise = entity, muscleGroup = muscleGroup)
        }

        if (exercises.isEmpty()) return false

        _uiState.value = _uiState.value.copy(
            workoutMode = WorkoutMode.ACTIVE,
            flatExercises = exercises,
            currentIndex = saved.currentIndex.coerceAtMost(exercises.size - 1).coerceAtLeast(0),
            logEntries = logEntries,
            setsCompleted = saved.setsCompleted,
            currentSets = saved.currentSets,
            currentReps = saved.currentReps,
            currentWeight = saved.currentWeight,
            startTimeMillis = saved.startedAtMillis,
            newPRs = newPRs,
            currentExerciseSets = savedSets,
            allExerciseSets = savedAllSets
        )

        loadPreviousPerformance()
        return true
    }

    private fun persistState() {
        val state = _uiState.value
        if (state.flatExercises.isEmpty()) return

        viewModelScope.launch {
            val refs = state.flatExercises.map {
                PlannedExerciseRef(it.exercise.id, it.muscleGroup.displayName)
            }
            repository.saveActiveWorkout(
                ActiveWorkoutEntity(
                    exerciseRefs = json.encodeToString(refs),
                    currentIndex = state.currentIndex,
                    logEntries = json.encodeToString(state.logEntries),
                    setsCompleted = state.setsCompleted,
                    currentSets = state.currentSets,
                    currentReps = state.currentReps,
                    currentWeight = state.currentWeight,
                    startedAtMillis = state.startTimeMillis,
                    newPRs = json.encodeToString(state.newPRs),
                    currentExerciseSets = json.encodeToString(state.currentExerciseSets),
                    allExerciseSets = json.encodeToString(state.allExerciseSets)
                )
            )
        }
    }

    // --- Exercise logging ---

    private fun loadPreviousPerformance() {
        val exercises = _uiState.value.flatExercises
        if (exercises.isEmpty()) return
        val current = exercises[_uiState.value.currentIndex]

        viewModelScope.launch {
            val record = repository.getPersonalRecord(current.exercise.id)
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
        val newSet = SetEntry(reps = state.currentReps, weight = state.currentWeight)
        _uiState.value = state.copy(
            setsCompleted = state.setsCompleted + 1,
            currentExerciseSets = state.currentExerciseSets + newSet
        )
        persistState()
        startRestTimer()
    }

    fun startEditingSet(index: Int) {
        val sets = _uiState.value.currentExerciseSets
        if (index !in sets.indices) return
        val set = sets[index]
        _uiState.value = _uiState.value.copy(
            editingSetIndex = index,
            currentReps = set.reps,
            currentWeight = set.weight
        )
    }

    fun saveEditedSet() {
        val state = _uiState.value
        val index = state.editingSetIndex ?: return
        val sets = state.currentExerciseSets.toMutableList()
        if (index !in sets.indices) return
        sets[index] = SetEntry(reps = state.currentReps, weight = state.currentWeight)
        _uiState.value = state.copy(
            currentExerciseSets = sets,
            editingSetIndex = null
        )
        persistState()
    }

    fun cancelEditingSet() {
        _uiState.value = _uiState.value.copy(editingSetIndex = null)
    }

    fun completeExercise() {
        val state = _uiState.value
        if (state.flatExercises.isEmpty()) return

        val current = state.flatExercises[state.currentIndex]
        val updatedMap = state.allExerciseSets + (current.exercise.id to state.currentExerciseSets)

        if (state.currentExerciseSets.isNotEmpty()) {
            val entries = buildLogEntries(current, state.currentExerciseSets)
            val detectedPRs = entries.flatMap { detectNewPRs(it, state.previousPerformance) }

            _uiState.value = state.copy(
                logEntries = state.logEntries + entries,
                newPRs = state.newPRs + detectedPRs,
                newPRDetected = detectedPRs.isNotEmpty(),
                allExerciseSets = updatedMap
            )
        } else {
            _uiState.value = state.copy(allExerciseSets = updatedMap)
        }

        advanceToNext()
    }

    fun skipExercise() {
        advanceToNext()
    }

    private fun buildLogEntries(exercise: PlannedExercise, sets: List<SetEntry>): List<ExerciseLogEntry> {
        return sets.groupBy { Pair(it.reps, it.weight) }
            .map { (key, grouped) ->
                ExerciseLogEntry(
                    exerciseId = exercise.exercise.id,
                    exerciseName = exercise.exercise.name,
                    muscleGroup = exercise.muscleGroup.displayName,
                    sets = grouped.size,
                    reps = key.first,
                    weight = key.second,
                    volumeMultiplier = exercise.exercise.volumeMultiplier
                )
            }
    }

    private fun advanceToNext() {
        stopTimer()
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1

        if (nextIndex >= state.flatExercises.size) {
            _uiState.value = state.copy(workoutMode = WorkoutMode.COMPLETE)
        } else {
            _uiState.value = state.copy(
                currentIndex = nextIndex,
                setsCompleted = 0,
                currentExerciseSets = emptyList(),
                editingSetIndex = null,
                currentSets = 3,
                currentReps = 10,
                currentWeight = 0.0,
                previousPerformance = null
            )
            persistState()
            loadPreviousPerformance()
        }
    }

    fun goToPreviousExercise() {
        val state = _uiState.value
        if (state.currentIndex <= 0) return

        stopTimer()

        val current = state.flatExercises[state.currentIndex]
        val updatedMap = state.allExerciseSets + (current.exercise.id to state.currentExerciseSets)

        val prevIndex = state.currentIndex - 1
        val prevExercise = state.flatExercises[prevIndex]
        val prevSets = updatedMap[prevExercise.exercise.id] ?: emptyList()

        val updatedLogEntries = state.logEntries.filter { it.exerciseId != prevExercise.exercise.id }
        val updatedPRs = state.newPRs.filter { it.exerciseId != prevExercise.exercise.id }

        _uiState.value = state.copy(
            currentIndex = prevIndex,
            currentExerciseSets = prevSets,
            setsCompleted = prevSets.size,
            editingSetIndex = null,
            allExerciseSets = updatedMap,
            logEntries = updatedLogEntries,
            newPRs = updatedPRs,
            previousPerformance = null,
            currentReps = prevSets.lastOrNull()?.reps ?: 10,
            currentWeight = prevSets.lastOrNull()?.weight ?: 0.0
        )
        persistState()
        loadPreviousPerformance()
    }

    fun finishEarly() {
        val state = _uiState.value
        val current = state.flatExercises.getOrNull(state.currentIndex)
        if (current != null && state.currentExerciseSets.isNotEmpty()) {
            val entries = buildLogEntries(current, state.currentExerciseSets)
            val detectedPRs = entries.flatMap { detectNewPRs(it, state.previousPerformance) }

            _uiState.value = state.copy(
                logEntries = state.logEntries + entries,
                newPRs = state.newPRs + detectedPRs,
                workoutMode = WorkoutMode.COMPLETE
            )
        } else {
            _uiState.value = state.copy(workoutMode = WorkoutMode.COMPLETE)
        }
        persistState()
        stopTimer()
    }

    fun resumeWorkout() {
        val state = _uiState.value
        if (state.workoutMode != WorkoutMode.COMPLETE) return
        val lastIndex = state.flatExercises.size - 1
        if (lastIndex < 0) return

        val lastExercise = state.flatExercises[lastIndex]
        val lastSets = state.allExerciseSets[lastExercise.exercise.id] ?: emptyList()

        // Remove log entries and PRs for the last exercise so it can be re-completed
        val updatedLogEntries = state.logEntries.filter { it.exerciseId != lastExercise.exercise.id }
        val updatedPRs = state.newPRs.filter { it.exerciseId != lastExercise.exercise.id }

        _uiState.value = state.copy(
            workoutMode = WorkoutMode.ACTIVE,
            currentIndex = lastIndex,
            currentExerciseSets = lastSets,
            setsCompleted = lastSets.size,
            editingSetIndex = null,
            logEntries = updatedLogEntries,
            newPRs = updatedPRs,
            currentReps = lastSets.lastOrNull()?.reps ?: 10,
            currentWeight = lastSets.lastOrNull()?.weight ?: 0.0,
            previousPerformance = null
        )
        persistState()
        loadPreviousPerformance()
    }

    // --- Save / Discard ---

    fun saveWorkout() {
        val state = _uiState.value
        if (state.logEntries.isEmpty()) return

        viewModelScope.launch {
            val now = clock.now()
            val muscleGroups = state.logEntries.map { it.muscleGroup }.distinct()
            val sessionId = repository.insertWorkoutSession(
                WorkoutSessionEntity(
                    date = now,
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
            repository.insertExerciseLogs(logs)

            for ((exerciseId, entries) in state.logEntries.groupBy { it.exerciseId }) {
                val existing = repository.getPersonalRecord(exerciseId)
                val sessionMaxWeight = entries.maxOf { it.weight }
                val sessionMaxReps = entries.maxOf { it.reps }
                val newRecord = PersonalRecordEntity(
                    exerciseId = exerciseId,
                    maxWeight = maxOf(existing?.maxWeight ?: 0.0, sessionMaxWeight),
                    maxWeightDate = if (sessionMaxWeight > (existing?.maxWeight ?: 0.0)) now else existing?.maxWeightDate,
                    maxReps = maxOf(existing?.maxReps ?: 0, sessionMaxReps),
                    maxRepsDate = if (sessionMaxReps > (existing?.maxReps ?: 0)) now else existing?.maxRepsDate,
                    lastWeight = sessionMaxWeight,
                    lastReps = sessionMaxReps,
                    lastPerformed = now
                )
                repository.upsertPersonalRecord(newRecord)
            }

            repository.clearActiveWorkout()
            _uiState.value = state.copy(
                workoutMode = WorkoutMode.PLANNING,
                currentIndex = 0,
                logEntries = emptyList(),
                currentExerciseSets = emptyList(),
                setsCompleted = 0,
                newPRs = emptyList(),
                previousPerformance = null,
                editingSetIndex = null,
                timerRunning = false,
                timerSeconds = 0,
                allExerciseSets = emptyMap()
            )
            generateWorkoutInternal()
        }
    }

    fun discardWorkout() {
        viewModelScope.launch {
            repository.clearActiveWorkout()
            stopTimer()
            _uiState.value = _uiState.value.copy(
                workoutMode = WorkoutMode.PLANNING,
                currentIndex = 0,
                logEntries = emptyList(),
                currentExerciseSets = emptyList(),
                setsCompleted = 0,
                newPRs = emptyList(),
                previousPerformance = null,
                editingSetIndex = null,
                allExerciseSets = emptyMap()
            )
        }
    }

    // --- Rest timer ---

    private fun startRestTimer() {
        val state = _uiState.value
        val current = state.flatExercises.getOrNull(state.currentIndex) ?: return
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
