package io.github.alight77.habittracker.feature.addhabit

import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.icon.IconKey
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
class AddHabitViewModelTest {

    @Test
    fun saveHabit_withBlankName_showsValidationErrorWithoutWriting() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = AddHabitViewModel(
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
    fun saveHabit_withValidName_keepsNavigateBackRequestUntilItIsConsumed() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = AddHabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.onNameChanged("  阅读  ")
        repeat(2) { viewModel.decreaseTargetPerWeek() }
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("阅读", habitDao.insertedHabits.single().name)
        assertEquals(5, habitDao.insertedHabits.single().targetPerWeek)
        assertEquals("reading", habitDao.insertedHabits.single().iconKey)
        val navigationRequestId = viewModel.uiState.value.navigateBackRequestId
        assertTrue(navigationRequestId != null)

        viewModel.onNavigateBackConsumed(navigationRequestId!!)

        assertEquals(AddHabitUiState(), viewModel.uiState.value)
    }

    @Test
    fun saveHabit_whileSaving_ignoresRepeatedRequest() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = AddHabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.onNameChanged("阅读")
        viewModel.saveHabit()
        viewModel.selectIcon(IconKey.WATER)
        viewModel.onNameChanged("晨跑")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals(1, habitDao.insertedHabits.size)
        assertEquals("reading", habitDao.insertedHabits.single().iconKey)
    }

    @Test
    fun automaticRecommendation_andManualOverride_areDistinct() = runTest {
        val viewModel = AddHabitViewModel(
            HabitRepository(RecordingHabitDao(), EmptyRecordDao()),
            StandardTestDispatcher(testScheduler)
        )

        assertEquals(IconKey.GENERAL, viewModel.uiState.value.effectiveIconKey)
        assertEquals(null, viewModel.uiState.value.manualIconKey)
        viewModel.onNameChanged("阅读")
        assertEquals(IconKey.READING, viewModel.uiState.value.effectiveIconKey)
        viewModel.onNameChanged("晨跑")
        assertEquals(IconKey.EXERCISE, viewModel.uiState.value.effectiveIconKey)
        viewModel.selectIcon(IconKey.WATER)
        viewModel.onNameChanged("健康餐")
        assertEquals(IconKey.WATER, viewModel.uiState.value.effectiveIconKey)
        viewModel.selectIcon(IconKey.GENERAL)
        viewModel.onNameChanged("阅读")
        assertEquals(IconKey.GENERAL, viewModel.uiState.value.manualIconKey)
        assertEquals(IconKey.GENERAL, viewModel.uiState.value.effectiveIconKey)
        viewModel.restoreAutomaticIcon()
        assertEquals(IconKey.READING, viewModel.uiState.value.effectiveIconKey)
        viewModel.onNameChanged("多喝水")
        assertEquals(IconKey.WATER, viewModel.uiState.value.effectiveIconKey)
    }

    @Test
    fun saveHabit_usesManualIconSnapshot() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = AddHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()),
            StandardTestDispatcher(testScheduler)
        )
        viewModel.onNameChanged("阅读")
        viewModel.selectIcon(IconKey.WATER)
        viewModel.saveHabit()
        advanceUntilIdle()
        assertEquals("water", habitDao.insertedHabits.single().iconKey)
    }

    @Test
    fun saveHabit_manualGeneralDoesNotSwitchBackToNameRecommendation() = runTest {
        val habitDao = RecordingHabitDao()
        val viewModel = AddHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()),
            StandardTestDispatcher(testScheduler)
        )
        viewModel.onNameChanged("阅读")
        viewModel.selectIcon(IconKey.GENERAL)
        viewModel.saveHabit()
        advanceUntilIdle()
        assertEquals("general", habitDao.insertedHabits.single().iconKey)
    }

    @Test
    fun targetPerWeek_isBoundedBetweenOneAndSeven() = runTest {
        val viewModel = AddHabitViewModel(
            repository = HabitRepository(RecordingHabitDao(), EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        assertEquals(7, viewModel.uiState.value.targetPerWeek)

        repeat(10) { viewModel.decreaseTargetPerWeek() }
        assertEquals(1, viewModel.uiState.value.targetPerWeek)

        repeat(10) { viewModel.increaseTargetPerWeek() }
        assertEquals(7, viewModel.uiState.value.targetPerWeek)
    }

    @Test
    fun saveHabit_whenWriteFails_keepsNameAndEmitsFailureMessage() = runTest {
        val viewModel = AddHabitViewModel(
            repository = HabitRepository(FailingHabitDao(), EmptyRecordDao()),
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val events = mutableListOf<AddHabitUiEvent>()
        val collectEvents = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onNameChanged("阅读")
        viewModel.selectIcon(IconKey.WATER)
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("阅读", viewModel.uiState.value.name)
        assertEquals(IconKey.WATER, viewModel.uiState.value.manualIconKey)
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

        override suspend fun getHabitById(habitId: Int): HabitEntity? = null

        override suspend fun updateHabit(habit: HabitEntity) = Unit

        override suspend fun deleteHabitById(habitId: Int) = 0

        override fun getAllHabits(): Flow<List<HabitEntity>> = flowOf(emptyList())
    }

    private class FailingHabitDao : HabitDao {
        override suspend fun insertHabit(habit: HabitEntity) {
            error("database unavailable")
        }

        override suspend fun deleteHabit(habit: HabitEntity) = Unit

        override suspend fun getHabitById(habitId: Int): HabitEntity? = null

        override suspend fun updateHabit(habit: HabitEntity) = Unit

        override suspend fun deleteHabitById(habitId: Int) = 0

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
