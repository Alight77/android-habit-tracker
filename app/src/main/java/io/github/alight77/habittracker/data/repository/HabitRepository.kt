package io.github.alight77.habittracker.data.repository

import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class HabitRepository(
    private val habitDao: HabitDao,
    private val recordDao: RecordDao
) {

    suspend fun addHabit(habit: HabitEntity) {
        habitDao.insertHabit(habit)
    }

    suspend fun getHabitById(habitId: Int): HabitEntity? {
        return habitDao.getHabitById(habitId)
    }

    suspend fun updateHabit(habit: HabitEntity) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: HabitEntity) {
        habitDao.deleteHabit(habit)
    }

    suspend fun deleteHabitById(habitId: Int) {
        habitDao.deleteHabitById(habitId)
    }

    suspend fun setTodayRecordChecked(habitId: Int, epochDay: Long, targetChecked: Boolean) {
        recordDao.setRecordChecked(habitId, epochDay, targetChecked)
    }

    fun observeTodayRecord(habitId: Int, epochDay: Long): Flow<Boolean> {
        return recordDao.observeRecordByDate(habitId, epochDay)
            .map { record -> record?.isDone == true }
            .distinctUntilChanged()
    }

}
