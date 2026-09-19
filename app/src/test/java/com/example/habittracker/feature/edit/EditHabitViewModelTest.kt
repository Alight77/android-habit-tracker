package com.example.habittracker.feature.edit

import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditHabitViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveHabit_updatesOnlyEditableFieldsAndRequestsNavigation() = runTest {
        val originalHabit = habit(
            id = 7,
            name = "阅读",
            description = "保留的描述",
            targetPerWeek = 3,
            createdAt = 100L
        )
        val habitDao = EditableHabitDao(originalHabit)
        val viewModel = EditHabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            habitId = originalHabit.id,
            dispatcher = mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()

        assertEquals("阅读", viewModel.uiState.value.name)
        assertEquals(3, viewModel.uiState.value.targetPerWeek)

        viewModel.onNameChanged("晨读")
        repeat(2) { viewModel.increaseTargetPerWeek() }
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals(
            originalHabit.copy(name = "晨读", targetPerWeek = 5),
            habitDao.updatedHabit
        )
        assertTrue(viewModel.uiState.value.navigateBackRequestId != null)
    }

    @Test
    fun loadingMissingHabit_showsRetryableError() = runTest {
        val viewModel = EditHabitViewModel(
            repository = HabitRepository(EditableHabitDao(null), EmptyRecordDao()),
            habitId = 99,
            dispatcher = mainDispatcherRule.dispatcher
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("未找到该习惯", viewModel.uiState.value.loadError)
    }

    @Test
    fun saveHabit_whenUpdateFails_keepsFormAndEmitsFailureMessage() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"), failOnUpdate = true)
        val viewModel = EditHabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            habitId = 7,
            dispatcher = mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.onNameChanged("晨读")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("晨读", viewModel.uiState.value.name)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(EditHabitUiEvent.SaveFailed("保存失败，请稍后重试"), event.await())
    }

    private fun habit(
        id: Int,
        name: String,
        description: String = "",
        targetPerWeek: Int = 3,
        createdAt: Long = 0L
    ): HabitEntity {
        return HabitEntity(
            id = id,
            name = name,
            description = description,
            targetPerWeek = targetPerWeek,
            createdAt = createdAt
        )
    }

    private class EditableHabitDao(
        private var habit: HabitEntity?,
        private val failOnUpdate: Boolean = false
    ) : HabitDao {
        var updatedHabit: HabitEntity? = null

        override suspend fun insertHabit(habit: HabitEntity) = Unit
        override suspend fun deleteHabit(habit: HabitEntity) = Unit
        override suspend fun getHabitById(habitId: Int): HabitEntity? = habit?.takeIf { it.id == habitId }

        override suspend fun updateHabit(habit: HabitEntity) {
            if (failOnUpdate) error("database unavailable")
            this.habit = habit
            updatedHabit = habit
        }

        override suspend fun deleteHabitById(habitId: Int) = Unit
        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(listOfNotNull(habit))
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
