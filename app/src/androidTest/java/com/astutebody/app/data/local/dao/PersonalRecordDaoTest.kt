package com.astutebody.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astutebody.app.data.local.AppDatabase
import com.astutebody.app.data.local.entity.PersonalRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PersonalRecordDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PersonalRecordDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.personalRecordDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsert_insertsNewRecord() = runTest {
        assertNull(dao.getByExerciseId("Bench_Press"))

        dao.upsert(PersonalRecordEntity(
            exerciseId = "Bench_Press",
            maxWeight = 135.0,
            maxWeightDate = 1000L,
            lastWeight = 135.0,
            lastReps = 10,
            lastPerformed = 1000L
        ))

        val record = dao.getByExerciseId("Bench_Press")
        assertEquals(135.0, record!!.maxWeight, 0.01)
    }

    @Test
    fun upsert_updatesExistingRecord() = runTest {
        dao.upsert(PersonalRecordEntity(
            exerciseId = "Bench_Press",
            maxWeight = 135.0,
            lastWeight = 135.0,
            lastReps = 10
        ))

        dao.upsert(PersonalRecordEntity(
            exerciseId = "Bench_Press",
            maxWeight = 155.0,
            maxWeightDate = 2000L,
            lastWeight = 155.0,
            lastReps = 8,
            lastPerformed = 2000L
        ))

        val record = dao.getByExerciseId("Bench_Press")
        assertEquals(155.0, record!!.maxWeight, 0.01)
        assertEquals(8, record.lastReps)
    }
}
