# Project Overview

## Astute Body — Smart Workout Recommendation App

**Android Application (Personal Use)**

---

## 1. Project Summary

Astute Body is a personal Android workout app that intelligently recommends exercises based on a scoring model that considers recovery time, training recency, muscle group balance, and exercise overlap. Each workout is dynamically sized (typically 6-9 exercises across 2-4 muscle groups) based on what's recovered and what's been neglected. The app uses the free-exercise-db open-source dataset (800+ exercises, public domain) as its exercise library.

The goal is to create a simpler, more focused alternative to apps like Fitbod — one that prioritizes balanced muscle group rotation and progressive overload tracking without unnecessary complexity.

## 2. Core Features

### 2.1 Workout Generation

- Dynamically generate a workout of 6-9 exercises across 2-4 muscle groups based on a scoring model
- Score each muscle group using recovery status, staleness, training frequency balance, and overlap penalties
- Allocate more exercises to higher-priority (higher-scoring) groups, fewer to supporting groups
- Within each group, lead with a compound movement followed by isolation exercises (using the `mechanic` field)
- Respect user's equipment availability and experience level
- Target a total workout size of ~6-9 exercises, configurable via a preferred workout length setting

### 2.2 Workout Tracking

- Log completed workouts with date, exercises performed, sets, reps, and weight
- Track last-trained date per muscle group and per exercise
- View workout history with the ability to see past sessions
- Edit or undo logged data for past workout sessions to correct mistakes

### 2.3 Progressive Overload Tracking

- During active workouts, display what weight and reps were used last time for each exercise
- Track personal records (max weight, max reps) per exercise
- Highlight when the user exceeds a previous best

### 2.4 User Preferences

- Set available equipment (filters exercises from the dataset)
- Set experience level (beginner / intermediate / expert)
- Option to exclude specific exercises
- Option to favorite exercises (increases selection priority)

## 3. Muscle Group Architecture

The free-exercise-db dataset uses 17 individual muscle identifiers. For workout planning purposes, these are consolidated into 7 trainable muscle groups that map to natural workout patterns:

| Muscle Group | Includes (from dataset) | Classification | Min Recovery |
|---|---|---|---|
| **Chest** | chest, triceps | Major (Push) | 48 hours |
| **Back** | lats, middle back, lower back, traps | Major (Pull) | 48–72 hours |
| **Shoulders** | shoulders, neck | Major (Push) | 48 hours |
| **Arms** | biceps, triceps, forearms | Minor | 24–48 hours |
| **Legs (Push)** | quadriceps, glutes, abductors, calves | Major (Push) | 48–72 hours |
| **Legs (Pull)** | hamstrings, adductors, glutes | Major (Pull) | 48–72 hours |
| **Core** | abdominals | Major | 24 hours |

Note: Triceps and glutes intentionally appear in multiple groups, reflecting how they are naturally recruited across different movement patterns. Recency tracking operates at the individual exercise level to prevent repetition across overlapping groups.

## 4. Recommendation Algorithm

### 4.1 Overview

The algorithm uses a scoring model to select muscle groups and allocate exercises, rather than rigid group counts or hard recovery cutoffs. This produces workouts that adapt naturally to training frequency, recovery state, and long-term balance.

### 4.2 Muscle Group Scoring

Each of the 7 muscle groups receives a composite score from 0-100 based on four weighted factors:

**Factor 1 — Recovery Status (weight: 35%)**

How recovered the group is, as a percentage of its minimum recovery time elapsed. A group at 100%+ recovery gets full points. A group at 50% recovery gets roughly half points. A group at 0% (just trained) gets near-zero points. This is a continuous curve, not a binary gate — a group at 95% recovery can still be selected if it scores well on other factors.

Formula: `min(hours_since_trained / min_recovery_hours, 1.5) / 1.5`

The cap at 1.5x prevents groups that have been resting for weeks from scoring disproportionately high on recovery alone.

