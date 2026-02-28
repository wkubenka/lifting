# Phase 1 — MVP Implementation Plan

## Overview

Phase 1 delivers a working app that generates smart workouts, lets you log them, and tracks history. The scope maps directly to what's defined in the project overview under Phase 1:

- Bundle and parse the free-exercise-db dataset into Room
- Muscle group mapping, recovery config, and the scoring algorithm
- Workout generation with dynamic sizing and swap
- Workout logging (sets, reps, weight per exercise)
- Rest timer between sets (compound / isolation / bodyweight-ab durations)
- History list view
- Settings screen (equipment, experience level, target workout size, weight unit)

---

## Step 1 — Android Project Scaffolding

**Goal:** A clean, building Android project with all dependencies wired up.

### Tasks

1. Generate a new Android project using Android Studio's "Empty Compose Activity" template
   - Package name: `com.astute.body`
   - Min SDK 26, target latest stable SDK
   - Kotlin DSL for Gradle (`build.gradle.kts`)

2. Add dependencies in `app/build.gradle.kts`:
   - **Jetpack Compose** BOM + `material3`, `ui`, `ui-tooling`, `navigation-compose`
   - **Room** — `room-runtime`, `room-ktx`, `room-compiler` (KSP)
   - **Hilt** — `hilt-android`, `hilt-compiler` (KSP), `hilt-navigation-compose`
   - **Coil** — `coil-compose` (for exercise images in later phases, but wire it now)
   - **Kotlinx Serialization** — `kotlinx-serialization-json` (for parsing the exercise JSON)
   - **Kotlin Coroutines** — `kotlinx-coroutines-android`

3. Configure KSP plugin for Room and Hilt annotation processing

4. Create the base package structure:
   ```
   com.astute.body/
   ├── data/
   │   ├── local/          # Room database, DAOs, entities
   │   ├── repository/     # Repository implementations
   │   └── model/          # Data transfer objects
   ├── domain/
   │   ├── model/          # Domain models (MuscleGroup, WorkoutPlan, etc.)
   │   ├── scoring/        # Scoring algorithm
   │   └── generator/      # Workout generation logic
   ├── di/                 # Hilt modules
   ├── ui/
   │   ├── home/           # Today's Workout screen
   │   ├── workout/        # Active Workout screen
   │   ├── history/        # History screen
   │   ├── settings/       # Settings screen
   │   ├── components/     # Shared composables
   │   └── theme/          # Material3 theming
   └── AstutBodyApp.kt    # Application class (Hilt entry point)
   ```

5. Set up the Hilt `@HiltAndroidApp` application class and `@AndroidEntryPoint` on `MainActivity`

6. Set up a basic `NavHost` with four routes: `home`, `workout`, `history`, `settings` — with a bottom navigation bar

### Tests

- Add test dependencies: `junit`, `kotlinx-coroutines-test`, `room-testing`, `compose-ui-test-junit4`, `hilt-android-testing`, `turbine` (for Flow testing)
- Verify the project compiles and runs with a basic instrumented test that launches `MainActivity`

### Deliverable
The app compiles, launches, and shows a bottom nav bar with four placeholder screens. Test infrastructure is in place.

---

## Step 2 — Exercise Dataset & Room Database

**Goal:** The 800+ exercise dataset is bundled in the APK and queryable through Room.

### Tasks

1. **Bundle the dataset**
   - Download `exercises.json` from `free-exercise-db` and place it in `app/src/main/assets/exercises.json`
   - Download exercise images and place in `app/src/main/assets/exercises/` (organized by exercise ID folder, e.g. `exercises/Barbell_Curl/0.jpg`). Alternatively, defer image bundling to Phase 2 and use placeholder icons for now.

