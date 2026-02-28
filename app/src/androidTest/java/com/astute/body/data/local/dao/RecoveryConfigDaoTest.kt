package com.astute.body.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astute.body.data.local.AppDatabase
import com.astute.body.data.local.entity.RecoveryConfigEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecoveryConfigDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: RecoveryConfigDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.recoveryConfigDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAll_andGetAll() = runTest {
        val configs = listOf(
            RecoveryConfigEntity("Chest", 48),
            RecoveryConfigEntity("Back", 60)
        )
        dao.insertAll(configs)

        val result = dao.getAll()
        assertEquals(2, result.size)
    }

    @Test
    fun getByMuscleGroup_returnsCorrectConfig() = runTest {
        dao.insertAll(listOf(
            RecoveryConfigEntity("Chest", 48),
            RecoveryConfigEntity("Core", 24)
        ))

        val chest = dao.getByMuscleGroup("Chest")
        assertEquals(48, chest!!.minRecoveryHours)

        val core = dao.getByMuscleGroup("Core")
        assertEquals(24, core!!.minRecoveryHours)
    }

    @Test
    fun insertAll_ignoresDuplicates() = runTest {
        dao.insertAll(listOf(RecoveryConfigEntity("Chest", 48)))
        dao.insertAll(listOf(RecoveryConfigEntity("Chest", 72)))

        val chest = dao.getByMuscleGroup("Chest")
        assertEquals(48, chest!!.minRecoveryHours)
    }
}