**Factor 2 — Staleness (weight: 30%)**

How long since the group was last trained, normalized across all groups. The stalest group gets full points, the most recently trained gets the fewest. Uses a logarithmic curve so the difference between 2 days and 4 days matters more than the difference between 10 days and 12 days.

Formula: `log(hours_since_trained + 1) / log(max_hours_across_all_groups + 1)`

**Factor 3 — Frequency Balance (weight: 25%)**

How many times the group has been trained over the past 14 days compared to the average across all groups. Groups trained less frequently than average get a boost; groups trained more frequently get penalized. This prevents long-term drift where some groups are consistently favored.

Formula: `1 - (group_sessions_last_14d / max_sessions_last_14d)`

**Factor 4 — Overlap Penalty (weight: 10%)**

Applied after initial scoring, this reduces a group's effective score if it shares muscles with a group already selected for the current workout. For example, if Chest is already selected, Arms gets penalized because both hit triceps. The penalty is proportional to the number of shared muscles.

Formula: `score * (1 - 0.3 * shared_muscle_ratio)` where `shared_muscle_ratio` is the fraction of the candidate group's muscles that overlap with already-selected groups.

**Final score:** `(recovery * 0.35) + (staleness * 0.30) + (frequency_balance * 0.25) - overlap_penalty`

Favorited groups can receive a small bonus (+5-10% to final score). For brand-new users with no history, all groups start with equal scores and are selected randomly.

### 4.3 Workout Sizing and Exercise Allocation

Rather than a fixed number of groups and exercises, the algorithm dynamically sizes each workout:

**Step 1 — Select muscle groups.** Rank all 7 groups by score. Select the top 2-4 groups, stopping when adding another group would either push the total exercise count beyond the target range or when the next group's score drops below a minimum threshold (e.g., 20% of the top group's score).

**Step 2 — Allocate exercises per group.** Distribute exercises proportionally to each group's score within a target workout size of 6-9 exercises (configurable). Higher-scoring groups get 3-4 exercises; lower-scoring groups get 2. Every selected group gets at least 2 exercises.

Example allocations:

| Scenario | Groups Selected | Exercise Distribution | Total |
|---|---|---|---|
| Balanced training | 3 groups | 3 / 3 / 2 | 8 |
| One group very overdue | 2 major + 1 minor | 4 / 3 / 2 | 9 |
| Frequent trainer, many recovered | 4 groups | 2 / 2 / 2 / 2 | 8 |
| New user, no history | 3 random groups | 2 / 2 / 2 | 6 |

**Step 3 — Select exercises within each group.**

1. Filter the dataset to exercises matching the group's muscles, user's equipment, and experience level.
2. Remove any excluded exercises; boost favorited exercises.
3. Select the first exercise as a compound movement (`mechanic = "compound"`). If none available, use any.
4. Select remaining exercises prioritizing those not recently performed, preferring a mix of compound and isolation.
5. If fewer exercises match filters than allocated, relax constraints (e.g., allow different experience levels).

### 4.4 Swap & Regenerate

- Tap to swap an individual exercise (replaces with the next-best candidate from the same group)
- Regenerate all exercises for a single muscle group
- Regenerate the entire workout
- Lock an exercise or group to prevent it from changing during regeneration

## 5. Data Model

### 5.1 Exercise (from dataset, read-only)

| Field | Type | Notes |
|---|---|---|
| id | String | Unique identifier (e.g., "Barbell_Curl") |
| name | String | Display name |
| primaryMuscles | List\<String\> | Maps to muscle group |
| secondaryMuscles | List\<String\> | Used for tie-breaking |
| force | String? | "push", "pull", "static" |
| level | String | "beginner", "intermediate", "expert" |
| mechanic | String? | "compound" or "isolation" |
| equipment | String? | Required equipment |
| category | String | "strength", "stretching", "plyometrics", etc. |
| instructions | List\<String\> | Step-by-step instructions |
| images | List\<String\> | Image paths for demonstration |

