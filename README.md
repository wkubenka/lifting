# Astute Body

A personal Android fitness app that generates smart, personalized workouts based on training history, muscle recovery, and user preferences.

## How It Works

The app scores 7 muscle groups (Shoulders, Arms, Chest, Back, Core, Legs Push, Legs Pull) using a weighted base score:

| Factor | Weight | Description |
|---|---|---|
| Recovery Status | 40% | Time since last training relative to recovery window (capped at 1.5×) |
| Staleness | 35% | Logarithmic recency penalty for muscle groups not trained recently |
| Frequency Balance | 25% | Inverse of sessions in the last 14 days, keeps training distributed |

A separate **overlap penalty** (−30% × shared-muscle ratio) is applied to candidates that share dataset muscles with already-selected groups, plus a **+5% favorited bonus** for groups that contain favorited exercises.

Top-scoring groups are selected and exercises are allocated proportionally (compound lifts get 2× selection probability vs isolation). Hard constraint: never both `LEGS_PUSH` and `LEGS_PULL` in the same workout.

## Features

- **Workout generation** — scored muscle group selection with per-exercise swap and per-group regenerate
- **Exercise logging** — sets, reps, and weight per exercise with last-performed values shown
- **Rest timer** — automatic countdown between sets (compound 60s / isolation 45s / bodyweight 30s)
- **History** — browse past sessions with full exercise detail
- **Settings** — configure target workout size, rest durations, weight unit; manage favorites/exclusions
- **Offline-first** — curated library of ~70 dumbbell and bodyweight exercises bundled in the APK

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite, currently v8), Hilt, kotlinx.coroutines, kotlinx.serialization
- MVVM + Repository pattern (`IWorkoutRepository` is the single data abstraction)
- `AppClock` interface for testable time (inject instead of `System.currentTimeMillis()`)
- Min SDK 26, Target SDK 36

## Project Structure

```
com.astute.body/
├── data/
│   ├── local/          # Room database, DAOs, entities, seeder, migrations
│   ├── repository/     # IWorkoutRepository + WorkoutRepository implementation
│   └── model/          # JSON deserialization models
├── domain/
│   ├── model/          # MuscleGroup enum, WorkoutPlan
│   ├── scoring/        # MuscleGroupScorer (weighted scoring algorithm)
│   ├── generator/      # WorkoutGenerator (selection + allocation)
│   └── AppClock.kt     # Injectable clock for testable time
├── di/                 # Hilt modules (DatabaseModule, DomainModule, RepositoryModule)
└── ui/
    ├── navigation/     # Compose NavHost
    ├── home/           # Planning + active workout + summary (single screen, multiple states)
    ├── exercise/       # Exercise detail + Manage exercises (favorite/exclude)
    ├── history/        # Past sessions list + detail
    ├── muscles/        # Per-muscle-group recovery overview
    ├── settings/       # User preferences + recovery config
    └── theme/          # Material 3 theming
```

## Building & Testing

```bash
# Debug build
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.astute.body.domain.scoring.MuscleGroupScorerTest"

# Lint
./gradlew lint

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Releasing

Tag pushes (`v*`) trigger `.github/workflows/release.yml`, which builds a signed release APK and creates a GitHub release. Required secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Tag format: semantic version, e.g. `v0.11.1`.

## Documentation

- [docs/superpowers/specs/](docs/superpowers/specs/) — design specs for major changes
- [docs/superpowers/plans/](docs/superpowers/plans/) — implementation plans for major changes
