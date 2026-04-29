package com.astute.body.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.astute.body.data.local.dao.ActiveWorkoutDao
import com.astute.body.data.local.dao.ExerciseDao
import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.RecoveryConfigDao
import com.astute.body.data.local.dao.UserPreferencesDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.ActiveWorkoutEntity
import com.astute.body.data.local.entity.ExerciseEntity
import com.astute.body.data.local.entity.ExerciseLogEntity
import com.astute.body.data.local.entity.PersonalRecordEntity
import com.astute.body.data.local.entity.RecoveryConfigEntity
import com.astute.body.data.local.entity.UserPreferencesEntity
import com.astute.body.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        ExerciseLogEntity::class,
        PersonalRecordEntity::class,
        UserPreferencesEntity::class,
        RecoveryConfigEntity::class,
        ActiveWorkoutEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun recoveryConfigDao(): RecoveryConfigDao
    abstract fun activeWorkoutDao(): ActiveWorkoutDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_logs_exerciseId ON exercise_logs(exerciseId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS active_workout (
                        id INTEGER NOT NULL PRIMARY KEY,
                        exerciseRefs TEXT NOT NULL,
                        currentIndex INTEGER NOT NULL,
                        logEntries TEXT NOT NULL,
                        setsCompleted INTEGER NOT NULL,
                        currentSets INTEGER NOT NULL,
                        currentReps INTEGER NOT NULL,
                        currentWeight REAL NOT NULL,
                        startedAtMillis INTEGER NOT NULL,
                        newPRs TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE active_workout ADD COLUMN currentExerciseSets TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE active_workout ADD COLUMN allExerciseSets TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN volumeMultiplier INTEGER NOT NULL DEFAULT 1")

                // All dumbbell exercises default to x2 (per-dumbbell weight)
                db.execSQL("UPDATE exercises SET volumeMultiplier = 2 WHERE equipment = 'dumbbell'")

                // Single-dumbbell bilateral exceptions back to x1
                db.execSQL("""
                    UPDATE exercises SET volumeMultiplier = 1 WHERE id IN (
                        'Bent-Arm_Dumbbell_Pullover',
                        'Straight-Arm_Dumbbell_Pullover',
                        'Seated_Bent-Over_Two-Arm_Dumbbell_Triceps_Extension',
                        'Standing_Bent-Over_Two-Arm_Dumbbell_Triceps_Extension',
                        'Two-Arm_Dumbbell_Preacher_Curl',
                        'Single_Dumbbell_Raise',
                        'Seated_Triceps_Press',
                        'Standing_Dumbbell_Triceps_Extension',
                        'Plie_Dumbbell_Squat',
                        'Calf_Raise_On_A_Dumbbell',
                        'Vertical_Swing'
                    )
                """.trimIndent())

                // Cable dual-cable and unilateral exercises to x2
                db.execSQL("""
                    UPDATE exercises SET volumeMultiplier = 2 WHERE id IN (
                        'Cable_Crossover',
                        'Cable_Iron_Cross',
                        'Flat_Bench_Cable_Flyes',
                        'Incline_Cable_Flye',
                        'Low_Cable_Crossover',
                        'Cable_Chest_Press',
                        'Incline_Cable_Chest_Press',
                        'Cable_Rear_Delt_Fly',
                        'Overhead_Cable_Curl',
                        'High_Cable_Curls',
                        'Alternating_Cable_Shoulder_Press',
                        'Cable_One_Arm_Tricep_Extension',
                        'Kneeling_Single-Arm_High_Pulley_Row',
                        'One-Arm_High-Pulley_Cable_Side_Bends',
                        'One-Legged_Cable_Kickback',
                        'One_Arm_Lat_Pulldown',
                        'Seated_One-arm_Cable_Pulley_Rows',
                        'Single-Arm_Cable_Crossover',
                        'Standing_Low-Pulley_One-Arm_Triceps_Extension',
                        'Standing_One-Arm_Cable_Curl'
                    )
                """.trimIndent())

                // Other equipment unilateral exercises to x2
                db.execSQL("""
                    UPDATE exercises SET volumeMultiplier = 2
                    WHERE (equipment IS NULL OR equipment NOT IN ('dumbbell', 'cable'))
                      AND (
                          LOWER(name) LIKE '%one-arm%' OR LOWER(name) LIKE '%one arm%'
                          OR LOWER(name) LIKE '%one-leg%' OR LOWER(name) LIKE '%one leg%'
                          OR LOWER(name) LIKE '%single-arm%' OR LOWER(name) LIKE '%single arm%'
                          OR LOWER(name) LIKE '%single-leg%' OR LOWER(name) LIKE '%single leg%'
                          OR LOWER(name) LIKE '%alternate%' OR LOWER(name) LIKE '%alternating%'
                      )
                """.trimIndent())
            }
        }

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
    }
}