### 5.2 WorkoutSession (user-created)

| Field | Type | Notes |
|---|---|---|
| sessionId | Long (auto) | Primary key |
| date | Date | When the workout occurred |
| muscleGroups | List\<String\> | The muscle groups targeted in this session |
| completed | Boolean | Whether session was finished |

### 5.3 ExerciseLog (per exercise in a session)

| Field | Type | Notes |
|---|---|---|
| logId | Long (auto) | Primary key |
| sessionId | Long (FK) | References WorkoutSession |
| exerciseId | String | References Exercise.id from dataset |
| muscleGroup | String | Which of the 7 groups this counted toward |
| sets | Int | Number of sets performed |
| reps | Int | Reps per set (simplified; could expand to per-set logging) |
| weight | Double | Weight used (lbs or kg based on user preference) |

### 5.4 PersonalRecord (per exercise)

| Field | Type | Notes |
|---|---|---|
| recordId | Long (auto) | Primary key |
| exerciseId | String | References Exercise.id from dataset |
| maxWeight | Double | Heaviest weight used for this exercise |
| maxWeightDate | Date | When the weight record was set |
| maxReps | Int | Most reps completed in a single set |
| maxRepsDate | Date | When the reps record was set |
| lastWeight | Double | Weight used in most recent session (for display during workout) |
| lastReps | Int | Reps in most recent session |
| lastPerformed | Date | Date of most recent session with this exercise |

### 5.5 UserPreferences

| Field | Type | Notes |
|---|---|---|
| experienceLevel | String | "beginner", "intermediate", "expert" |
| availableEquipment | Set\<String\> | Equipment the user has access to |
| excludedExercises | Set\<String\> | Exercise IDs the user wants to skip |
| favoritedExercises | Set\<String\> | Exercise IDs to prioritize in selection |
| weightUnit | String | "lbs" or "kg" |
| targetWorkoutSize | Int | Preferred number of exercises per workout (default: 8, range: 5-12) |
| restCompound | Int | Rest duration in seconds between sets for compound exercises (default: 60) |
| restIsolation | Int | Rest duration in seconds between sets for isolation exercises (default: 45) |
| restBodyweightAb | Int | Rest duration in seconds between sets for bodyweight abdominal exercises (default: 30) |

### 5.6 RecoveryConfig (per muscle group, app-defined defaults)

| Field | Type | Notes |
|---|---|---|
| muscleGroup | String | One of the 7 muscle groups (primary key) |
| minRecoveryHours | Int | Minimum hours before the group is eligible again |

Default values:

| Muscle Group | Default Min Recovery (hours) |
|---|---|
| Chest | 48 |
| Back | 60 |
| Shoulders | 48 |
| Arms | 36 |
| Legs (Push) | 60 |
| Legs (Pull) | 60 |
| Core | 24 |

These defaults are stored in the database so they could be made user-adjustable in a future settings screen if desired.

## 6. Tech Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM with Repository pattern |
| **Local Database** | Room (SQLite) |
| **Dependency Injection** | Hilt |
| **Navigation** | Jetpack Navigation Compose |
| **Exercise Data** | free-exercise-db JSON bundled as asset |
| **Image Loading** | Coil (for exercise demonstration images) |
| **Min SDK** | API 26 (Android 8.0) |

The app is fully offline-capable. The exercise dataset is bundled at build time, and all user data is stored locally in Room. No backend or network connectivity is required.

## 7. Screens

### 7.1 Home / Today's Workout

- Shows the generated workout grouped by muscle group, with each section containing its allocated exercises
- Each exercise card shows name, equipment needed, and primary muscles
- Muscle group headers display the group's score and recovery status
- Tap a card to expand and see instructions + demonstration images
- Swap button on each exercise to replace it
- "Start Workout" button begins the logging flow

### 7.2 Active Workout

