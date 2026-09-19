package com.example.habittracker.viewmodel

import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.data.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModelTest {

    @Test
    fun saveHabit_withBlankName_showsValidationErrorWithoutWriting() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = HabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.onNameChanged("   ")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("请输入习惯名称", viewModel.uiState.value.nameError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(habitDao.insertedHabits.isEmpty())
    }

    @Test
    fun saveHabit_withValidName_trimsNameWritesHabitAndEmitsSaved() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = HabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val events = mutableListOf<AddHabitUiEvent>()
        val collectEvents = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onNameChanged("  阅读  ")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("阅读", habitDao.insertedHabits.single().name)
        assertEquals(AddHabitUiState(), viewModel.uiState.value)
        assertEquals(listOf(AddHabitUiEvent.Saved), events)
        collectEvents.cancel()
    }

    @Test
    fun saveHabit_whenWriteFails_keepsNameAndEmitsFailureMessage() = runTest {
        val viewModel = HabitViewModel(
            repository = HabitRepository(FailingHabitDao(), EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val events = mutableListOf<AddHabitUiEvent>()
        val collectEvents = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onNameChanged("阅读")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("阅读", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(
            listOf(AddHabitUiEvent.SaveFailed("保存失败，请稍后重试")),
            events
        )
        collectEvents.cancel()
    }

    private class RecordingHabitDao : HabitDao {
        val insertedHabits = mutableListOf<HabitEntity>()

        override suspend fun insertHabit(habit: HabitEntity) {
            insertedHabits += habit
        }

        override suspend fun deleteHabit(habit: HabitEntity) = Unit

        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    }

    private class FailingHabitDao : HabitDao {
        override suspend fun insertHabit(habit: HabitEntity) {
            error("database unavailable")
        }

        override suspend fun deleteHabit(habit: HabitEntity) = Unit

        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    }

    private class EmptyRecordDao : RecordDao {
        override suspend fun setRecordChecked(habitId: Int, epochDay: Long, targetChecked: Boolean) = Unit
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
