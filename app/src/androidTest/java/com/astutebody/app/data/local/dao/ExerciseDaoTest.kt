package com.astutebody.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.astutebody.app.data.local.AppDatabase
import com.astutebody.app.data.local.entity.ExerciseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExerciseDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.exerciseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndCount() = runTest {
        assertEquals(0, dao.count())
        dao.insertAll(listOf(testExercise("Bench_Press"), testExercise("Squat")))
        assertEquals(2, dao.count())
    }

    @Test
    fun getById_returnsExercise() = runTest {
        dao.insertAll(listOf(testExercise("Bench_Press")))
        val result = dao.getById("Bench_Press")
        assertNotNull(result)
        assertEquals("Bench_Press", result!!.id)
    }

    @Test
    fun getById_returnsNullForMissing() = runTest {
        assertNull(dao.getById("nonexistent"))
    }

    @Test
    fun getByMuscle_filtersCorrectly() = runTest {
        dao.insertAll(listOf(
            testExercise("Bench_Press", primaryMuscles = listOf("chest", "triceps")),
            testExercise("Squat", primaryMuscles = listOf("quadriceps", "glutes")),
            testExercise("Dip", primaryMuscles = listOf("chest", "triceps", "shoulders"))
        ))
        val chestExercises = dao.getByMuscle("chest")
        assertEquals(2, chestExercises.size)
        val quadExercises = dao.getByMuscle("quadriceps")
        assertEquals(1, quadExercises.size)
    }

    @Test
    fun getDistinctEquipment_returnsUniqueValues() = runTest {
        dao.insertAll(listOf(
            testExercise("ex1", equipment = "barbell"),
            testExercise("ex2", equipment = "dumbbell"),
            testExercise("ex3", equipment = "barbell"),
            testExercise("ex4", equipment = null)
        ))
        val equipment = dao.getDistinctEquipment()
        assertEquals(listOf("barbell", "dumbbell"), equipment)
    }

    private fun testExercise(
        id: String,
        primaryMuscles: List<String> = listOf("chest"),
        equipment: String? = "barbell"
    ) = ExerciseEntity(
        id = id,
        name = id.replace("_", " "),
        force = "push",
        level = "beginner",
        mechanic = "compound",
        equipment = equipment,
        category = "strength",
        primaryMuscles = primaryMuscles,
        secondaryMuscles = emptyList(),
        instructions = listOf("Do it"),
        images = emptyList()
    )
}