2. **Define the Room entity for exercises**
   ```kotlin
   @Entity(tableName = "exercises")
   data class ExerciseEntity(
       @PrimaryKey val id: String,
       val name: String,
       val force: String?,          // "push", "pull", "static", or null
       val level: String,           // "beginner", "intermediate", "expert"
       val mechanic: String?,       // "compound", "isolation", or null
       val equipment: String?,      // "barbell", "dumbbell", "body only", etc.
       val category: String,        // "strength", "stretching", etc.
       val primaryMuscles: String,  // JSON array stored as string, e.g. '["chest","triceps"]'
       val secondaryMuscles: String,// JSON array stored as string
       val instructions: String,    // JSON array stored as string
       val images: String           // JSON array stored as string
   )
   ```
   Use Room `TypeConverter`s to convert `List<String>` ↔ JSON string for the array fields.

3. **Define the ExerciseDao**
   - `getAll(): Flow<List<ExerciseEntity>>`
   - `getById(id: String): ExerciseEntity?`
   - `getByMuscle(muscle: String): Flow<List<ExerciseEntity>>` — query where `primaryMuscles LIKE '%"muscle"%'`
   - `getByEquipmentAndLevel(equipment: Set<String>, level: String): Flow<List<ExerciseEntity>>`
   - `insertAll(exercises: List<ExerciseEntity>)`
   - `count(): Int`

4. **Parse and seed the database on first launch**
   - Create a `RoomDatabase.Callback` that reads `exercises.json` from assets, deserializes it with Kotlinx Serialization, maps to `ExerciseEntity`, and bulk-inserts
   - Only seed if the exercises table is empty (check `count()`)
   - Run this in a coroutine off the main thread

5. **Define remaining Room entities**

   **WorkoutSessionEntity:**
   ```kotlin
   @Entity(tableName = "workout_sessions")
   data class WorkoutSessionEntity(
       @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
       val date: Long,              // epoch millis
       val muscleGroups: String,    // JSON array of group names
       val completed: Boolean = false
   )
   ```

   **ExerciseLogEntity:**
   ```kotlin
   @Entity(
       tableName = "exercise_logs",
       foreignKeys = [ForeignKey(
           entity = WorkoutSessionEntity::class,
           parentColumns = ["sessionId"],
           childColumns = ["sessionId"],
           onDelete = ForeignKey.CASCADE
       )]
   )
   data class ExerciseLogEntity(
       @PrimaryKey(autoGenerate = true) val logId: Long = 0,
       val sessionId: Long,
       val exerciseId: String,
       val muscleGroup: String,
       val sets: Int,
       val reps: Int,
       val weight: Double
   )
   ```

   **PersonalRecordEntity:**
   ```kotlin
   @Entity(tableName = "personal_records")
   data class PersonalRecordEntity(
       @PrimaryKey val exerciseId: String,
       val maxWeight: Double = 0.0,
       val maxWeightDate: Long? = null,
       val maxReps: Int = 0,
       val maxRepsDate: Long? = null,
       val lastWeight: Double = 0.0,
       val lastReps: Int = 0,
       val lastPerformed: Long? = null
   )
   ```

   **UserPreferencesEntity:**
   ```kotlin
   @Entity(tableName = "user_preferences")
   data class UserPreferencesEntity(
       @PrimaryKey val id: Int = 0,  // singleton row
       val experienceLevel: String = "beginner",
       val availableEquipment: String = "[]",   // JSON set
       val excludedExercises: String = "[]",     // JSON set
       val favoritedExercises: String = "[]",    // JSON set
       val weightUnit: String = "lbs",
       val targetWorkoutSize: Int = 8,
       val restCompound: Int = 60,
       val restIsolation: Int = 45,
       val restBodyweightAb: Int = 30
   )
   ```

   **RecoveryConfigEntity:**
   ```kotlin
   @Entity(tableName = "recovery_config")
   data class RecoveryConfigEntity(
       @PrimaryKey val muscleGroup: String,
       val minRecoveryHours: Int
   )
   ```

6. **Create DAOs** for each entity with the necessary CRUD queries

7. **Create the AppDatabase** `@Database` class aggregating all entities

8. **Hilt module** — provide the `AppDatabase` singleton and each DAO

### Tests

