package com.example.habittracker.data.repository

import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.domain.usecase.calculateStreak
import com.example.habittracker.domain.usecase.epochMillisToLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HabitRepository(
    private val habitDao: HabitDao,
    private val recordDao: RecordDao
) {

    fun getAllHabits(): Flow<List<HabitEntity>> {
        return habitDao.getAllHabits()
    }

    suspend fun addHabit(habit: HabitEntity) {
        habitDao.insertHabit(habit)
    }

    suspend fun deleteHabit(habit: HabitEntity) {
        habitDao.deleteHabit(habit)
    }

    suspend fun setTodayRecordChecked(habitId: Int, date: Long, targetChecked: Boolean) {
        recordDao.setRecordChecked(habitId, date, targetChecked)
    }

    fun observeTodayRecord(habitId: Int, today: Long): Flow<Boolean> {
        return recordDao.observeRecordByDate(habitId, today)
            .map { record -> record?.isDone == true }
            .distinctUntilChanged()
    }

    fun getDoneRecords(habitId: Int): Flow<List<RecordEntity>> {
        return recordDao.getDoneRecord(habitId)
    }

    fun observeStreak(habitId: Int, today: LocalDate): Flow<Int> {
        return recordDao.getDoneRecord(habitId)
            .map { records ->
                val dates = records.map { record -> epochMillisToLocalDate(record.date) }
                calculateStreak(dates, today)
            }
            .distinctUntilChanged()
    }
}