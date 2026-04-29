# Dumbbell Exercise Library Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 800+ exercise library with a curated set of 73 dumbbell-only exercises sourced from Endomondo, drop image rendering and assets, wipe pre-existing user data via DB migration, and remove the now-unused coil dependency.

**Architecture:** Single atomic schema-change refactor (Task 1) drops the `images` field from `ExerciseEntity`/`ExerciseJson` and bumps Room DB to v7 with a migration that wipes per-exercise data and rebuilds the `exercises` table. Subsequent tasks replace the JSON dataset, delete the bundled image assets, and remove the coil dependency.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.x, Hilt, kotlinx.serialization, Gradle Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-04-28-dumbbell-exercise-overhaul-design.md`

---

## Task ordering note

Task 1 is intentionally large because the schema change must be atomic — splitting it would leave the build in a broken state mid-way. All sub-edits in Task 1 must land before running tests/build. Tasks 2-5 are smaller and independent.

---

## Task 1: Drop `images` field, reorder MuscleGroup enum, bump Room DB to v7, strip image rendering

**Files:**
- Modify: `app/src/main/java/com/astute/body/domain/model/MuscleGroup.kt`
- Modify: `app/src/main/java/com/astute/body/data/local/entity/ExerciseEntity.kt`
- Modify: `app/src/main/java/com/astute/body/data/model/ExerciseJson.kt`
- Modify: `app/src/main/java/com/astute/body/data/local/DatabaseSeeder.kt:41-54`
- Modify: `app/src/main/java/com/astute/body/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/astute/body/di/DatabaseModule.kt:6-10,37`
- Modify: `app/src/main/java/com/astute/body/ui/exercise/ExerciseDetailScreen.kt`
- Modify: `app/src/test/java/com/astute/body/domain/generator/FakeWorkoutRepository.kt:140`
- Modify: `app/src/test/java/com/astute/body/ui/exercise/ManageExercisesViewModelTest.kt:226`
- Modify: `app/src/test/java/com/astute/body/ui/home/HomeViewModelTest.kt:459`
- Modify: `app/src/test/java/com/astute/body/ui/exercise/ExerciseDetailViewModelTest.kt:151`

- [ ] **Step 0: Reorder `MuscleGroup` enum entries (ARMS before CHEST)**

In `app/src/main/java/com/astute/body/domain/model/MuscleGroup.kt`, swap the order of the `CHEST` and `ARMS` enum entries so `ARMS` comes first. The full enum body becomes:

```kotlin
    SHOULDERS("Shoulders", setOf("shoulders", "neck"), Classification.MAJOR_PUSH),
    ARMS("Arms", setOf("biceps", "triceps", "forearms"), Classification.MINOR),
    CHEST("Chest", setOf("chest", "triceps"), Classification.MAJOR_PUSH),
    BACK("Back", setOf("lats", "middle back", "lower back", "traps"), Classification.MAJOR_PULL),
    CORE("Core", setOf("abdominals"), Classification.MAJOR),
    LEGS_PUSH("Legs (Push)", setOf("quadriceps", "glutes", "abductors", "calves"), Classification.MAJOR_PUSH),
    LEGS_PULL("Legs (Pull)", setOf("hamstrings", "adductors", "glutes"), Classification.MAJOR_PULL);
```

The `datasetMuscles` set contents are unchanged — only the declaration order of `ARMS` and `CHEST` swaps. This makes `WorkoutGenerator.inferMuscleGroup` (which returns the first matching group in iteration order) bucket exercises with `primaryMuscles: ["triceps"]` into `ARMS` instead of `CHEST`.

- [ ] **Step 1: Drop `images` from `ExerciseEntity`**

Replace the entire contents of `app/src/main/java/com/astute/body/data/local/entity/ExerciseEntity.kt` with:

```kotlin
package com.astute.body.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val force: String?,
    val level: String,
    val mechanic: String?,
    val equipment: String?,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val volumeMultiplier: Int = 1
)
```

- [ ] **Step 2: Drop `images` from `ExerciseJson`**

Replace the entire contents of `app/src/main/java/com/astute/body/data/model/ExerciseJson.kt` with:

```kotlin
package com.astute.body.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseJson(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String,
    val mechanic: String? = null,
    val equipment: String? = null,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val volumeMultiplier: Int = 1
)
```

- [ ] **Step 3: Drop `images` line from seeder mapper**

In `app/src/main/java/com/astute/body/data/local/DatabaseSeeder.kt`, replace the `toEntity` function (currently lines 41-54) with:

```kotlin
    private fun ExerciseJson.toEntity() = ExerciseEntity(
        id = id,
        name = name,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        category = category,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        instructions = instructions,
        volumeMultiplier = volumeMultiplier
    )