- **TypeConverter tests** (unit): round-trip `List<String>` → JSON string → `List<String>` preserves values
- **DAO tests** (instrumented, using Room's in-memory database):
  - `ExerciseDao`: insert exercises, query by muscle, query by equipment+level, verify count
  - `WorkoutSessionDao`: insert a session, query by date range, verify cascade delete removes logs
  - `ExerciseLogDao`: insert logs for a session, query by sessionId
  - `PersonalRecordDao`: upsert a record, verify max values update correctly
  - `UserPreferencesDao`: insert and read the singleton row
  - `RecoveryConfigDao`: verify default values are seeded
- **Database seeding test** (instrumented): use the real `exercises.json` asset, verify 800+ rows are inserted and key exercises are queryable (e.g., "Barbell_Curl" exists with expected primaryMuscles)

### Deliverable
On first launch, the database is populated with 800+ exercises. You can query exercises by muscle, equipment, and level. DAO tests pass against an in-memory database.

---

## Step 3 — Domain Layer: Muscle Groups & Scoring

**Goal:** The scoring algorithm ranks muscle groups and the workout generator produces a concrete exercise list.

### Tasks

1. **Define the MuscleGroup enum and mapping**
   ```kotlin
   enum class MuscleGroup(
       val displayName: String,
       val datasetMuscles: Set<String>,  // maps to primaryMuscles values in the dataset
       val classification: String         // "major_push", "major_pull", "minor", "major"
   ) {
       CHEST("Chest", setOf("chest", "triceps"), "major_push"),
       BACK("Back", setOf("lats", "middle back", "lower back", "traps"), "major_pull"),
       SHOULDERS("Shoulders", setOf("shoulders", "neck"), "major_push"),
       ARMS("Arms", setOf("biceps", "triceps", "forearms"), "minor"),
       LEGS_PUSH("Legs (Push)", setOf("quadriceps", "glutes", "abductors", "calves"), "major_push"),
       LEGS_PULL("Legs (Pull)", setOf("hamstrings", "adductors", "glutes"), "major_pull"),
       CORE("Core", setOf("abdominals"), "major");
   }
   ```

2. **Implement the MuscleGroupScorer**

   This class takes training history and produces a score (0–100) for each muscle group. Implement the four factors exactly as defined in section 4.2 of the overview:

   - `recoveryScore(hoursSinceTrained, minRecoveryHours)` — continuous 0–1 scale, capped at 1.5x recovery
   - `stalenessScore(hoursSinceTrained, maxHoursAcrossAllGroups)` — logarithmic normalization
   - `frequencyBalanceScore(groupSessionsLast14d, maxSessionsLast14d)` — penalizes over-trained groups
   - `overlapPenalty(candidateGroup, alreadySelectedGroups)` — shared muscle ratio × 0.3

   Final: `(recovery × 0.35) + (staleness × 0.30) + (frequencyBalance × 0.25)`, then apply overlap penalty during group selection.

   Edge case: when there's no training history (new user), return equal scores for all groups.

3. **Implement the WorkoutGenerator**

   **Input:** user preferences, training history, recovery config, exercise database access
   **Output:** `WorkoutPlan` containing an ordered list of `PlannedExercise` objects grouped by muscle group

   Steps (matching section 4.3):
   1. Score all 7 muscle groups
   2. Rank and select top 2–4 groups, stopping when the next group's score is below 20% of the top score or adding it would exceed the target exercise count
   3. Allocate exercise counts proportionally to scores (minimum 2 per group, total within target range)
   4. For each group, query exercises filtered by the group's muscles, user's equipment, and experience level
   5. Pick the first exercise as a compound movement; fill remaining slots prioritizing exercises not recently performed
   6. Return the `WorkoutPlan`

4. **Define domain models**
   ```kotlin
   data class WorkoutPlan(
       val muscleGroupAllocations: List<MuscleGroupAllocation>,
       val generatedAt: Instant
   )

   data class MuscleGroupAllocation(
       val muscleGroup: MuscleGroup,
       val score: Double,
       val exercises: List<PlannedExercise>
   )

   data class PlannedExercise(
       val exercise: ExerciseEntity,
       val muscleGroup: MuscleGroup,
       val isLocked: Boolean = false
   )
   ```

5. **Implement swap and regenerate logic**
   - `swapExercise(plan, exerciseToReplace)` — returns a new plan with that exercise replaced by the next-best candidate from the same group
   - `regenerateGroup(plan, muscleGroup)` — re-selects all exercises for that group, keeping locked ones
   - `regenerateAll(plan)` — full re-generation, keeping locked exercises/groups

6. **Create a WorkoutRepository** that ties the generator to the database, handles caching the current plan, and provides training history queries:
   - `getLastTrainedDate(muscleGroup): Instant?`
   - `getSessionCountLast14Days(muscleGroup): Int`
   - `getRecentExerciseIds(muscleGroup, limit): List<String>`

### Tests

- **MuscleGroupScorer tests** (unit, no Android dependencies):
  - `recoveryScore`: 0 hours → near-zero, exactly at min recovery → ~0.67, 1.5x recovery → 1.0, 3x recovery → still 1.0 (capped)
  - `stalenessScore`: the stalest group scores 1.0, most recent scores lowest, logarithmic curve verified with known values
  - `frequencyBalanceScore`: group trained 0 times in 14 days → 1.0, group matching max → 0.0
  - `overlapPenalty`: Chest selected → Arms penalty reflects shared triceps ratio; no overlap → no penalty
  - New user (no history): all groups return equal scores
  - Composite score: verify weighted combination with a hand-calculated example
- **WorkoutGenerator tests** (unit, using fake/mock repository):
  - Selects 2–4 muscle groups within the target exercise count
  - Respects the 20% score threshold — low-scoring groups are excluded
  - Every selected group gets at least 2 exercises
  - First exercise in each group is compound (when available)
  - Exercises are filtered by equipment and experience level
  - Recently performed exercises are deprioritized
  - Overlap penalty affects group selection order (e.g., if Chest is selected, Arms ranks lower)
- **Swap/regenerate tests** (unit):
  - `swapExercise` returns a different exercise from the same group
  - `regenerateGroup` replaces non-locked exercises, keeps locked ones
  - `regenerateAll` keeps locked exercises and groups intact

### Deliverable
Unit tests pass for the scorer and generator. Given mock history data, the generator produces valid workout plans with correct group counts and exercise ordering (compound first).

---

## Step 4 — Home Screen (Today's Workout)

**Goal:** The main screen generates and displays a workout plan with swap/regenerate controls.

### Tasks

1. **HomeViewModel**
   - On init, generate a workout using `WorkoutGenerator`
   - Expose `WorkoutPlan` as state
   - Actions: `swapExercise()`, `regenerateGroup()`, `regenerateAll()`
   - Action: `startWorkout()` — navigates to the active workout screen, passing the plan

2. **HomeScreen composable**
   - If no equipment is configured yet (first launch), show a prompt to configure settings first
   - Display the workout grouped by muscle group using `LazyColumn`
   - **Muscle group header:** group name, score badge, number of exercises
   - **Exercise card:** name, equipment tag, primary muscles tag, mechanic badge (compound/isolation)
   - **Swap button** on each card — calls `swapExercise()`
   - **Regenerate button** on each group header — calls `regenerateGroup()`
   - **Floating "Regenerate All" button** — calls `regenerateAll()`
   - **"Start Workout" button** — fixed at bottom of screen

3. **First-launch flow**
   - If `UserPreferences` hasn't been configured yet (default equipment is empty), navigate to Settings on first launch
   - Seed `RecoveryConfig` with default values on first database creation

### Tests

- **HomeViewModel tests** (unit, using fake repository):
  - On init, a workout plan is generated and exposed as state
  - `swapExercise()` updates the plan with a different exercise
  - `regenerateAll()` produces a new plan (different from the original)
  - When no equipment is configured, state reflects "needs setup"
- **HomeScreen UI tests** (instrumented compose test):
  - Given a workout plan, the screen renders the correct number of muscle group sections
  - Each section contains the expected number of exercise cards
  - Swap button is visible on each exercise card

### Deliverable
The home screen shows a generated workout. Tapping swap replaces individual exercises. Regenerate rerolls groups or the full workout.

---

## Step 5 — Active Workout Screen

**Goal:** A guided workout flow that lets the user log sets/reps/weight for each exercise with a rest timer between sets.

### Tasks

1. **WorkoutViewModel**
   - Receives the `WorkoutPlan` (via navigation argument or shared state)
   - Tracks the current exercise index and per-exercise log state
   - Manages rest timer state (countdown, skip, extend)
   - On completion, persists a `WorkoutSession` and all `ExerciseLog` entries
   - Updates `PersonalRecord` for each logged exercise

2. **Active workout flow**
   - Display exercises in order (grouped by muscle group)
   - For the current exercise, show:
     - Exercise name, muscle group, mechanic type
     - Input fields: **sets** (stepper), **reps** (stepper), **weight** (numeric input)
     - "Log Set" button to record a set and start the rest timer
     - Previous performance if available: "Last: 3×10 @ 135 lbs"
   - **"Skip Exercise"** button — moves to the next exercise without logging
   - **"Complete Exercise"** button — finalizes the current exercise and moves on

3. **Rest timer**
   - Starts automatically after logging a set
   - Duration based on exercise type from user preferences:
     - Compound: `restCompound` seconds (default 60)
     - Isolation: `restIsolation` seconds (default 45)
     - Bodyweight abdominal (primaryMuscles contains "abdominals" AND equipment is null or "body only"): `restBodyweightAb` seconds (default 30)
   - Visual countdown with a circular progress indicator
   - **Skip** button to dismiss immediately
   - **+15s** button to extend
   - Vibration when timer completes (use `Vibrator` system service)

4. **Workout completion**
   - After the last exercise (or user taps "Finish Early"), show a summary:
     - Total exercises completed
     - Total sets
     - Total volume (sets × reps × weight, summed)
     - Duration (time from start to finish)
   - "Save Workout" button persists everything to the database
   - Navigate back to Home

5. **Data persistence on save**
   - Insert `WorkoutSessionEntity` with current date, muscle groups, `completed = true`
   - Insert `ExerciseLogEntity` for each logged exercise
   - Upsert `PersonalRecordEntity` — update `lastWeight`, `lastReps`, `lastPerformed`; update `maxWeight`/`maxReps` if new records

### Tests

- **WorkoutViewModel tests** (unit, using fake repository):
  - Logging a set starts the rest timer with the correct duration (compound vs isolation vs bodyweight-ab)
  - Skipping an exercise advances to the next without creating a log entry
  - Completing all exercises transitions to the summary state
  - On save, a `WorkoutSession` and all `ExerciseLog` entries are persisted
  - `PersonalRecord` is updated when a new max is logged
- **Rest timer tests** (unit):
  - Timer counts down from the correct starting value
  - Skip resets the timer and dismisses it
  - Extend adds 15 seconds to the remaining time
- **Active workout UI tests** (instrumented compose test):
  - Sets/reps/weight inputs accept numeric values
  - "Log Set" button triggers the rest timer overlay
  - "Skip" and "+15s" buttons are visible during timer

### Deliverable
You can start a workout, log sets/reps/weight for each exercise, get rest timers between sets, and save the session. Data persists in Room.

---

## Step 6 — History Screen

**Goal:** View past workouts in a simple list with the ability to see detail.

### Tasks

1. **HistoryViewModel**
   - Query all `WorkoutSession`s ordered by date descending
   - For a selected session, load its `ExerciseLog` entries

2. **History list screen**
   - `LazyColumn` of past sessions
   - Each row shows: date (formatted), muscle groups trained (as chips/tags), total exercises, total volume
   - Tap a row to expand or navigate to a detail view

3. **Session detail view**
   - Show all exercises logged in that session
   - For each: exercise name, muscle group, sets × reps @ weight
   - No editing in Phase 1 (deferred to Phase 2)

### Tests

- **HistoryViewModel tests** (unit, using fake repository):
  - Sessions are returned in reverse chronological order
  - Selecting a session loads its exercise logs
  - Empty state is handled (no sessions yet)
- **History UI tests** (instrumented compose test):
  - Given sessions, the list renders rows with date and muscle group tags
  - Tapping a row shows the detail view with exercise logs

### Deliverable
After completing workouts, they appear in the History tab with date, groups trained, and detailed logs.

---

## Step 7 — Settings Screen

**Goal:** Configure equipment, experience level, workout size, and weight unit.

### Tasks

1. **SettingsViewModel**
   - Load and persist `UserPreferencesEntity`
   - Expose current preferences as state
   - Save changes immediately (or on a "Save" action)

2. **Settings screen composable**
   - **Experience level** — radio group: Beginner / Intermediate / Expert
   - **Weight unit** — toggle: lbs / kg
   - **Target workout size** — slider from 5 to 12 (default 8), with current value displayed
   - **Rest timer durations** — numeric inputs for compound, isolation, and bodyweight-ab rest times
   - **Equipment selection** — multi-select checklist of all equipment values found in the dataset. Query distinct equipment values from the exercises table to build this list dynamically.

3. **Equipment list**
   Extract all unique `equipment` values from the exercises table (e.g., "barbell", "dumbbell", "cable", "machine", "body only", "kettlebells", etc.). Display as a scrollable checklist. Checking/unchecking updates `availableEquipment` in preferences.

### Tests

- **SettingsViewModel tests** (unit, using fake repository):
  - Loading preferences populates state with stored values
  - Changing equipment updates `availableEquipment` in the database
  - Changing experience level persists correctly
- **Settings UI tests** (instrumented compose test):
  - Equipment checklist renders all distinct equipment values from the database
  - Toggling an equipment item updates the checked state
  - Slider for target workout size shows the current value and updates on drag

### Deliverable
User can configure their equipment, level, preferred workout size, rest timers, and weight unit. Workout generation respects these settings.

---

## Step 8 — Integration & Edge Cases

**Goal:** Wire everything together, handle edge cases, and run the full test suite.

### Tasks

1. **End-to-end flow verification**
   - First launch → Settings → configure equipment → Home generates workout → Start → log exercises → save → appears in History
   - Change equipment in Settings → regenerate workout → only matching exercises appear

2. **Edge cases**
   - No training history (new user): all groups score equally, exercises selected randomly
   - Very few exercises match filters: relax constraints (allow different experience levels), show a warning if < 2 exercises found for a group
   - User has no equipment selected: show "body only" exercises as fallback, prompt to configure equipment
   - All groups recently trained (none fully recovered): still generate a workout using the best-scoring groups (recovery is a soft factor, not a gate)

3. **Performance**
   - Verify the exercise JSON parsing + Room insert completes in under 2 seconds on first launch
   - Verify workout generation is fast enough to not need a loading spinner (target < 200ms)

### Tests

- **End-to-end integration test** (instrumented): launch the app with an in-memory database, navigate through Settings → Home → Active Workout → History, verify data flows correctly across screens
- **Edge case tests** (unit): new user generates a workout, empty equipment falls back to "body only", all groups recently trained still produces a result
- **Full test suite pass**: run all unit and instrumented tests from Steps 1–7 together, verify no regressions

### Deliverable
A fully functional Phase 1 app that can generate workouts, log them, and review history. All tests pass.

---

## Implementation Order Summary

| Order | Step | Depends On | Estimated Complexity |
|-------|------|------------|---------------------|
| 1 | Project Scaffolding | — | Low |
| 2 | Exercise Dataset & Room Database | Step 1 | Medium |
| 3 | Domain Layer: Scoring & Generation | Step 2 | High |
| 4 | Home Screen (Today's Workout) | Steps 2, 3 | Medium |
| 5 | Active Workout Screen | Steps 2, 4 | High |
| 6 | History Screen | Steps 2, 5 | Low |
| 7 | Settings Screen | Step 2 | Medium |
| 8 | Integration & Edge Cases | All | Medium |

Steps 6 and 7 are independent of each other and can be built in either order. Step 3 is the core of the app — get this right and the UI steps are straightforward.

---

## What's Explicitly Deferred to Phase 2+

- Exercise detail view with images and full instructions
- Favorites and excluded exercises management UI
- Muscle group overview dashboard
- Progressive overload display (showing PR during active workout)
- Editing/deleting past workout logs
- Workout summary with volume calculations on the summary screen (basic version in Phase 1)
- Calendar view for history
- Personal records dashboard
- Export, notifications, theming
