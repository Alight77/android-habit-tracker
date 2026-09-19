package com.example.habittracker.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDatabaseDaoTest {

    private lateinit var database: HabitDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getAllHabits_returnsNewestHabitFirst() = runBlocking {
        database.habitDao().insertHabit(habit(name = "早起", createdAt = 1))
        database.habitDao().insertHabit(habit(name = "阅读", createdAt = 2))

        assertEquals(
            listOf("阅读", "早起"),
            database.habitDao().getAllHabits().first().map { it.name }
        )
    }

    @Test
    fun setRecordChecked_updatesExistingRecordInsteadOfCreatingDuplicate() = runBlocking {
        database.habitDao().insertHabit(habit(name = "阅读", createdAt = 1))
        val habitId = database.habitDao().getAllHabits().first().single().id

        database.recordDao().setRecordChecked(habitId, epochDay = 20_000L, targetChecked = true)
        database.recordDao().setRecordChecked(habitId, epochDay = 20_000L, targetChecked = false)

        val records = database.recordDao().getRecordsForHabitId(habitId).first()
        assertEquals(1, records.size)
        assertEquals(false, records.single().isDone)
    }

    private fun habit(name: String, createdAt: Long): HabitEntity {
        return HabitEntity(
            name = name,
            description = "",
            targetPerWeek = 7,
            createdAt = createdAt
        )
    }
}
