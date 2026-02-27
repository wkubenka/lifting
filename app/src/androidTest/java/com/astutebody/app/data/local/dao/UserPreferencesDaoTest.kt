package com.astutebody.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astutebody.app.data.local.AppDatabase
import com.astutebody.app.data.local.entity.UserPreferencesEntity
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
        dao.upsert(UserPreferencesEntity(
            availableEquipment = listOf("barbell", "dumbbell"),
            experienceLevel = "intermediate"
        ))

        val prefs = dao.getOnce()
        assertEquals("intermediate", prefs!!.experienceLevel)
        assertEquals(listOf("barbell", "dumbbell"), prefs.availableEquipment)
        assertEquals(8, prefs.targetWorkoutSize)
    }

    @Test
    fun upsert_updatesExistingRow() = runTest {
        dao.upsert(UserPreferencesEntity(experienceLevel = "beginner"))
        dao.upsert(UserPreferencesEntity(experienceLevel = "expert", weightUnit = "kg"))

        val prefs = dao.getOnce()
        assertEquals("expert", prefs!!.experienceLevel)
        assertEquals("kg", prefs.weightUnit)
    }
}
