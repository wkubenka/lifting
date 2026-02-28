# Astute Body

A personal Android fitness app that generates smart, personalized workouts based on training history, muscle recovery, and user preferences.

## How It Works

The app scores 7 muscle groups (Chest, Back, Shoulders, Arms, Legs Push, Legs Pull, Core) using a weighted algorithm:

| Factor | Weight | Description |
|---|---|---|
| Recovery Status | 35% | Time since last training relative to recovery window |
| Staleness | 30% | Penalizes muscle groups not trained recently |
| Frequency Balance | 25% | Keeps training evenly distributed |
| Overlap Penalty | 10% | Avoids redundant muscle activation |

Top-scoring groups are selected, exercises are allocated proportionally (compound first, then isolation), and filtered by your available equipment and experience level.

## Features

- **Workout generation** — scored muscle group selection with swap and regenerate
- **Exercise logging** — sets, reps, and weight per exercise with last-performed values shown
- **Rest timer** — automatic countdown between sets (compound 60s / isolation 45s / bodyweight 30s)
- **History** — browse past sessions with full exercise detail
- **Settings** — configure equipment, experience level, target workout size, rest durations
- **Offline-first** — 800+ exercises bundled from [free-exercise-db](https://github.com/yuhonas/free-exercise-db)

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite), Hilt, Coroutines
- MVVM + Repository pattern
- Min SDK 26, Target SDK 36

## Project Structure

```
com.astutebody.app/
├── data/
│   ├── local/          # Room database, DAOs, entities, seeder
│   ├── repository/     # Repository interface + implementation
│   └── model/          # JSON deserialization models
├── domain/
│   ├── model/          # MuscleGroup, WorkoutPlan
│   ├── scoring/        # Muscle group scoring algorithm
│   └── generator/      # Workout generation logic
├── di/                 # Hilt modules
└── ui/
    ├── navigation/     # Compose NavHost
    ├── home/           # Workout generation screen
    ├── workout/        # Active workout logging screen
    ├── history/        # Past sessions list + detail
    ├── settings/       # User preferences
    └── theme/          # Material 3 theming
```

## Building

```
./gradlew assembleDebug
```

## Documentation

- [Project Overview](docs/Astute_Body_Project_Overview.md) — full specification, data models, and scoring algorithm
- [Phase 1 Implementation Plan](docs/Phase_1_Implementation_Plan.md) — step-by-step build plan with tasks and tests
