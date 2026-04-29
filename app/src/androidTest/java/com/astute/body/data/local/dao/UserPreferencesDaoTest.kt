package com.astute.body.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astute.body.data.local.AppDatabase
import com.astute.body.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UserPreferencesDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: UserPreferencesDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.userPreferencesDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getOnce_returnsNullWhenEmpty() = runTest {
        assertNull(dao.getOnce())
    }

    @Test
    fun upsert_insertsAndRetrievesSingleton() = runTest {
        dao.upsert(UserPreferencesEntity(targetWorkoutSize = 10))

        val prefs = dao.getOnce()
        assertEquals(10, prefs!!.targetWorkoutSize)
        assertEquals("lbs", prefs.weightUnit)
    }

    @Test
    fun upsert_updatesExistingRow() = runTest {
        dao.upsert(UserPreferencesEntity(weightUnit = "lbs"))
        dao.upsert(UserPreferencesEntity(weightUnit = "kg"))

        val prefs = dao.getOnce()
        assertEquals("kg", prefs!!.weightUnit)
    }
}
