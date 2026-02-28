# Phase 2 — Polish Implementation Plan

## Overview

Phase 2 builds on the working MVP to add depth and polish. The scope maps directly to what's defined in the project overview under Phase 2:

- Exercise detail view with images and full instructions
- Progressive overload display during active workouts (last weight/reps and PR)
- Enhanced workout summary with total volume calculation and PR callouts
- Favorites and excluded exercises management
- Edit and delete past workout logs
- Muscle group overview dashboard with freshness indicators

### Phase 1 Bug Fix (prerequisite)

The existing `deleteSession()` in `HistoryViewModel` uses Room's cascade delete for exercise logs but does **not** recalculate personal records for the affected exercises. This means deleting a session that held a PR leaves stale PR data in the database. This is a correctness bug, not a new feature — fix it before starting Phase 2. See Step 4 task 6 for the implementation.

### Foundation from Phase 1

Phase 2 builds on existing infrastructure that's already in place:

- **ExerciseEntity** already stores `images` and `instructions` fields (unused in UI)
- **UserPreferencesEntity** already has `excludedExercises` and `favoritedExercises` fields (unused by generator or UI)
- **PersonalRecordEntity** already tracks `maxWeight`, `maxReps`, `maxWeightDate`, `maxRepsDate` (only `lastWeight`/`lastReps` displayed)
- **WorkoutSummary** already calculates total exercises, sets, volume, and duration
- **Coil** dependency is already configured in `build.gradle.kts` (used for loading bundled asset images)
- **Session delete** already works in History (cascade to exercise logs)
- **Weight unit** is stored in `UserPreferencesEntity` but currently hardcoded to `"lbs"` in all UI displays

### Cross-Cutting Concerns

Two infrastructure changes apply across multiple steps and should be built first:

