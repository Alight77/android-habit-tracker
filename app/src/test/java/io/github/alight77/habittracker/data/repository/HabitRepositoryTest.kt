package io.github.alight77.habittracker.data.repository

import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.local.RecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitRepositoryTest {

    @Test
    fun editOperations_delegateToHabitDao() = runBlocking {
        val habit = HabitEntity(
            id = 7,
            name = "阅读",
            description = "",
            targetPerWeek = 3,
            createdAt = 10L
        )
        val habitDao = RecordingHabitDao(habit)
        val repository = HabitRepository(habitDao, RecordingRecordDao())

        assertEquals(habit, repository.getHabitById(7))

        val updatedHabit = habit.copy(name = "晨读", targetPerWeek = 5)
        repository.updateHabit(updatedHabit)
        repository.deleteHabitById(7)

        assertEquals(updatedHabit, habitDao.updatedHabit)
        assertEquals(listOf(7), habitDao.deletedHabitIds)
    }

    @Test
    fun setTodayRecordChecked_delegatesToDaoTransactionEntryPoint() = runBlocking {
        val recordDao = RecordingRecordDao()
        val repository = HabitRepository(
            habitDao = EmptyHabitDao(),
            recordDao = recordDao
        )

        repository.setTodayRecordChecked(
            habitId = 7,
            epochDay = LocalDate.of(2026, 7, 7).toEpochDay(),
            targetChecked = true
        )

        assertEquals(
            listOf(RecordWriteCall(7, LocalDate.of(2026, 7, 7).toEpochDay(), true)),
            recordDao.setRecordCheckedCalls
        )
    }

    private data class RecordWriteCall(
        val habitId: Int,
        val epochDay: Long,
        val targetChecked: Boolean
    )

    private class EmptyHabitDao : HabitDao {
        override suspend fun insertHabit(habit: HabitEntity) = Unit
        override suspend fun deleteHabit(habit: HabitEntity) = Unit
        override suspend fun getHabitById(habitId: Int): HabitEntity? = null
        override suspend fun updateHabit(habit: HabitEntity) = Unit
        override suspend fun deleteHabitById(habitId: Int) = Unit
        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    }

    private class RecordingHabitDao(
        private val habit: HabitEntity
    ) : HabitDao {
        var updatedHabit: HabitEntity? = null
        val deletedHabitIds = mutableListOf<Int>()

        override suspend fun insertHabit(habit: HabitEntity) = Unit
        override suspend fun deleteHabit(habit: HabitEntity) = Unit
        override suspend fun getHabitById(habitId: Int): HabitEntity? = habit.takeIf { it.id == habitId }
        override suspend fun updateHabit(habit: HabitEntity) {
            updatedHabit = habit
        }

        override suspend fun deleteHabitById(habitId: Int) {
            deletedHabitIds += habitId
        }

        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    }

    private class RecordingRecordDao : RecordDao {
        val setRecordCheckedCalls = mutableListOf<RecordWriteCall>()

        override suspend fun setRecordChecked(habitId: Int, epochDay: Long, targetChecked: Boolean) {
            setRecordCheckedCalls += RecordWriteCall(habitId, epochDay, targetChecked)
        }

        override suspend fun insertRecord(record: RecordEntity) = Unit
        override suspend fun deleteRecord(record: RecordEntity) = Unit
        override suspend fun updateRecord(record: RecordEntity) = Unit
        override suspend fun getRecordByDate(habitId: Int, epochDay: Long): RecordEntity? = null
        override fun getRecordsForHabitId(habitId: Int): Flow<List<RecordEntity>> = flowOf(emptyList())
        override fun getAllRecords(): Flow<List<RecordEntity>> = flowOf(emptyList())
        override fun observeRecordByDate(habitId: Int, epochDay: Long): Flow<RecordEntity?> = flowOf(null)
        override fun getDoneRecord(habitId: Int): Flow<List<RecordEntity>> = flowOf(emptyList())
    }
}
