package io.github.alight77.habittracker.feature.edit

import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.testutil.MainDispatcherRule
import kotlinx.coroutines.CancellationException
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
            createdAt = 100L,
            iconKey = "cycling"
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
        assertEquals(IconKey("cycling"), viewModel.uiState.value.iconKey)

        viewModel.onNameChanged("晨读")
        repeat(2) { viewModel.increaseTargetPerWeek() }
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals(
            originalHabit.copy(name = "晨读", targetPerWeek = 5),
            habitDao.updatedHabit
        )
        assertTrue(viewModel.uiState.value.navigateBackRequestId != null)
        assertEquals(0, habitDao.insertCalls)
        assertEquals(1, habitDao.updateCalls)
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
        assertEquals(R.string.edit_habit_not_found, viewModel.uiState.value.loadErrorResId)
    }

    @Test
    fun blankName_keepsDraftUntilInputChangesAndUsesSharedResource() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"))
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()

        viewModel.onNameChanged("   ")
        viewModel.selectIcon(IconKey.WATER)
        viewModel.decreaseTargetPerWeek()
        viewModel.saveHabit()

        assertEquals(R.string.habit_name_required, viewModel.uiState.value.nameErrorResId)
        assertEquals("   ", viewModel.uiState.value.name)
        assertEquals(IconKey.WATER, viewModel.uiState.value.iconKey)
        assertEquals(2, viewModel.uiState.value.targetPerWeek)
        assertEquals(0, habitDao.updateCalls)

        viewModel.onNameChanged("晨读")
        assertEquals(null, viewModel.uiState.value.nameErrorResId)
    }

    @Test
    fun loadFailure_retryFetchesSameHabitAndClearsError() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"), failOnLoad = true)
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()

        assertEquals(R.string.edit_habit_load_failed, viewModel.uiState.value.loadErrorResId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(7), habitDao.requestedIds)

        habitDao.failOnLoad = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(listOf(7, 7), habitDao.requestedIds)
        assertEquals(null, viewModel.uiState.value.loadErrorResId)
        assertEquals("阅读", viewModel.uiState.value.name)
        assertEquals(3, viewModel.uiState.value.targetPerWeek)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun cancelledLoadDoesNotBecomeOrdinaryLoadFailure() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"), cancelOnLoad = true)
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()

        assertEquals(listOf(7), habitDao.requestedIds)
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.loadErrorResId)
    }

    @Test
    fun saveHabit_whenUpdateFails_keepsFormAndEmitsFailureMessage() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"), failOnUpdate = true)
        val viewModel = EditHabitViewModel(
            repository = HabitRepository(habitDao, EmptyRecordDao()),
            habitId = 7,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()
        val events = mutableListOf<EditHabitUiEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onNameChanged("晨读")
        viewModel.selectIcon(IconKey.WATER)
        viewModel.saveHabit()
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals("晨读", viewModel.uiState.value.name)
        assertEquals(IconKey.WATER, viewModel.uiState.value.iconKey)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(listOf(EditHabitUiEvent.SaveFailed(R.string.habit_save_failed)), events)
        assertEquals(1, habitDao.updateCalls)
        collector.cancel()
    }

    @Test
    fun successfulSaveEmitsNoFailureEventAndUpdatesExistingHabitOnce() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"))
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        val events = mutableListOf<EditHabitUiEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onNameChanged("晨读")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals(1, habitDao.updateCalls)
        assertEquals(0, habitDao.insertCalls)
        assertEquals("晨读", habitDao.updatedHabit?.name)
        assertTrue(viewModel.uiState.value.navigateBackRequestId != null)
        collector.cancel()
    }

    @Test
    fun repeatedSaveWhileSavingOnlyUpdatesOnce() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"))
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()

        viewModel.onNameChanged("晨读")
        viewModel.saveHabit()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.saveHabit()
        advanceUntilIdle()

        assertEquals(1, habitDao.updateCalls)
        assertEquals(0, habitDao.insertCalls)
    }

    @Test
    fun cancelledSaveDoesNotBecomeFailureEvent() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读"), cancelOnUpdate = true)
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        val events = mutableListOf<EditHabitUiEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.onNameChanged("晨读")
        viewModel.saveHabit()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals("晨读", viewModel.uiState.value.name)
        assertEquals(null, viewModel.uiState.value.navigateBackRequestId)
        collector.cancel()
    }

    @Test
    fun loadedIcon_isStableAcrossNameChanges_andExplicitRecommendationIsOneShot() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读", iconKey = "reading"))
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        assertEquals(IconKey.READING, viewModel.uiState.value.iconKey)
        viewModel.onNameChanged("晨跑")
        assertEquals(IconKey.READING, viewModel.uiState.value.iconKey)
        viewModel.recommendIconForCurrentName()
        assertEquals(IconKey.EXERCISE, viewModel.uiState.value.iconKey)
        viewModel.onNameChanged("健康餐")
        assertEquals(IconKey.EXERCISE, viewModel.uiState.value.iconKey)
        viewModel.selectIcon(IconKey.WATER)
        viewModel.saveHabit()
        advanceUntilIdle()
        assertEquals("water", habitDao.updatedHabit?.iconKey)
    }

    @Test
    fun unknownIcon_isPreservedUntilExplicitReplacement() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "阅读", iconKey = "cycling"))
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        assertEquals(IconKey("cycling"), viewModel.uiState.value.iconKey)
        viewModel.onNameChanged("晨跑")
        assertEquals(IconKey("cycling"), viewModel.uiState.value.iconKey)
        assertEquals(null, habitDao.updatedHabit)
        viewModel.saveHabit()
        advanceUntilIdle()
        assertEquals("cycling", habitDao.updatedHabit?.iconKey)
    }

    @Test
    fun unknownIcon_explicitRecommendationReplacesRawKeyOnlyOnSave() = runTest {
        val habitDao = EditableHabitDao(habit(id = 7, name = "健康餐", iconKey = "cycling"))
        val viewModel = EditHabitViewModel(
            HabitRepository(habitDao, EmptyRecordDao()), 7, mainDispatcherRule.dispatcher
        )
        advanceUntilIdle()
        viewModel.recommendIconForCurrentName()
        assertEquals(IconKey.NUTRITION, viewModel.uiState.value.iconKey)
        assertEquals(null, habitDao.updatedHabit)
        viewModel.saveHabit()
        advanceUntilIdle()
        assertEquals("nutrition", habitDao.updatedHabit?.iconKey)
    }

    private fun habit(
        id: Int,
        name: String,
        description: String = "",
        targetPerWeek: Int = 3,
        createdAt: Long = 0L,
        iconKey: String = "general"
    ): HabitEntity {
        return HabitEntity(
            id = id,
            name = name,
            description = description,
            targetPerWeek = targetPerWeek,
            createdAt = createdAt,
            iconKey = iconKey
        )
    }

    private class EditableHabitDao(
        private var habit: HabitEntity?,
        private val failOnUpdate: Boolean = false,
        var failOnLoad: Boolean = false,
        private val cancelOnUpdate: Boolean = false,
        private val cancelOnLoad: Boolean = false
    ) : HabitDao {
        var updatedHabit: HabitEntity? = null
        var updateCalls: Int = 0
        var insertCalls: Int = 0
        val requestedIds = mutableListOf<Int>()

        override suspend fun insertHabit(habit: HabitEntity) { insertCalls++ }
        override suspend fun deleteHabit(habit: HabitEntity) = Unit
        override suspend fun getHabitById(habitId: Int): HabitEntity? {
            requestedIds += habitId
            if (cancelOnLoad) throw CancellationException("load cancelled")
            if (failOnLoad) error("database unavailable")
            return habit?.takeIf { it.id == habitId }
        }

        override suspend fun updateHabit(habit: HabitEntity) {
            updateCalls++
            if (cancelOnUpdate) throw CancellationException("save cancelled")
            if (failOnUpdate) error("database unavailable")
            this.habit = habit
            updatedHabit = habit
        }

        override suspend fun deleteHabitById(habitId: Int) = 0
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