```

(Only the `images = images,` line is removed; everything else stays.)

- [ ] **Step 4: Bump `AppDatabase` to v7 and add `MIGRATION_6_7`**

In `app/src/main/java/com/astute/body/data/local/AppDatabase.kt`:

(a) Change the `@Database(... version = 6 ...)` annotation to `version = 7`.

(b) Inside the `companion object`, after `MIGRATION_5_6`, add:

```kotlin
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Wipe per-exercise data so the new dumbbell-only library starts clean.
                db.execSQL("DELETE FROM workout_sessions")
                db.execSQL("DELETE FROM exercise_logs")
                db.execSQL("DELETE FROM personal_records")
                db.execSQL("DELETE FROM active_workout")
                db.execSQL("DELETE FROM exercises")

                // Rebuild exercises table without the `images` column.
                db.execSQL("""
                    CREATE TABLE exercises_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        force TEXT,
                        level TEXT NOT NULL,
                        mechanic TEXT,
                        equipment TEXT,
                        category TEXT NOT NULL,
                        primaryMuscles TEXT NOT NULL,
                        secondaryMuscles TEXT NOT NULL,
                        instructions TEXT NOT NULL,
                        volumeMultiplier INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("DROP TABLE exercises")
                db.execSQL("ALTER TABLE exercises_new RENAME TO exercises")
            }
        }
```

- [ ] **Step 5: Register `MIGRATION_6_7` in `DatabaseModule`**

In `app/src/main/java/com/astute/body/di/DatabaseModule.kt`:

(a) After the existing `import com.astute.body.data.local.AppDatabase.Companion.MIGRATION_5_6` (line 10), add:

```kotlin
import com.astute.body.data.local.AppDatabase.Companion.MIGRATION_6_7
```

(b) On the `addMigrations(...)` line (currently line 37), append `, MIGRATION_6_7`:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

- [ ] **Step 6: Strip image rendering from `ExerciseDetailScreen`**

Replace the entire contents of `app/src/main/java/com/astute/body/ui/exercise/ExerciseDetailScreen.kt` with:

```kotlin
package com.astute.body.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astute.body.data.local.entity.ExerciseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.exercise?.name ?: "",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (uiState.isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (uiState.isFavorited) "Remove from favorites" else "Add to favorites",
                            tint = if (uiState.isFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.toggleExclude() }) {
                        Icon(
                            if (uiState.isExcluded) Icons.Filled.Block else Icons.Outlined.Block,
                            contentDescription = if (uiState.isExcluded) "Remove from excluded" else "Exclude exercise",
                            tint = if (uiState.isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.exercise != null -> {
                ExerciseDetailContent(
                    exercise = uiState.exercise!!,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetailContent(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (exercise.instructions.isNotEmpty()) {
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            exercise.instructions.forEachIndexed { index, instruction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Text(
                text = "No instructions available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            exercise.mechanic?.let { mechanic ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(mechanic.replaceFirstChar { it.uppercase() }) }
                )
            }
            exercise.force?.let { force ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(force.replaceFirstChar { it.uppercase() }) }
                )
            }
            SuggestionChip(
                onClick = {},
                label = { Text(exercise.level.replaceFirstChar { it.uppercase() }) }
            )
        }

        Text(
            text = "Equipment",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = exercise.equipment?.replaceFirstChar { it.uppercase() } ?: "Body Only",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Primary Muscles",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            exercise.primaryMuscles.forEach { muscle ->
                AssistChip(
                    onClick = {},
                    label = { Text(muscle.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        if (exercise.secondaryMuscles.isNotEmpty()) {
            Text(
                text = "Secondary Muscles",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                exercise.secondaryMuscles.forEach { muscle ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(muscle.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
```

(All `coil3`, `HorizontalPager`, `rememberPagerState`, `aspectRatio`, `clip`, `CircleShape`, `ContentScale`, `FitnessCenter`, `background`, `size` imports are removed because their consumers were the deleted `ExerciseImagePager` and `ImagePlaceholder` composables.)

- [ ] **Step 7: Drop `images` arg from `FakeWorkoutRepository`**

In `app/src/test/java/com/astute/body/domain/generator/FakeWorkoutRepository.kt`, find the `ExerciseEntity(...)` constructor call near line 129 and remove the `images = emptyList()` line (currently line 140). Also remove the trailing comma from the line above it if it leaves a syntax error.

The block before:
```kotlin
        ) = ExerciseEntity(
            id = id,
            // ... other fields ...
            instructions = emptyList(),
            images = emptyList()
        )
```

The block after:
```kotlin
        ) = ExerciseEntity(
            id = id,
            // ... other fields ...
            instructions = emptyList()
        )
```

- [ ] **Step 8: Drop `images` arg from `ManageExercisesViewModelTest`**

In `app/src/test/java/com/astute/body/ui/exercise/ManageExercisesViewModelTest.kt`, find the `ExerciseEntity(...)` constructor call near line 215 and remove the `images = emptyList()` line (currently line 226). Adjust the comma on the prior line as needed.

- [ ] **Step 9: Drop `images` arg from `HomeViewModelTest`**

In `app/src/test/java/com/astute/body/ui/home/HomeViewModelTest.kt`, find the `makeTestExercise` helper near line 448 and remove the `images = emptyList()` line (currently line 459). Adjust the comma on the prior line as needed.

- [ ] **Step 10: Drop `images` arg from `ExerciseDetailViewModelTest`**

In `app/src/test/java/com/astute/body/ui/exercise/ExerciseDetailViewModelTest.kt`, find the `ExerciseEntity(...)` constructor call near line 140 and remove the `images = listOf("bench_press/0.jpg")` line (currently line 151). Adjust the comma on the prior line as needed. If any later assertion in this file references `.images` on the entity, remove that assertion as well — grep the file for `images` to confirm.

- [ ] **Step 11: Run the unit test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass. The `MuscleGroupScorerTest.overlapPenalty - chest selected penalizes arms for shared triceps` test continues to pass — only enum declaration order changed; `CHEST.datasetMuscles` and `ARMS.datasetMuscles` still both contain `"triceps"`, so the overlap math is unchanged. If a test fails complaining about `images`, return to steps 7-10 and verify all four were updated.

- [ ] **Step 12: Run a debug build to regenerate the Room schema export**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. A new file `app/schemas/com.astute.body.data.local.AppDatabase/7.json` should appear, reflecting the entity schema without the `images` column. If Room reports a schema mismatch, the entity and the migration's `CREATE TABLE` are out of sync — re-check Step 1 vs Step 4.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/astute/body/domain/model/MuscleGroup.kt \
        app/src/main/java/com/astute/body/data/local/entity/ExerciseEntity.kt \
        app/src/main/java/com/astute/body/data/model/ExerciseJson.kt \
        app/src/main/java/com/astute/body/data/local/DatabaseSeeder.kt \
        app/src/main/java/com/astute/body/data/local/AppDatabase.kt \
        app/src/main/java/com/astute/body/di/DatabaseModule.kt \
        app/src/main/java/com/astute/body/ui/exercise/ExerciseDetailScreen.kt \
        app/src/test/java/com/astute/body/domain/generator/FakeWorkoutRepository.kt \
        app/src/test/java/com/astute/body/ui/exercise/ManageExercisesViewModelTest.kt \
        app/src/test/java/com/astute/body/ui/home/HomeViewModelTest.kt \
        app/src/test/java/com/astute/body/ui/exercise/ExerciseDetailViewModelTest.kt \
        app/schemas/com.astute.body.data.local.AppDatabase/7.json
git commit -m "Drop images, reorder MuscleGroup so triceps bucket as ARMS, bump Room DB to v7"
```

---

## Task 2: Replace `exercises.json` with the new 73-exercise dumbbell library

**Files:**
- Modify (full rewrite): `app/src/main/assets/exercises.json`

**Note on count:** The Endomondo source listed 74 numbered entries, but #38 (Dumbbell Shrug under Shoulders) and #53 (Dumbbell Shrug under Back) are the same exercise. The new library has 73 distinct entries. The shrug is bucketed into `BACK` via `primaryMuscles: ["traps"]`.

- [ ] **Step 1: Replace `app/src/main/assets/exercises.json` entirely**

Overwrite `app/src/main/assets/exercises.json` with this exact content:

```json
[
  {
    "id": "Dumbbell_Bench_Press",
    "name": "Dumbbell Bench Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["chest"],
    "secondaryMuscles": ["shoulders", "triceps"],
    "instructions": ["Lie on a flat bench holding a pair of dumbbells on your thighs with neutral grip, kick them up to shoulder level, then press to lockout and lower under control to build chest, shoulders, and triceps."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Bicep_Curl",
    "name": "Dumbbell Bicep Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": [],
    "instructions": ["Stand with feet hip-width apart and dumbbells at your sides with palms forward, then curl both dumbbells up by flexing the elbows while keeping the upper arms still."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Hammer_Curl",
    "name": "Dumbbell Hammer Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": ["forearms"],
    "instructions": ["Stand with arms at your sides holding dumbbells in a neutral grip (palms facing your body), then curl them up to your shoulders while keeping the wrists fixed."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Reverse_Curl",
    "name": "Dumbbell Reverse Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["forearms"],
    "secondaryMuscles": ["biceps"],
    "instructions": ["Stand with dumbbells at your thighs in a pronated grip (knuckles forward), then curl up by flexing the elbows while keeping the wrists straight to load the forearms."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Skull_Crusher",
    "name": "Dumbbell Skull Crusher",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Lie on a bench holding dumbbells extended over your chest with palms facing each other, then bend the elbows to lower the weights toward your forehead and extend back to lockout."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Overhead_Tricep_Extension",
    "name": "Dumbbell Overhead Tricep Extension",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Hold a single dumbbell vertically overhead with both hands, then lower it behind your head by bending the elbows and press back to full extension."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Standing_Overhead_Tricep_Extension",
    "name": "Dumbbell Standing Overhead Tricep Extension",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Stand tall holding a dumbbell overhead with both hands, lower it behind your head by bending only the elbows, and extend back to lockout."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Single_Arm_Tricep_Extension",
    "name": "Dumbbell Single-Arm Tricep Extension",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Sit or stand holding one dumbbell overhead in a single hand, lower it behind your head with controlled elbow flexion, then extend back up. Repeat on the other side."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Standing_Single_Arm_Tricep_Kickback",
    "name": "Dumbbell Standing Single-Arm Tricep Kickback",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Adopt a split stance and hinge forward, holding one dumbbell with the upper arm parallel to the floor, then extend the elbow to kick the dumbbell straight back. Switch sides."],
    "volumeMultiplier": 2
  },
  {
    "id": "Tricep_Dumbbell_Kickback",
    "name": "Tricep Dumbbell Kickback",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Hinge over a bench with one knee and hand on the bench for support, holding a dumbbell with the upper arm parallel to the floor, then extend the elbow to kick the weight back."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Preacher_Curl",
    "name": "Dumbbell Preacher Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": [],
    "instructions": ["Sit at a preacher bench with the back of your upper arm on the pad and a dumbbell in each hand with palms up, then curl the dumbbells up and lower under control."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_One_Arm_Zottman_Preacher_Curl",
    "name": "Dumbbell One-Arm Zottman Preacher Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": ["forearms"],
    "instructions": ["At a preacher bench, curl one dumbbell up with palm facing you, then rotate the wrist to a pronated grip and lower under control. Repeat on the other arm."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Zottman_Curl",
    "name": "Dumbbell Zottman Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": ["forearms"],
    "instructions": ["Stand with dumbbells at your sides palms forward, curl up to the top, rotate the wrists to a pronated grip, then lower slowly to load both biceps and forearms."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Zottman_Preacher_Curl",
    "name": "Dumbbell Zottman Preacher Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": ["forearms"],
    "instructions": ["At a preacher bench, curl dumbbells up with palms facing you, rotate wrists to pronated at the top, then lower slowly to combine the preacher curl with the Zottman finish."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Cross_Body_Hammer_Curl",
    "name": "Dumbbell Cross-Body Hammer Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": ["forearms"],
    "instructions": ["Stand holding dumbbells in a neutral grip, then curl one dumbbell across your body toward the opposite shoulder. Lower and alternate sides."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Concentration_Curl",
    "name": "Dumbbell Concentration Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": [],
    "instructions": ["Sit on a bench with feet wide, brace one elbow against the inside of the same-side thigh, and curl the dumbbell up with full focus on the biceps. Switch arms."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Back_Wrist_Curl",
    "name": "Dumbbell Back Wrist Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["forearms"],
    "secondaryMuscles": [],
    "instructions": ["Stand with dumbbells held behind your back with palms facing away, then curl the wrists upward through their full range to load the forearm extensors."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Finger_Curl",
    "name": "Dumbbell Finger Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["forearms"],
    "secondaryMuscles": [],
    "instructions": ["Sit with forearms supported on your thighs and palms up, let dumbbells roll down to your fingertips, then curl your fingers to roll them back up into your palms."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Wrist_Twist",
    "name": "Dumbbell Wrist Twist",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["forearms"],
    "secondaryMuscles": [],
    "instructions": ["Hold dumbbells at elbow height with elbows tucked, then rotate the wrists from palms-up to palms-down through a controlled range to work forearm rotation."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Hammer_Wrist_Curl",
    "name": "Dumbbell Hammer Wrist Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["forearms"],
    "secondaryMuscles": [],
    "instructions": ["Sit with forearms on a bench in a neutral grip (palms facing each other), then flex the wrists upward and lower under control to work the forearm flexors and extensors."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Incline_Bicep_Curl",
    "name": "Dumbbell Incline Bicep Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": [],
    "instructions": ["Lie back on an incline bench with dumbbells hanging at your sides palms forward, then curl them up while keeping the upper arms pinned to the bench."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Alternating_Bicep_Curl",
    "name": "Dumbbell Alternating Bicep Curl",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["biceps"],
    "secondaryMuscles": [],
    "instructions": ["Stand with dumbbells at your sides palms forward, then curl one arm at a time up to your shoulder, lower under control, and alternate."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Tate_Press",
    "name": "Dumbbell Tate Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": ["chest"],
    "instructions": ["Lie on a bench with dumbbells held vertically over your chest with palms forward, then bend the elbows out to lower the dumbbells in toward your chest, and press straight up."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Close_Grip_Press",
    "name": "Dumbbell Close-Grip Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": ["chest", "shoulders"],
    "instructions": ["Lie on a bench holding dumbbells touching each other over your chest with palms facing in, then press up and lower with the dumbbells kept close together to emphasize triceps."],
    "volumeMultiplier": 2
  },
  {
    "id": "Decline_Dumbbell_Tricep_Extension",
    "name": "Decline Dumbbell Tricep Extension",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["triceps"],
    "secondaryMuscles": [],
    "instructions": ["Lie on a decline bench holding dumbbells extended above your chest with palms facing in, then bend the elbows to lower the dumbbells toward your forehead and press back to lockout."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Shoulder_Press",
    "name": "Dumbbell Shoulder Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Sit on a bench holding dumbbells at shoulder height with palms forward, then press them overhead to lockout and lower with control."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Seated_Shoulder_Press",
    "name": "Dumbbell Seated Shoulder Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Sit on a bench with back support, hold dumbbells at shoulder height with palms forward, and press them overhead to full lockout. Lower under control."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Standing_Shoulder_Press",
    "name": "Dumbbell Standing Shoulder Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Stand with feet shoulder-width apart, hold dumbbells at shoulder height with palms forward, brace your core, and press the dumbbells overhead to lockout."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Arnold_Press",
    "name": "Dumbbell Arnold Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Hold dumbbells in front of your shoulders with palms facing you, then rotate the wrists outward as you press overhead. Reverse the motion on the way down."],
    "volumeMultiplier": 2
  },
  {
    "id": "Alternating_Dumbbell_Press",
    "name": "Alternating Dumbbell Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Sit holding dumbbells at shoulder height, then press one arm overhead to lockout while the other stays at the shoulder. Lower and alternate sides each rep."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Single_Arm_Overhead_Press",
    "name": "Dumbbell Single-Arm Overhead Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Stand or sit holding one dumbbell at shoulder height with palm forward, brace the core, and press it overhead to lockout. Repeat on the other side."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Front_Raise",
    "name": "Dumbbell Front Raise",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Stand holding dumbbells in front of your thighs palms down, then raise them straight forward to shoulder height with arms nearly straight, and lower under control."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Lateral_Raise",
    "name": "Dumbbell Lateral Raise",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Stand with dumbbells at your sides and a slight bend in the elbows, then raise the dumbbells out to the sides until your arms are parallel to the floor."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Seated_Lateral_Raise",
    "name": "Dumbbell Seated Lateral Raise",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Sit on a flat bench with feet planted firmly and dumbbells hanging at your sides, then raise both dumbbells out to shoulder height with a slight elbow bend."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Reverse_Fly",
    "name": "Dumbbell Reverse Fly",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["middle back"],
    "instructions": ["Lie face-down on an incline bench holding dumbbells beneath you with palms facing each other, then raise the arms out to the sides to shoulder height to target the rear delts."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Bent_Over_Rear_Delt_Fly",
    "name": "Dumbbell Bent-Over Rear Delt Fly",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["middle back"],
    "instructions": ["Hinge forward at the hips with a flat back holding dumbbells beneath your shoulders, then raise the arms out to the sides until they reach shoulder level."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Upright_Row",
    "name": "Dumbbell Upright Row",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["traps", "biceps"],
    "instructions": ["Stand holding dumbbells in front of your thighs palms toward you, then pull them up along your torso to chest height by leading with the elbows."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Shrug",
    "name": "Dumbbell Shrug",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["traps"],
    "secondaryMuscles": [],
    "instructions": ["Stand with dumbbells hanging at your sides, then elevate your shoulders straight up toward your ears and lower under control. Avoid rotating the shoulders."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Bent_Over_Face_Pull",
    "name": "Dumbbell Bent-Over Face Pull",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["middle back"],
    "instructions": ["Hinge forward with dumbbells hanging beneath your shoulders, then pull them up toward your face by flaring the elbows wide and squeezing the rear delts."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Scapular_External_Rotation",
    "name": "Dumbbell Scapular External Rotation",
    "force": "pull",
    "level": "beginner",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Lie on your side with the working arm on top, elbow bent 90 degrees and tucked to your side holding a light dumbbell, then rotate the forearm upward away from your body."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Rear_Lateral_Raise_With_Head_Support",
    "name": "Dumbbell Rear Lateral Raise With Head Support",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Lie face-down on an incline bench with your forehead supported, dumbbells hanging beneath you, then raise the arms out to the sides until parallel to the floor."],
    "volumeMultiplier": 2
  },
  {
    "id": "Lying_Dumbbell_One_Arm_Rear_Lateral_Raise",
    "name": "Lying Dumbbell One-Arm Rear Lateral Raise",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Lie face-down on a flat bench with one dumbbell hanging directly beneath your shoulder, then raise that arm out to the side to shoulder height. Switch arms."],
    "volumeMultiplier": 2
  },
  {
    "id": "Side_Lying_Dumbbell_Rear_Delt_Raise",
    "name": "Side Lying Dumbbell Rear Delt Raise",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": [],
    "instructions": ["Lie on your side on a bench, top arm holding a dumbbell across your body, then raise it up and back behind your head in an arc to target the rear delt."],
    "volumeMultiplier": 2
  },
  {
    "id": "Seated_Dumbbell_Rear_Delt_Fly",
    "name": "Seated Dumbbell Rear Delt Fly",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["shoulders"],
    "secondaryMuscles": ["middle back"],
    "instructions": ["Sit on the end of a bench, hinge forward chest-to-thigh, hold dumbbells beneath you, then raise the arms out to the sides to shoulder height."],
    "volumeMultiplier": 2
  },
  {
    "id": "Incline_Dumbbell_Bench_Press",
    "name": "Incline Dumbbell Bench Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["chest"],
    "secondaryMuscles": ["shoulders", "triceps"],
    "instructions": ["Set a bench to roughly a 30-degree incline, hold dumbbells at chest level with palms forward, and press them up to lockout. Lower under control."],
    "volumeMultiplier": 2
  },
  {
    "id": "Decline_Dumbbell_Bench_Press",
    "name": "Decline Dumbbell Bench Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["chest"],
    "secondaryMuscles": ["shoulders", "triceps"],
    "instructions": ["Lie on a decline bench secured by your feet, hold dumbbells at chest level with palms forward, and press to lockout to emphasize the lower chest."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Chest_Fly",
    "name": "Dumbbell Chest Fly",
    "force": "push",
    "level": "intermediate",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["chest"],
    "secondaryMuscles": [],
    "instructions": ["Lie on a flat bench with dumbbells extended above your chest palms facing each other and a slight bend in the elbows, then open the arms wide and squeeze them back together."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Floor_Press",
    "name": "Dumbbell Floor Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["chest"],
    "secondaryMuscles": ["triceps"],
    "instructions": ["Lie on the floor with knees bent holding dumbbells at chest height with palms forward, then press up to lockout. The floor caps the range of motion at the upper arms."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Svend_Press",
    "name": "Dumbbell Svend Press",
    "force": "push",
    "level": "beginner",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["chest"],
    "secondaryMuscles": [],
    "instructions": ["Stand holding a single dumbbell pressed between your palms at chest level, then extend your arms forward while squeezing the chest, and return."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Bent_Over_Row",
    "name": "Dumbbell Bent-Over Row",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["middle back"],
    "secondaryMuscles": ["lats", "biceps"],
    "instructions": ["Hinge forward at the hips with a flat back holding dumbbells with arms straight, then row the dumbbells to your hips by driving the elbows back and squeezing the shoulder blades."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Single_Arm_Bent_Over_Row",
    "name": "Dumbbell Single-Arm Bent-Over Row",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["middle back"],
    "secondaryMuscles": ["lats", "biceps"],
    "instructions": ["Place one knee and hand on a bench for support, hold a dumbbell in the free hand with arm hanging, then row it to your hip while keeping the torso level. Switch sides."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Pullover",
    "name": "Dumbbell Pullover",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["lats"],
    "secondaryMuscles": ["chest", "triceps"],
    "instructions": ["Lie across a bench with shoulders supported and feet on the floor, hold a single dumbbell over your chest with both hands, then lower it back behind your head and pull it back up overhead."],
    "volumeMultiplier": 1
  },
  {
    "id": "Incline_Dumbbell_Row",
    "name": "Incline Dumbbell Row",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["middle back"],
    "secondaryMuscles": ["lats", "biceps"],
    "instructions": ["Lie chest-down on a 45-degree incline bench holding dumbbells with arms hanging, then row both dumbbells to your hips by driving the elbows back."],
    "volumeMultiplier": 2
  },
  {
    "id": "Renegade_Rows",
    "name": "Renegade Rows",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["middle back"],
    "secondaryMuscles": ["abdominals", "biceps"],
    "instructions": ["Set up in a high plank with hands on dumbbells beneath your shoulders, then row one dumbbell to your hip while bracing the core. Alternate sides each rep."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Sumo_Squat",
    "name": "Dumbbell Sumo Squat",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes", "adductors"],
    "instructions": ["Stand with feet wide and toes turned out, hold a dumbbell vertically between your legs with both hands, then squat down keeping the chest up and drive back to standing."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Lunge",
    "name": "Dumbbell Lunge",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes", "hamstrings"],
    "instructions": ["Stand with dumbbells at your sides, take a large step forward, and lower until both knees are bent about 90 degrees. Push back to standing and alternate legs."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Walking_Lunge",
    "name": "Dumbbell Walking Lunge",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes", "hamstrings"],
    "instructions": ["Hold dumbbells at your sides and lunge forward, then drive off the front foot and step into the next lunge with the opposite leg, walking forward continuously."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Reverse_Lunge",
    "name": "Dumbbell Reverse Lunge",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes", "hamstrings"],
    "instructions": ["Stand with dumbbells at your sides, step one foot back into a lunge, and drive off the back foot to return to standing. Alternate legs."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Side_Lunge",
    "name": "Dumbbell Side Lunge",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes", "adductors"],
    "instructions": ["Hold a dumbbell at your chest, step laterally into a wide stance, and squat down on the stepping leg while the other leg stays straight. Push back to center and alternate."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Split_Squat",
    "name": "Dumbbell Split Squat",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes"],
    "instructions": ["Stand in a staggered stance with one foot well in front of the other holding dumbbells, then lower straight down until both knees bend about 90 degrees. Drive up. Switch legs after a set."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Bulgarian_Split_Squat",
    "name": "Dumbbell Bulgarian Split Squat",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes"],
    "instructions": ["Place the top of one foot on a bench behind you, hold dumbbells at your sides, and lower into a single-leg squat on the front leg. Drive up. Switch legs after a set."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Goblet_Squat",
    "name": "Dumbbell Goblet Squat",
    "force": "push",
    "level": "beginner",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes"],
    "instructions": ["Hold a single dumbbell vertically against your chest with both hands cupping the top, then squat down keeping the chest up and rise back to standing."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Jumping_Squat",
    "name": "Dumbbell Jumping Squat",
    "force": "push",
    "level": "advanced",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes", "calves"],
    "instructions": ["Hold light dumbbells at your sides, drop into a quarter squat, then explode upward into a jump. Land softly and immediately drop into the next rep."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Deadlift",
    "name": "Dumbbell Deadlift",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["hamstrings"],
    "secondaryMuscles": ["glutes", "lower back"],
    "instructions": ["Stand with feet shoulder-width apart and dumbbells in front of your shins, hinge at the hips with a flat back to grip them, then drive through the floor to stand tall."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Romanian_Deadlift",
    "name": "Dumbbell Romanian Deadlift",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["hamstrings"],
    "secondaryMuscles": ["glutes"],
    "instructions": ["Stand holding dumbbells in front of your thighs with knees soft, then hinge at the hips and slide the dumbbells down your legs until you feel a hamstring stretch. Reverse to standing."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Single_Leg_Romanian_Deadlift",
    "name": "Dumbbell Single Leg Romanian Deadlift",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["hamstrings"],
    "secondaryMuscles": ["glutes"],
    "instructions": ["Stand on one leg holding a dumbbell in the opposite hand, hinge forward at the hip while the free leg extends behind you, then return to standing. Switch sides."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Stiff_Leg_Deadlift",
    "name": "Dumbbell Stiff Leg Deadlift",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["hamstrings"],
    "secondaryMuscles": ["glutes", "lower back"],
    "instructions": ["Stand with dumbbells in front of your thighs with legs nearly straight, then hinge at the hips and lower the dumbbells toward the floor while keeping the back flat. Reverse to standing."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Step_Up",
    "name": "Dumbbell Step-Up",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["glutes"],
    "instructions": ["Stand in front of a sturdy box or bench with dumbbells at your sides, step one foot up and drive through the heel to stand on top, then step back down. Alternate legs."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Standing_Calf_Raise",
    "name": "Dumbbell Standing Calf Raise",
    "force": "push",
    "level": "beginner",
    "mechanic": "isolation",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["calves"],
    "secondaryMuscles": [],
    "instructions": ["Stand on the edge of a step or weight plate with the balls of your feet, holding dumbbells at your sides, then rise up onto your toes as high as possible and lower below the step level."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_V_Up",
    "name": "Dumbbell V-Up",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["abdominals"],
    "secondaryMuscles": [],
    "instructions": ["Lie flat on your back holding a single dumbbell overhead, then simultaneously raise your arms and legs to meet over your midsection in a V shape. Lower with control."],
    "volumeMultiplier": 1
  },
  {
    "id": "Dumbbell_Frog_Hip_Thrust",
    "name": "Dumbbell Frog Hip Thrust",
    "force": "pull",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["glutes"],
    "secondaryMuscles": ["hamstrings"],
    "instructions": ["Sit with your upper back against a bench and the soles of your feet pressed together with knees out wide, place a dumbbell across your hips, then drive the hips up to lockout."],
    "volumeMultiplier": 1
  },
  {
    "id": "Squat_To_Overhead_Press",
    "name": "Squat To Overhead Press",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["shoulders", "glutes", "triceps"],
    "instructions": ["Hold dumbbells at shoulder height and squat down to parallel, then drive up explosively and press the dumbbells overhead in one motion. Lower the weights as you descend into the next rep."],
    "volumeMultiplier": 2
  },
  {
    "id": "Dumbbell_Lunge_With_Bicep_Curl",
    "name": "Dumbbell Lunge With Bicep Curl",
    "force": "push",
    "level": "intermediate",
    "mechanic": "compound",
    "equipment": "dumbbell",
    "category": "strength",
    "primaryMuscles": ["quadriceps"],
    "secondaryMuscles": ["biceps", "glutes"],
    "instructions": ["Hold dumbbells at your sides, step forward into a lunge while curling both dumbbells to your shoulders, lower the weights as you push back to standing, then alternate legs."],
    "volumeMultiplier": 2
  }
]
```

- [ ] **Step 2: Run the unit test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Run a debug build to confirm the JSON parses on first launch**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

(Full deserialization is exercised at runtime by `DatabaseSeeder.seedExercises` during app startup. We verify that end-to-end in Task 5.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/exercises.json
git commit -m "Replace exercise library with 73 dumbbell-only exercises"
```

---

## Task 3: Delete the bundled image assets

**Files:**
- Delete: `app/src/main/assets/exercises/` (entire directory tree)

- [ ] **Step 1: Delete the assets directory**

Run: `rm -rf app/src/main/assets/exercises`
Expected: no output, directory gone.

- [ ] **Step 2: Verify it's gone**

Run: `ls app/src/main/assets/`
Expected: only `exercises.json` remains.

- [ ] **Step 3: Run a debug build to confirm nothing references the deleted assets**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL with no warnings about missing assets.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/assets/
git commit -m "Delete bundled exercise image assets"
```

---

## Task 4: Remove the coil dependency

**Files:**
- Modify: `app/build.gradle.kts:95-97`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Remove the coil block from `app/build.gradle.kts`**

In `app/build.gradle.kts`, delete these three lines (currently lines 95-97):

```kotlin
    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
```

- [ ] **Step 2: Remove the coil entries from `gradle/libs.versions.toml`**

Open `gradle/libs.versions.toml`, find the `coil` version line (under `[versions]`) and the `coil-compose` and `coil-network-okhttp` library entries (under `[libraries]`), and delete all three.

If the file structure isn't immediately obvious, run `grep -n coil gradle/libs.versions.toml` to find the line numbers first.

- [ ] **Step 3: Run a debug build to confirm no consumer broke**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If any source file still imports `coil3.*`, the build will fail with "unresolved reference" — that means Step 6 of Task 1 missed an import; remove it.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml
git commit -m "Remove unused coil image-loading dependency"
```

---

## Task 5: End-to-end verification

This task has no commits — it confirms the changes work on a real device or emulator before considering the work done.

- [ ] **Step 1: Confirm a Room schema file for v7 exists**

Run: `ls app/schemas/com.astute.body.data.local.AppDatabase/`
Expected: includes `7.json` alongside `1.json`-`6.json`.

- [ ] **Step 2: Run the full unit test suite one more time**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install the new build over the existing v6 install on a connected device or emulator**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL, app installed. Do **not** uninstall the previous version first — the migration is the thing being tested.

- [ ] **Step 4: Launch the app and confirm the migration succeeds**

Open the app on the device. Expected: app launches without `IllegalStateException: Migration didn't properly handle ...`. The Home screen renders.

- [ ] **Step 5: Generate a workout and confirm only new exercises appear**

On the Home screen, generate a workout. Expected: every exercise has a name from the new dumbbell library (e.g. "Dumbbell Bench Press", "Goblet Squat") — none of the old exercises like "Cable Crossover" or "Barbell Squat" should appear.

- [ ] **Step 6: Open an exercise detail screen and confirm no image area**

Tap any exercise in the workout to open its detail screen. Expected: the screen opens directly to the "Instructions" section with no image at the top, no fitness-center placeholder icon, and no leftover empty space where images used to be.

- [ ] **Step 7: Confirm ManageExercises shows the right count**

Navigate to the Manage Exercises screen (via Settings if needed). Expected: roughly 73 entries listed, all dumbbell-flavored.

- [ ] **Step 8: Smoke-test the full workout flow**

Start a workout, log a set on the first exercise, advance through the workout, and finish. Expected: summary screen renders, workout saves to history, no crashes. Re-open Home and confirm a fresh workout suggestion is generated.

- [ ] **Step 9: Confirm APK shrinkage**

Run: `ls -lh app/build/outputs/apk/debug/app-debug.apk`
Expected: noticeably smaller than before the asset deletion. (Pre-change APK had ~800 image directories bundled.)

---

## Notes for the implementer

- **TDD note:** This work is largely a refactor (the data model shrinks, the data content is replaced) rather than new behavior. The "test" is the existing unit test suite plus the manual verification in Task 5. There are no new behaviors that warrant new unit tests.
- **Migration testing:** Room migration tests need an emulator/device (`./gradlew connectedAndroidTest`). The plan relies on Step 3 of Task 5 (install over existing v6 install) for migration verification. If the user runs this on a fresh install only, the migration path is never exercised.
- **Why one big Task 1:** Splitting the schema change across multiple commits would leave the build broken between commits because the entity, JSON model, mapper, migration, and tests all change together. Treating Task 1 atomically keeps `main` always-buildable.
- **Why no separate "delete `MuscleGroup.datasetMuscles`" task:** It's not dead code — `WorkoutGenerator.inferMuscleGroup` and `getFilteredExercises` still use it to bucket exercises by their `primaryMuscles` strings. Removing it would be a separate refactor (option B in the spec, explicitly out of scope).
