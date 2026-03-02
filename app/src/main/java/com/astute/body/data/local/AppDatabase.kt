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
    version = 5,
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
    }
}
