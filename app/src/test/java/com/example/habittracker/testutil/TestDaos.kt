package com.example.habittracker.testutil

import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.local.RecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TestHabitDao(initialHabits: List<HabitEntity>) : HabitDao {
    private val habits = MutableStateFlow(initialHabits)

    override suspend fun insertHabit(habit: HabitEntity) {
        habits.value += habit
    }

    override suspend fun deleteHabit(habit: HabitEntity) {
        habits.value = habits.value.filterNot { it.id == habit.id }
    }

    override fun getAllHabits(): Flow<List<HabitEntity>> = habits
}

class TestRecordDao(initialRecords: List<RecordEntity>) : RecordDao {
    private val records = MutableStateFlow(initialRecords)

    override suspend fun setRecordChecked(habitId: Int, epochDay: Long, targetChecked: Boolean) {
        val record = getRecordByDate(habitId, epochDay)
        if (record == null) {
            records.value += RecordEntity(
                id = (records.value.maxOfOrNull { it.id } ?: 0) + 1,
                habitId = habitId,
                date = epochDay,
                isDone = targetChecked
            )
        } else {
            updateRecord(record.copy(isDone = targetChecked))
        }
    }

    override suspend fun insertRecord(record: RecordEntity) {
        records.value += record
    }

    override suspend fun deleteRecord(record: RecordEntity) {
        records.value = records.value.filterNot { it.id == record.id }
    }

    override suspend fun updateRecord(record: RecordEntity) {
        records.value = records.value.map { existing ->
            if (existing.id == record.id) record else existing
        }
    }

    override suspend fun getRecordByDate(habitId: Int, epochDay: Long): RecordEntity? {
        return records.value.firstOrNull { record ->
            record.habitId == habitId && record.date == epochDay
        }
    }

    override fun getRecordsForHabitId(habitId: Int): Flow<List<RecordEntity>> {
        return records.map { allRecords -> allRecords.filter { it.habitId == habitId } }
    }

    override fun getAllRecords(): Flow<List<RecordEntity>> = records

    override fun observeRecordByDate(habitId: Int, epochDay: Long): Flow<RecordEntity?> {
        return records.map { allRecords ->
            allRecords.firstOrNull { record ->
                record.habitId == habitId && record.date == epochDay
            }
        }
    }

    override fun getDoneRecord(habitId: Int): Flow<List<RecordEntity>> {
        return records.map { allRecords ->
            allRecords.filter { record -> record.habitId == habitId && record.isDone }
        }
    }
}