1. **Centralized user preferences flow.** Multiple ViewModels (Workout, History, Home, MuscleOverview) need access to user preferences — especially `weightUnit`. Rather than querying `UserPreferencesDao` independently in each ViewModel, create a `UserPreferencesRepository` that exposes a `Flow<UserPreferencesEntity>` (backed by Room's reactive query). Each ViewModel collects this flow and receives updates automatically when preferences change. This replaces the per-ViewModel `UserPreferencesDao` queries proposed in individual steps.

   ```kotlin
   @Singleton
   class UserPreferencesRepository @Inject constructor(
       private val dao: UserPreferencesDao
   ) {
       val preferences: Flow<UserPreferencesEntity> = dao.observe()
   }
   ```

   Add a corresponding `observe()` method to `UserPreferencesDao`:
   ```kotlin
   @Query("SELECT * FROM user_preferences WHERE id = 0")
   fun observe(): Flow<UserPreferencesEntity>
   ```

2. **Database index on `exercise_logs.exerciseId`.** Step 4 introduces queries that scan all exercise logs for a given exercise (`getByExerciseId`). As workout history grows over months of use, this query becomes a bottleneck without an index. Add the index to `ExerciseLogEntity` alongside the existing `sessionId` index:

   ```kotlin
   indices = [Index("sessionId"), Index("exerciseId")]
   ```

   This requires a Room database migration from version 1 → 2. Add a migration in `AppDatabase.kt`:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_logs_exerciseId ON exercise_logs(exerciseId)")
       }
   }
   ```

---

## Step 1 — Exercise Detail View with Images

**Goal:** A full-screen detail view for any exercise showing images, instructions, equipment, muscles, and mechanic type.

### Tasks

1. **Image strategy — bundled assets**

   The `images` field in `ExerciseEntity` contains relative paths like `"Barbell_Curl/0.jpg"`. Rather than loading images from GitHub at runtime (which adds network dependency, caching complexity, and error handling), bundle all exercise images directly in the APK under `assets/exercises/`.

   **Setup:** Clone or download the image files from the [free-exercise-db](https://github.com/yuhonas/free-exercise-db) repository's `exercises/` directory into `app/src/main/assets/exercises/`. The directory structure mirrors the relative paths already stored in `ExerciseEntity` (e.g., `assets/exercises/Barbell_Curl/0.jpg`).

   **Loading:** Use Coil's `AsyncImage` with an asset URI scheme:
   ```kotlin
   AsyncImage(
       model = "file:///android_asset/exercises/$imagePath",
       contentDescription = "Image showing how to perform ${exercise.name}"
   )
   ```
   No custom `ImageLoader`, disk cache, or network configuration needed — images load instantly from local assets. Show a placeholder `Icon(Icons.Default.FitnessCenter)` only for exercises that have an empty `images` list.

   **Trade-off:** This increases APK size (~100-250MB depending on image count/size) but the app is sideloaded via GitHub Actions, not distributed through the Play Store, so APK size limits don't apply. The benefit is true offline-first behavior with zero network code, no cache management, and no loading states for images.

2. **Add a navigation route**

   Add `ExerciseDetailRoute` with an exercise ID parameter:
   ```kotlin
   @Serializable
   data class ExerciseDetailRoute(val exerciseId: String)
   ```
   Register it in `AppNavigation.kt` as a non-bottom-nav destination (navigated to from exercise cards, not from the bottom bar).

3. **ExerciseDetailViewModel**
   - Inject `ExerciseDao`
   - On init, load exercise by ID via `exerciseDao.getById(exerciseId)`
   - Expose `ExerciseDetailUiState` with the loaded exercise, loading flag, and error state
   - No new DAO methods needed — `getById` already exists

4. **ExerciseDetailScreen composable**
   - **Image section:** horizontal pager or vertical scroll of exercise images loaded via Coil `AsyncImage` from bundled assets. Show a placeholder `Icon(Icons.Default.FitnessCenter)` when no images are available.
   - **Header:** exercise name, mechanic badge (compound/isolation), force type (push/pull/static), level badge
   - **Equipment:** displayed as a chip (e.g., "Barbell")
   - **Muscles:** primary muscles as filled chips, secondary muscles as outlined chips
   - **Instructions:** numbered step-by-step list from the `instructions` field
   - **Back navigation:** top app bar with back arrow

5. **Wire up navigation from existing screens**
   - **HomeScreen:** make exercise cards tappable — tap navigates to `ExerciseDetailRoute(exerciseId)`
   - **HistoryScreen:** make exercise names in session detail tappable — same navigation
   - Keep swap/regenerate buttons functional (tap the card body for detail, tap the swap icon for swap)

   **Note — UX deviation from spec:** The project overview §7.1 says "tap a card to expand and see instructions + demonstration images" (inline expansion). This plan instead navigates to a full-screen detail view. The full-screen approach is preferred because exercise images and multi-step instructions are too content-heavy for inline expansion within a workout card list — it would push other exercises off screen and make the Home screen unwieldy. The detail screen also provides a consistent entry point for favorite/exclude actions (Step 3).

### Tests

- **ExerciseDetailViewModel tests** (unit):
  - Loading a valid exercise ID populates state with exercise data
  - Loading an invalid ID sets error state
- **ExerciseDetailScreen UI tests** (instrumented compose test):
  - Given an exercise, the screen renders name, equipment, muscles, and instructions
  - Image placeholder is shown when exercise has no images
  - Back button navigates back

### Deliverable
Tapping an exercise card anywhere in the app opens a detail screen showing images, muscles, equipment, and step-by-step instructions. Back navigation returns to the previous screen.

---

## Step 2 — Progressive Overload Display & Enhanced Workout Summary

**Goal:** Show personal records alongside last performance during active workouts, highlight new PRs when they're set, and display a detailed summary on workout completion.

### Tasks

1. **Enhance `WorkoutViewModel.loadPreviousPerformance()`**

   Currently loads only `lastWeight` and `lastReps` from `PersonalRecordEntity`. Extend to also load `maxWeight`, `maxReps`, `maxWeightDate`, and `maxRepsDate`:
   ```kotlin
   data class ExercisePerformance(
       val lastWeight: Double?,
       val lastReps: Int?,
       val maxWeight: Double,
       val maxReps: Int,
       val maxWeightDate: Long?,
       val maxRepsDate: Long?
   )
   ```
   Update `WorkoutUiState` to hold `ExercisePerformance?` instead of separate `previousWeight`/`previousReps` fields.

2. **New PR detection**

   After `completeExercise()` persists the log, compare the logged weight and reps against the current `maxWeight`/`maxReps`. If either is exceeded:
   - Add to a `newPRs: List<NewPR>` in `WorkoutUiState` (exercise name, PR type, old value, new value)
   - Show a brief visual indicator on the exercise card (e.g., a "NEW PR!" badge with an accent color)

   ```kotlin
   data class NewPR(
       val exerciseId: String,
       val exerciseName: String,
       val type: PRType,        // WEIGHT or REPS
       val oldValue: Double,
       val newValue: Double
   )
   ```

3. **Update active workout UI**

   Enhance the previous performance card in `ExerciseLoggingScreen`:
   - Keep: `"Last: 10 reps @ 135 lbs"`
   - Add: `"PR: 185 lbs (Jan 15) · 12 reps (Feb 2)"`
   - Use the weight unit from `UserPreferences` instead of hardcoded `"lbs"`
   - When no PR exists yet (first time doing this exercise), show `"First time — no previous data"`

4. **Enhance `WorkoutSummary` composable**

   The current summary shows total exercises, sets, volume, and duration. Add:

   - **Weight unit from preferences** — replace hardcoded `"lbs"` with the user's configured unit
   - **Muscle group breakdown** — show volume per muscle group (sum of sets × reps × weight for exercises in each group), using the `muscleGroup` field from `ExerciseLogEntity`
   - **Per-exercise detail** — expandable list showing each logged exercise with its sets × reps @ weight
   - **New PRs section** — if any PRs were set during this session, list them with the exercise name, PR type (weight/reps), and old → new value
   - Keep the Save/Discard buttons at the bottom

5. **Weight unit from centralized preferences**

   Inject `UserPreferencesRepository` (see Cross-Cutting Concerns) into `WorkoutViewModel` and collect the `preferences` flow. Map `weightUnit` into `WorkoutUiState` so both the active workout screen and summary display the correct unit. This replaces any direct `UserPreferencesDao` query — all ViewModels that need preferences should collect from the shared repository flow.

### Tests

- **WorkoutViewModel tests** (unit, using fake repository):
  - `loadPreviousPerformance()` populates both last and max values
  - New PR is detected when logged weight exceeds maxWeight
  - New PR is detected when logged reps exceed maxReps
  - No PR flagged when values are below max
  - First-time exercise (no PersonalRecord) shows null performance
- **Workout summary tests** (unit):
  - Volume per muscle group is calculated correctly from log entries
  - New PRs appear in the summary list
  - Weight unit matches user preferences
- **Active workout UI tests** (instrumented compose test):
  - PR line is displayed alongside last performance
  - Weight unit label matches preferences

### Deliverable
During an active workout, each exercise shows both last performance and personal records. When a new PR is set, it's visually highlighted. The workout completion summary shows volume breakdown by muscle group, per-exercise detail, and any new PRs achieved.

---

## Step 3 — Favorites & Excluded Exercises Management

**Goal:** Users can favorite and exclude exercises. The workout generator respects these preferences. A management UI lets users browse and toggle favorites/exclusions.

### Tasks

1. **Integrate favorites/exclusions into WorkoutGenerator**

   The `UserPreferencesEntity` already has `excludedExercises: List<String>` and `favoritedExercises: List<String>` fields. Update `WorkoutGenerator.selectExercisesForGroup()`:

   - **Excluded exercises:** after querying exercises for a group, filter out any whose `id` is in `excludedExercises`. This is a simple list filter — no DAO changes needed.
   - **Favorited exercises:** after filtering, sort candidates so favorited exercises appear first (before the compound-first / recently-performed deprioritization logic). Favorited exercises should be preferred but not guaranteed — the compound-first rule still applies.
   - **Favorited group bonus:** as described in overview §4.2, if a muscle group contains exercises that the user has favorited, apply a small score bonus (+5%) to that group during scoring. Add a `favoritedBonus` parameter to `MuscleGroupScorer.scoreGroup()`.

2. **Add SettingsViewModel methods**

   Add to `SettingsViewModel`:
   ```kotlin
   fun toggleFavorite(exerciseId: String)
   fun toggleExcluded(exerciseId: String)
   fun isFavorited(exerciseId: String): Boolean
   fun isExcluded(exerciseId: String): Boolean
   ```
   Each method reads the current preferences, adds/removes the exercise ID from the relevant list, and upserts.

3. **Favorites & exclusions management screen**

   Add a new route `ManageExercisesRoute` accessible from Settings. This screen provides:

   - **Search bar** — filter exercises by name (client-side filtering from `exerciseDao.getAll()`)
   - **Exercise list** — scrollable list of exercises, each showing:
     - Exercise name
     - Equipment and primary muscles as small chips
     - Star icon toggle (filled = favorited)
     - X/ban icon toggle (filled = excluded)
   - **Filter tabs** — "All", "Favorites", "Excluded" to quickly see current selections
   - **Counts** — show "N favorites, M excluded" in the header

   Create a `ManageExercisesViewModel` that:
   - Loads all exercises via `exerciseDao.getAll()`
   - Loads current preferences for favorites/exclusions lists
   - Provides search, filter, toggle actions
   - Persists changes via `UserPreferencesDao.upsert()`

4. **Add favorite/exclude buttons to ExerciseDetailScreen**

   In the exercise detail view (Step 1), add a top app bar action menu or floating buttons:
   - Star icon to toggle favorite
   - Ban icon to toggle excluded
   - Mutually exclusive — favoriting removes from excluded and vice versa

5. **Visual indicators on exercise cards**

   On `HomeScreen` exercise cards, show small indicators:
   - Filled star icon for favorited exercises
   - These are read-only indicators (tap the card to go to detail, use detail or Settings to manage)

6. **Add a "Manage Exercises" entry in SettingsScreen**

   Add a clickable row in the Settings screen: `"Manage Favorites & Exclusions"` with a count subtitle (e.g., "3 favorites, 1 excluded"). Tapping navigates to `ManageExercisesRoute`.

7. **Stale plan detection when excluding a current exercise**

   If the user excludes an exercise (from the detail view or management screen) that is currently in the Home screen's workout plan, the plan becomes stale. Rather than auto-regenerating the entire plan (which would discard any locks or manual swaps), show a subtle banner on the Home screen: `"Workout plan contains excluded exercises. Tap to regenerate."` Tapping the banner triggers `regenerateAll()`. Detect staleness by comparing the plan's exercise IDs against the current `excludedExercises` list whenever `HomeViewModel` collects a preferences update from `UserPreferencesRepository`.

### Tests

- **WorkoutGenerator tests** (unit, using fake repository):
  - Excluded exercises never appear in generated plans
  - Favorited exercises are preferred when available for a group
  - Excluding all exercises for a group triggers constraint relaxation / warning
  - Favorited group bonus increases group score by expected amount
- **ManageExercisesViewModel tests** (unit):
  - Search filters exercises by name (case-insensitive)
  - Toggling favorite adds/removes from preferences
  - Toggling excluded adds/removes from preferences
  - Favoriting an excluded exercise removes it from excluded
  - Filter tabs show correct subsets
- **Settings UI tests** (instrumented compose test):
  - "Manage Favorites & Exclusions" row is visible with correct counts
  - Navigation to management screen works

### Deliverable
Users can favorite or exclude exercises from the detail view or a dedicated management screen. The workout generator excludes excluded exercises and prioritizes favorites. Favorited exercises are visually indicated on workout cards.

---

## Step 4 — Edit & Delete Past Workout Logs

**Goal:** Users can edit sets/reps/weight for individual exercise logs within a past session and delete individual logs. Personal records are recalculated when affected.

### Tasks

1. **Add ExerciseLogDao methods**

   ```kotlin
   @Update
   suspend fun update(log: ExerciseLogEntity)

   @Query("DELETE FROM exercise_logs WHERE logId = :logId")
   suspend fun deleteById(logId: Long)

   @Query("SELECT * FROM exercise_logs WHERE exerciseId = :exerciseId ORDER BY logId DESC")
   suspend fun getByExerciseId(exerciseId: String): List<ExerciseLogEntity>
   ```

   The `getByExerciseId` query is needed for PR recalculation — when a log is edited or deleted, recalculate the max from all remaining logs for that exercise.

2. **PersonalRecord recalculation**

   Create a `PersonalRecordRecalculator` utility (or method on the repository):
   ```kotlin
   suspend fun recalculateForExercise(exerciseId: String)
   ```
   - Query all `ExerciseLogEntity` rows for the exercise
   - Find the max weight and max reps across all logs
   - Find the most recent log for lastWeight/lastReps/lastPerformed
   - Upsert the `PersonalRecordEntity` with recalculated values
   - If no logs remain, delete the PersonalRecord entirely

   Add a corresponding DAO method:
   ```kotlin
   @Query("DELETE FROM personal_records WHERE exerciseId = :exerciseId")
   suspend fun deleteByExerciseId(exerciseId: String)
   ```

3. **Update HistoryViewModel**

   Add edit and delete actions:
   ```kotlin
   fun startEditingLog(log: ExerciseLogEntity)
   fun updateLog(logId: Long, sets: Int, reps: Int, weight: Double)
   fun cancelEdit()
   fun deleteLog(logId: Long, exerciseId: String)
   ```

   `updateLog` persists the change and triggers PR recalculation. `deleteLog` removes the log and triggers PR recalculation.

   Add to `HistoryUiState`:
   ```kotlin
   val editingLog: ExerciseLogEntity? = null
   ```

4. **Update HistoryScreen session detail**

   In the expanded session detail view, for each exercise log:
   - **Tap** the log row to enter inline edit mode
   - **Edit mode:** show editable fields for sets, reps, weight with save/cancel buttons (same stepper/input pattern used in the active workout screen)
   - **Delete button:** icon button with confirmation dialog ("Delete this exercise log? This cannot be undone.")
   - After save or delete, refresh the session detail and show updated values

5. **Handle edge cases**
   - Deleting the last log in a session: prompt to delete the entire session, or leave the session with 0 exercises (prefer prompt)
   - Editing a log that held a PR: recalculation may lower the PR — this is correct behavior
   - Deleting a session (existing feature): already cascades to logs, but now also needs to recalculate PRs for all affected exercises

6. **Fix session delete to recalculate PRs (Phase 1 bug fix)**

   The existing `deleteSession()` in `HistoryViewModel` uses cascade delete for logs but doesn't recalculate PRs. **This is a correctness bug in Phase 1** — deleting a session that contained a PR leaves stale max values in `PersonalRecordEntity`. Fix it by:
   - Before deleting, query all exercise logs for the session to get affected exercise IDs
   - After deleting, recalculate PRs for each affected exercise using `PersonalRecordRecalculator`

   This fix should be implemented early (it's listed as a Phase 1 bug fix prerequisite in the Overview section) and can land independently of the rest of Step 4.

### Tests

- **ExerciseLogDao tests** (instrumented):
  - `update` modifies sets/reps/weight for an existing log
  - `deleteById` removes a single log without affecting others in the same session
  - `getByExerciseId` returns all logs for an exercise across sessions
- **PersonalRecord recalculation tests** (unit):
  - After deleting a PR-holding log, PR is recalculated from remaining logs
  - After editing a PR-holding log to a lower value, PR reflects the new max from remaining logs
  - After deleting the only log for an exercise, PersonalRecord is deleted
  - After editing a log to exceed the current PR, PR is updated
- **HistoryViewModel tests** (unit):
  - `updateLog` persists changes and triggers recalculation
  - `deleteLog` removes the log and triggers recalculation
  - `deleteSession` recalculates PRs for all affected exercises
  - Edit mode state is managed correctly (start, cancel, save transitions)
- **History UI tests** (instrumented compose test):
  - Tapping a log row enters edit mode with editable fields
  - Save button persists and exits edit mode
  - Delete button shows confirmation dialog
- **Test infrastructure:** Extend `FakeWorkoutRepository` to support the new `getMuscleGroupOverview()` method and any other new repository methods added across Phase 2 steps. Each step that adds repository methods must also add the corresponding fake implementation so unit tests can run without a real database.

### Deliverable
Users can tap any exercise log in a past session to edit its sets, reps, or weight. Individual logs can be deleted. Personal records are automatically recalculated when affected by edits or deletes.

---

## Step 5 — Muscle Group Overview Dashboard

**Goal:** A dashboard showing all 7 muscle groups with recovery status, freshness indicators, and training frequency.

### Tasks

1. **Define freshness status thresholds**

   Based on the ratio of hours since last trained to minimum recovery hours:

   | Status | Condition | Color | Description |
   |---|---|---|---|
   | Recovering | ratio < 1.0 | Blue/Grey | Within recovery window |
   | Ready | 1.0 ≤ ratio < 2.0 | Green | Recovered, good to train |
   | Due | 2.0 ≤ ratio < 4.0 | Yellow/Amber | Should train soon |
   | Overdue | ratio ≥ 4.0 or never trained | Red | Significantly past ideal window |

2. **Add repository methods**

   Add to `IWorkoutRepository` and `WorkoutRepository`:
   ```kotlin
   suspend fun getMuscleGroupOverview(): List<MuscleGroupStatus>
   ```

   This method computes, for each of the 7 muscle groups:
   - Last trained date (from workout sessions)
   - Hours since last trained
   - Recovery percentage (hours since / min recovery hours)
   - Freshness status (Recovering / Ready / Due / Overdue)
   - Session count in the last 14 days
   - Current score from `MuscleGroupScorer`

   ```kotlin
   data class MuscleGroupStatus(
       val muscleGroup: MuscleGroup,
       val lastTrainedMillis: Long?,
       val hoursSinceLastTrained: Double?,
       val recoveryPercentage: Double,     // 0.0 = just trained, 1.0 = fully recovered
       val freshnessStatus: FreshnessStatus,
       val sessionsLast14Days: Int,
       val currentScore: Double
   )

   enum class FreshnessStatus { RECOVERING, READY, DUE, OVERDUE }
   ```

3. **Restructure Home tab to surface muscle overview**

   Rather than adding a new bottom nav tab or replacing the Workout tab, make the muscle group overview the **landing content of the Home tab**. The current Home tab immediately shows a generated workout plan, but users benefit from seeing their recovery status before generating. Restructure the Home tab as:

   - **Default state (no active plan):** Show the `MuscleOverviewScreen` content (7 muscle group cards with freshness indicators) with a prominent "Generate Workout" FAB or button. This gives the user context about what's recovered before they generate.
   - **After generating:** Show the current workout plan (existing `HomeScreen` content) with a top bar action or button to return to the muscle overview.
   - **During active workout:** The active workout screen is still navigated to via "Start Workout" and is a non-bottom-nav destination.

   Replace the "Workout" bottom nav tab with "Muscles" as a dedicated route for the full `MuscleOverviewScreen` with exercise browsing. This keeps the bottom nav at 4 tabs: **Home** (overview + generate), **Muscles** (dashboard + exercise browser), **History**, **Settings**.

   This avoids adding a 5th tab, preserves the Home tab as the entry point, and ensures the muscle overview is immediately visible rather than buried behind navigation.

4. **MuscleOverviewViewModel**
   - On init, load muscle group overview data from the repository
   - Expose `MuscleOverviewUiState` with the list of `MuscleGroupStatus`
   - Refresh on resume (when returning from a workout)
   - Action: `selectGroup(muscleGroup)` — navigates to a filtered exercise list for that group

5. **MuscleOverviewScreen composable**

   - **Grid layout** (2 columns) or **list layout** of 7 muscle group cards
   - **Each card shows:**
     - Muscle group name and classification label (e.g., "Major Push")
     - Freshness status with color-coded indicator (background tint or left border)
     - Time since last trained: `"2 days ago"`, `"12 hours ago"`, or `"Never trained"` (text label alongside color — don't rely on color alone per accessibility spec)
     - Recovery progress bar: fills from 0% to 100% (capped visually at 100%, even if ratio > 1.0)
     - Sessions in last 14 days: `"3 sessions"` as small text
   - **Tap a card** to navigate to the exercise detail list for that group (or show a bottom sheet with exercises)

6. **Muscle group exercise list** (optional sub-screen)

   When tapping a muscle group card, show a list of exercises for that group filtered by the user's equipment and experience level. Each exercise is tappable to navigate to the exercise detail view (Step 1). This reuses `ExerciseDao.getByMuscle()` and the equipment/level filtering from the repository.

   This can be a simple `MuscleGroupExercisesRoute(muscleGroup: String)` screen or a bottom sheet — keep it lightweight.

### Tests

- **MuscleOverviewViewModel tests** (unit, using fake repository):
  - All 7 muscle groups are represented in state
  - Freshness status is correct for each threshold (recovering, ready, due, overdue)
  - Never-trained groups show as "Overdue" with null last trained date
  - Session counts reflect 14-day window
- **Freshness calculation tests** (unit):
  - Ratio < 1.0 → RECOVERING
  - Ratio 1.0–1.99 → READY
  - Ratio 2.0–3.99 → DUE
  - Ratio ≥ 4.0 → OVERDUE
  - No training history → OVERDUE
- **MuscleOverviewScreen UI tests** (instrumented compose test):
  - 7 muscle group cards are displayed
  - Each card shows name, status text, and progress bar
  - Color coding matches freshness status

### Deliverable
A muscle group dashboard shows all 7 groups with color-coded freshness indicators, recovery progress bars, time-since-trained labels, and training frequency. Tapping a group shows exercises available for that group.

---

## Step 6 — Integration & Polish

**Goal:** Wire all Phase 2 features together, verify cross-feature interactions, accessibility, and run the full test suite.

### Tasks

1. **End-to-end flow verification**
   - Home → tap exercise card → Exercise Detail → favorite it → back → regenerate → favorited exercise is prioritized
   - Home → Start Workout → see PR display → log a set that beats PR → "NEW PR!" indicator → complete → summary shows new PR → save → History
   - History → expand session → edit a log → verify PR recalculation → back to Muscle Overview → verify freshness reflects the session
   - Settings → Manage Exercises → exclude an exercise → back → Home → regenerate → excluded exercise does not appear
   - Muscle Overview → tap a group → see filtered exercises → tap exercise → detail view

2. **Cross-feature consistency**
   - Weight unit from preferences is used everywhere: active workout, workout summary, history detail, exercise detail (if applicable)
   - Editing a history log recalculates PRs → next workout shows updated PR values
   - Excluding an exercise that was in the current workout plan → regenerating removes it
   - Favoriting exercises in one group → that group gets a score boost → visible in Muscle Overview scores

3. **Animations and transitions**

   A "Polish" phase should feel polished. Add targeted animations:

   - **PR celebration:** When a new PR is detected after `completeExercise()`, play a brief scale-up animation on the "NEW PR!" badge and trigger a short haptic pulse (`HapticFeedbackType.LongPress`). Keep it subtle — a 300ms scale from 0.8→1.0 with overshoot interpolation, not confetti.
   - **Screen transitions:** Use Compose Navigation's `AnimatedNavHost` with shared element transitions for exercise cards navigating to the detail view. The exercise name and muscle chips should animate smoothly between the card and detail header.
   - **Freshness progress bars:** On the Muscle Overview dashboard, animate the recovery progress bars from 0 to their actual value with a 500ms duration when the screen first loads or refreshes.
   - **Rest timer:** Animate the circular progress indicator smoothly (already uses `CircularProgressIndicator`, ensure it uses `animateFloatAsState` rather than snapping between values).

4. **Rest timer audio/vibration**

   The project overview §7.2 specifies "Audio/vibration alert when rest period ends." The current implementation only has visual UI (countdown + circular progress). Add:

   - **Vibration:** When the rest timer hits 0, trigger a vibration pattern via `Vibrator` service (e.g., two short pulses: 0, 200, 100, 200). Request `VIBRATE` permission in the manifest.
   - **Audio:** Play a short system notification sound via `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)`. Respect the device's current ringer mode (no sound if on silent/vibrate).
   - Both vibration and audio should fire once when the timer completes, not repeatedly.

5. **Accessibility**
   - `contentDescription` on all exercise images (`"Image showing how to perform [exercise name]"`)
   - Color contrast on freshness indicators meets 4.5:1 ratio against card backgrounds
   - Freshness status conveyed via both color AND text label (e.g., "Ready — 2 days ago")
   - All icon buttons (favorite star, exclude ban, swap, delete) have `contentDescription`
   - Tap targets meet 48dp minimum
   - PR celebration indicator is not color-only (text badge, not just a color change)

6. **Edge cases**
   - Exercise with no images: show a placeholder icon, no image pager
   - Exercise with no instructions: show "No instructions available"
   - Exercise with null equipment: display "Body Only"
   - Editing the only log for an exercise to 0 weight/reps: treat as delete, prompt user
   - Excluding all exercises for a muscle group: workout generation warns and skips the group
   - Favoriting an exercise that doesn't match current equipment: it's still favorited but won't appear in workouts until equipment matches (no special handling needed)
   - Exercise images missing from assets (corrupted APK or missing file): placeholder icon shown gracefully

7. **Navigation cleanup**
   - Ensure back stack behaves correctly: Exercise Detail → back returns to previous screen (Home, History, or Muscle Overview depending on origin)
   - Deep navigation: Muscle Overview → exercise list → exercise detail → back → back returns to overview
   - Active workout screen is not accessible from bottom nav (only from Home's "Start Workout")

### Tests

- **End-to-end integration test** (instrumented): launch the app, navigate through the full Phase 2 flow — detail view, favorites, active workout with PR tracking, summary, history editing, muscle overview
- **Cross-feature tests** (unit): edit a log → verify PR recalculation → verify next workout PR display reflects change
- **Accessibility audit**: verify all images have content descriptions, all interactive elements meet minimum tap target size
- **FakeWorkoutRepository completeness**: verify the test fake implements all new repository methods from Phase 2 and provides reasonable defaults for each
- **Full test suite pass**: run all unit and instrumented tests from Phase 1 + Phase 2 steps together, verify no regressions

### Deliverable
All Phase 2 features work together seamlessly. Navigation flows are correct. Accessibility guidelines are met. The full test suite passes.

---

## Step 7 — Active Workout Persistence

**Goal:** Survive Android process death during an active workout. Currently, the workout plan is held in an in-memory singleton (`ActiveWorkoutState`), which means if the user switches to another app and Android kills the process, the entire workout-in-progress is lost.

### Tasks

1. **Persist active workout state to Room**

   Create an `ActiveWorkoutEntity` that stores the workout-in-progress:
   ```kotlin
   @Entity(tableName = "active_workout")
   data class ActiveWorkoutEntity(
       @PrimaryKey val id: Int = 0,        // singleton row
       val planJson: String,                // serialized WorkoutPlan
       val currentIndex: Int,               // which exercise the user is on
       val logEntries: String,              // serialized list of completed ExerciseLogEntry
       val startedAtMillis: Long,
       val newPRsJson: String               // serialized list of NewPR (from Step 2)
   )
   ```

   Add an `ActiveWorkoutDao` with:
   ```kotlin
   @Upsert suspend fun save(state: ActiveWorkoutEntity)
   @Query("SELECT * FROM active_workout WHERE id = 0") suspend fun get(): ActiveWorkoutEntity?
   @Query("DELETE FROM active_workout") suspend fun clear()
   ```

   This requires a Room migration (version 2 → 3, or combined with the index migration as 1 → 2 if both land together).

2. **Update WorkoutViewModel to persist on every state change**

   After each meaningful state change (exercise completed, set logged, exercise skipped), serialize the current state and call `activeWorkoutDao.save()`. This is a lightweight upsert of a single row. On ViewModel init, check for an existing `ActiveWorkoutEntity` — if present, restore the workout from the persisted state instead of reading from `ActiveWorkoutState`.

3. **Clear persisted state on workout completion or discard**

   When `saveWorkout()` or `discardWorkout()` is called, call `activeWorkoutDao.clear()` to remove the persisted state.

4. **Resume prompt on app relaunch**

   In `HomeViewModel`, on init check if an `ActiveWorkoutEntity` exists. If so, show a banner: `"You have an unfinished workout. Tap to resume."` Tapping navigates to `WorkoutRoute`, where `WorkoutViewModel` restores from the persisted state.

5. **Remove `ActiveWorkoutState` singleton**

   Once persistence is in place, the in-memory singleton is no longer needed. Remove `ActiveWorkoutState` and update `HomeViewModel.startWorkout()` to persist the plan to `ActiveWorkoutEntity` instead of writing to the singleton.

### Tests

- **ActiveWorkoutDao tests** (instrumented):
  - Save and retrieve round-trips correctly
  - Clear removes the active workout
  - Saving a second time overwrites the first (upsert)
- **WorkoutViewModel tests** (unit):
  - On init with persisted state, restores workout at correct exercise index
  - On init without persisted state, starts fresh from the plan
  - Completing an exercise triggers a persist
  - Save/discard clears persisted state
- **HomeViewModel tests** (unit):
  - Shows resume banner when active workout exists in database
  - No banner when no active workout exists

### Deliverable
Active workouts survive process death. If the user leaves the app mid-workout and Android kills the process, reopening the app shows a prompt to resume. The in-memory singleton is replaced by Room persistence.

---

## Implementation Order Summary

| Order | Step | Depends On | Estimated Complexity |
|-------|------|------------|---------------------|
| 0 | Phase 1 bug fix (PR recalc on session delete) | — | Low |
| 0 | Cross-cutting concerns (UserPreferencesRepository, DB index + migration) | — | Low |
| 1 | Exercise Detail View with Images | — | Medium |
| 2 | Progressive Overload & Enhanced Summary | Cross-cutting | Medium |
| 3 | Favorites & Excluded Exercises | Steps 1, 2 | Medium |
| 4 | Edit & Delete Past Workout Logs | Cross-cutting | High |
| 5 | Muscle Group Overview Dashboard | — | Medium |
| 6 | Integration & Polish | All | Medium-High |
| 7 | Active Workout Persistence | Step 2 (NewPR model) | Medium |

The Phase 1 bug fix and cross-cutting concerns should land first as they are prerequisites for multiple steps. Steps 1, 2, 4, and 5 are independent of each other at the data/logic layer and can be built in any order. Step 3 has a soft dependency on Step 1 (favorite/exclude buttons on the detail screen) and Step 2 (favorited group bonus in scorer). Step 4 is the most complex due to PR recalculation logic. Step 6 is now Medium-High due to the addition of animations, audio/vibration, and FakeWorkoutRepository expansion. Step 7 can be built at any point after Step 2 (it references the `NewPR` model) but is best done late since it replaces the `ActiveWorkoutState` singleton that other steps still reference during development.

---

## What's Explicitly Deferred to Phase 3

- Personal records dashboard per exercise
- Calendar view for history
- Export workout data (CSV or JSON)
- Optional notifications / workout reminders
- Theming and dark mode support
