package com.example.habittracker.data.repository

import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.local.RecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitRepositoryTest {

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
