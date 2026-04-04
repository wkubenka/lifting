# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.astute.body.domain.scoring.MuscleGroupScorerTest"

# Run a single test method (supports wildcards)
./gradlew test --tests "com.astute.body.domain.scoring.MuscleGroupScorerTest.recoveryScore*"

# Lint
./gradlew lint

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Architecture

MVVM + Repository pattern with Hilt DI, Jetpack Compose UI, Room database.

**Critical rules:**
- ViewModels must inject `IWorkoutRepository`, never DAOs directly.
- Use `AppClock.now()` instead of `System.currentTimeMillis()` — inject `AppClock` so tests can swap in `FakeClock`.

**Data flow:** UI → ViewModel → `WorkoutGenerator` (stateless) → `IWorkoutRepository` → DAOs → Room

**DI modules:**
- `DatabaseModule` — `AppDatabase` singleton + DAO providers
- `DomainModule` — `MuscleGroupScorer`, `AppClock` (binds `SystemClock`)
- `RepositoryModule` — binds `IWorkoutRepository` → `WorkoutRepository`

## Domain Logic

**`MuscleGroupScorer`** — 3-factor weighted score (must sum to 1.0):
- Recovery status: 0.40 (time since last training ÷ recovery window, capped at 1.5)
- Staleness: 0.35 (logarithmic recency penalty)
- Frequency balance: 0.25 (inverse of sessions in last 14 days)
- Overlap penalty: −30% for muscle overlap between selected groups
- Favorited bonus: +5% for groups containing favorited exercises

**`WorkoutGenerator`** — selects 2–4 muscle groups, allocates 3 exercises per group. Hard constraints: never both `LEGS_PUSH` + `LEGS_PULL`. Exercise selection order: favorited+recent > favorited > recent > other. Within each tier, compound exercises (`mechanic="compound"`) get 2× probability vs isolation.

**`MuscleGroup`** — 7 enum values. Each has a dataset muscle list used for matching exercises from the DB.

## Test Infrastructure

Unit tests live in `app/src/test/`. Key test doubles:

- **`FakeWorkoutRepository`** (`test/domain/generator/`) — full `IWorkoutRepository` implementation with mutable fields (`lastTrainedMillis`, `sessionCounts`, `recentExerciseIds`, `exercises`, `preferences`, `recoveryConfigs`) and a `makeExercise()` factory.
- **`FakeClock`** (public class in `test/ui/home/HomeViewModelTest.kt`) — mutable `currentTimeMillis` used across test files.

Standard test setup pattern:
```kotlin
@Before fun setup() {
    Dispatchers.setMain(StandardTestDispatcher())
}
@After fun teardown() { Dispatchers.resetMain() }
@Test fun `name`() = runTest { advanceUntilIdle() }
```

## Database

Room v6. 7 entities: `ExerciseEntity` (800+ seeded from bundled JSON), `WorkoutSessionEntity`, `ExerciseLogEntity`, `PersonalRecordEntity`, `UserPreferencesEntity`, `RecoveryConfigEntity`, `ActiveWorkoutEntity`. Migrations 1→6 are defined in `AppDatabase.kt`.

## Release

Tagged releases trigger `.github/workflows/release.yml`, which builds a signed APK. Requires secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