- Guided flow through each exercise in the workout
- For each exercise: input sets, reps, and weight
- Display previous performance for the exercise (last weight/reps used and personal record)
- Automatic rest timer starts between sets with duration based on exercise type:
  - **Compound exercises** (`mechanic = "compound"`) — 60 seconds
  - **Isolation exercises** (`mechanic = "isolation"`) — 45 seconds
  - **Bodyweight abdominal exercises** (`primaryMuscles` includes "abdominals" AND `equipment` is null/bodyweight) — 30 seconds
- Rest timer displays a countdown with an option to skip or extend (+15 seconds)
- Audio/vibration alert when rest period ends
- Skip button to move past an exercise without logging it
- Summary screen at completion with total volume

### 7.3 History

- Calendar or list view of past workout sessions
- Tap a session to see exercises performed with logged sets/reps/weight
- Edit or delete individual exercise logs within a past session
- Visual indicator of which muscle groups were trained and when

### 7.4 Muscle Group Overview

- Dashboard showing all 7 muscle groups with days/hours since last trained
- Color-coded status indicator combining freshness and recovery state:
  - **Recovering** (blue/grey) — within the minimum recovery window, not yet eligible
  - **Ready** (green) — recovered and available for training
  - **Due** (yellow) — recovered and hasn't been trained in a while
  - **Overdue** (red) — significantly past the ideal training window
- Recovery progress bar showing percentage of recovery time elapsed for each group
- Tap a group to see all available exercises for that group

### 7.5 Settings

- Equipment selection (multi-select checklist)
- Experience level selector
- Weight unit preference (lbs / kg)
- Target workout size slider (5-12 exercises, default 8)
- Manage excluded and favorited exercises

## 8. Development Phases

### Phase 1 — MVP

- Bundle exercise dataset and parse into Room database
- Implement muscle group mapping, recovery config, and scoring algorithm
- Build workout generation screen with dynamic sizing and swap functionality
- Basic workout logging (sets, reps, weight per exercise)
- Automatic rest timer between sets with exercise-type-based durations
- Simple history list view
- Settings screen for equipment, experience level, and target workout size

### Phase 2 — Polish

- Muscle group overview dashboard with freshness indicators
- Exercise detail view with images and full instructions
- Favorites and excluded exercises management
- Workout summary with total volume calculation
- Progressive overload display during active workouts (last weight/reps and PR)
- Edit and delete past workout logs

### Phase 3 — Enhancements

- Personal records dashboard per exercise
- Calendar view for history
- Export workout data (CSV or JSON)
- Optional notifications / workout reminders
- Theming and dark mode support

## 9. Accessibility Considerations

Even as a personal-use app, building with accessibility in mind establishes good habits and future-proofs the codebase. Key considerations:

- Provide `contentDescription` on all exercise images and icons for TalkBack compatibility
- Ensure sufficient color contrast ratios (minimum 4.5:1) on the freshness indicators and all text elements
- Don't rely solely on color to convey information (e.g., the red/yellow/green freshness indicators should also include text labels like "3 days ago")
- Use semantic Compose modifiers (e.g., `Modifier.semantics`) for screen reader support on interactive elements
- Ensure all tap targets meet the minimum 48dp size guideline
- Support dynamic font sizing (sp units) so text scales with system settings

## 10. Open Questions & Considerations

- **Dataset updates:** Should the app check for new exercises from the GitHub repo, or is a bundled-at-build approach sufficient?
- **Per-set logging:** The current model logs a single sets/reps/weight per exercise. This could expand to per-set granularity (e.g., set 1: 10×135, set 2: 8×155) if needed.
- **Stretching and cardio:** The dataset includes stretching and plyometric exercises. These could be incorporated into warmups/cooldowns, or excluded from recommendations.
- **Image hosting:** Exercise images can be loaded from GitHub raw URLs, but this requires network access. Consider bundling a subset or using placeholders for offline mode.
