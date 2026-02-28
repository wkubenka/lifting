package com.astute.body.data.repository

import com.astute.body.data.local.dao.ExerciseLogDao
import com.astute.body.data.local.dao.PersonalRecordDao
import com.astute.body.data.local.dao.WorkoutSessionDao
import com.astute.body.data.local.entity.PersonalRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalRecordRecalculator @Inject constructor(
    private val exerciseLogDao: ExerciseLogDao,
    private val personalRecordDao: PersonalRecordDao,
    private val workoutSessionDao: WorkoutSessionDao
) {
    suspend fun recalculateForExercise(exerciseId: String) {
        val logs = exerciseLogDao.getByExerciseId(exerciseId)
        if (logs.isEmpty()) {
            personalRecordDao.deleteByExerciseId(exerciseId)
            return
        }

        // Build a cache of session dates to avoid repeated queries
        val sessionDates = mutableMapOf<Long, Long>()
        for (log in logs) {
            if (log.sessionId !in sessionDates) {
                val session = workoutSessionDao.getById(log.sessionId)
                if (session != null) {
                    sessionDates[log.sessionId] = session.date
                }
            }
        }

        var maxWeight = 0.0
        var maxWeightDate: Long? = null
        var maxReps = 0
        var maxRepsDate: Long? = null
        var lastPerformed: Long? = null
        var lastWeight = 0.0
        var lastReps = 0

        for (log in logs) {
            val date = sessionDates[log.sessionId] ?: continue

            if (log.weight > maxWeight) {
                maxWeight = log.weight
                maxWeightDate = date
            }
            if (log.reps > maxReps) {
                maxReps = log.reps
                maxRepsDate = date
            }
            if (lastPerformed == null || date > lastPerformed) {
                lastPerformed = date
                lastWeight = log.weight
                lastReps = log.reps
            }
        }

        personalRecordDao.upsert(
            PersonalRecordEntity(
                exerciseId = exerciseId,
                maxWeight = maxWeight,
                maxWeightDate = maxWeightDate,
                maxReps = maxReps,
                maxRepsDate = maxRepsDate,
                lastWeight = lastWeight,
                lastReps = lastReps,
                lastPerformed = lastPerformed
            )
        )
    }
}
