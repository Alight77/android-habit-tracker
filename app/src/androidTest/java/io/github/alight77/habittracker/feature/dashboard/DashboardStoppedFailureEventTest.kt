package io.github.alight77.habittracker.feature.dashboard

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardStoppedFailureEventTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun failureWhileStoppedRollsBackWithoutReplayingSnackbarAndCanRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(
            instrumentation.targetContext, HabitDatabase::class.java
        ).build()
        val habitDao = database.habitDao()
        val recordDao = database.recordDao()
        val habitName = "C43 stopped failure"
        val today = LocalDate.now()
        runBlocking {
            habitDao.insertHabit(
                HabitEntity(name = habitName, description = "", targetPerWeek = 3, createdAt = 0L)
            )
        }
        val habitId = runBlocking { habitDao.getAllHabits().first().single().id }
        val repository = HabitRepository(habitDao, recordDao)
        val firstWriteEntered = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val resumed = CompletableDeferred<Unit>()
        val reverted = CompletableDeferred<SetTodayHabitCheckedUseCase.DomainEvent.Reverted>()
        val applyCalls = AtomicInteger()
        val repositoryWrites = AtomicInteger()
        val lifecycleEvents = CopyOnWriteArrayList<Lifecycle.Event>()
        val domainEvents = CopyOnWriteArrayList<SetTodayHabitCheckedUseCase.DomainEvent>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (applyCalls.incrementAndGet() == 1) {
                    firstWriteEntered.complete(Unit)
                    releaseFirstWrite.await()
                    error("controlled persistence failure")
                }
                repository.setTodayRecordChecked(
                    command.habitId, command.today.toEpochDay(), command.targetChecked
                )
                repositoryWrites.incrementAndGet()
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
            }
        )
        val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        eventScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // 只观察 DomainEvent；额外订阅 UI SharedFlow 会改变本实验的无订阅者条件。
            useCase.events.collect { event ->
                domainEvents.add(event)
                if (event is SetTodayHabitCheckedUseCase.DomainEvent.Reverted) {
                    reverted.complete(event)
                }
            }
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                assertEquals(DashboardViewModel::class.java, modelClass)
                return DashboardViewModel(
                    habitDao = habitDao,
                    recordDao = recordDao,
                    repository = repository,
                    setTodayHabitChecked = useCase,
                    todaySource = flowOf(today),
                    currentDate = { today }
                ) as T
            }
        }
        val context = instrumentation.targetContext
        val uncheckedDescription = context.getString(
            R.string.habit_check_content_description, context.getString(R.string.check_in), habitName
        )
        val checkedDescription = context.getString(
            R.string.habit_check_content_description, context.getString(R.string.completed), habitName
        )
        val errorText = context.getString(R.string.check_in_persist_failed)
        lateinit var activity: ComponentActivity
        lateinit var viewModel: DashboardViewModel
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            lifecycleEvents.add(event)
            if (event == Lifecycle.Event.ON_STOP) stopped.complete(Unit)
            if (event == Lifecycle.Event.ON_START && stopped.isCompleted) resumed.complete(Unit)
        }

        try {
            composeRule.activityRule.scenario.onActivity { current ->
                activity = current
                viewModel = ViewModelProvider(current, factory)[DashboardViewModel::class.java]
                current.lifecycle.addObserver(lifecycleObserver)
                current.setContent {
                    HabitTrackerTheme {
                        DashboardScreen(viewModel, onAddClick = {}, onEditClick = {})
                    }
                }
            }
            composeRule.waitUntil(5_000L) {
                (viewModel.uiState.value as? DashboardUiState.Success)
                    ?.items?.singleOrNull()?.isDoneToday == false
            }
            composeRule.onNodeWithContentDescription(uncheckedDescription).performClick()
            composeRule.waitUntil(5_000L) {
                firstWriteEntered.isCompleted &&
                    (viewModel.uiState.value as? DashboardUiState.Success)
                        ?.items?.singleOrNull()?.isDoneToday == true
            }
            composeRule.onNodeWithContentDescription(checkedDescription).assertIsDisplayed()
            assertEquals(1, applyCalls.get())
            assertEquals(0, repositoryWrites.get())
            assertNull(runBlocking { recordDao.getRecordByDate(habitId, today.toEpochDay()) })
            captureStage("optimistic-before-stop")

            // moveToState 等待真实 ON_STOP；主线程空闲点确保 repeatOnLifecycle 的取消已处理。
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { withTimeout(5_000L) { stopped.await() } }
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
            assertTrue(lifecycleEvents.contains(Lifecycle.Event.ON_STOP))
            assertTrue(firstWriteEntered.isCompleted)
            assertFalse(releaseFirstWrite.isCompleted)
            assertEquals(true, (viewModel.uiState.value as DashboardUiState.Success)
                .items.single().isDoneToday)
            println("C43 STOPPED lifecycle=$lifecycleEvents apply=${applyCalls.get()} room=empty")

            releaseFirstWrite.complete(Unit)
            val failure = runBlocking { withTimeout(5_000L) { reverted.await() } }
            assertEquals(SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED, failure.reason)
            runBlocking {
                withTimeout(5_000L) {
                    viewModel.uiState.first { state ->
                        (state as? DashboardUiState.Success)?.items?.singleOrNull()?.isDoneToday == false
                    }
                }
            }
            instrumentation.waitForIdleSync()
            assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
            assertNull(runBlocking { recordDao.getRecordByDate(habitId, today.toEpochDay()) })
            assertEquals(1, applyCalls.get())
            assertEquals(0, repositoryWrites.get())
            assertEquals(1, domainEvents.count {
                it is SetTodayHabitCheckedUseCase.DomainEvent.Reverted
            })
            // clearOptimisticEvent 成功后紧接着 emitFailureMessage；此空闲点在 RESUMED 前。
            println("C43 FAILED_WHILE_STOPPED lifecycle=$lifecycleEvents event=$failure " +
                "uiChecked=${(viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday} " +
                "apply=${applyCalls.get()} writes=${repositoryWrites.get()}")

            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking { withTimeout(5_000L) { resumed.await() } }
            instrumentation.waitForIdleSync()
            composeRule.waitUntil(5_000L) {
                (viewModel.uiState.value as? DashboardUiState.Success)
                    ?.items?.singleOrNull()?.isDoneToday == false
            }
            composeRule.onNodeWithContentDescription(uncheckedDescription).assertIsDisplayed()
            assertSame(activity, composeRule.activity)
            assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
            assertTrue(composeRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isEmpty())
            captureStage("resumed-no-old-snackbar")
            println("C43 RESUMED_NO_REPLAY lifecycle=$lifecycleEvents snackbarNodes=0")

            // 恢复后的明确新点击应正常持久化；首次失败不得留下 inFlight/pending 阻塞。
            composeRule.onNodeWithContentDescription(uncheckedDescription).performClick()
            composeRule.waitUntil(5_000L) {
                applyCalls.get() == 2 && repositoryWrites.get() == 1 &&
                    domainEvents.any { it is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed } &&
                    (viewModel.uiState.value as? DashboardUiState.Success)
                        ?.items?.singleOrNull()?.isDoneToday == true
            }
            val rows = runBlocking { recordDao.getAllRecords().first() }
            assertEquals(1, rows.count { it.habitId == habitId && it.date == today.toEpochDay() })
            assertEquals(true, rows.single().isDone)
            assertEquals(1, domainEvents.count {
                it is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed
            })
            composeRule.onNodeWithContentDescription(checkedDescription).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isEmpty())
            captureStage("retry-confirmed")
            println("C43 RETRY_CONFIRMED apply=${applyCalls.get()} writes=${repositoryWrites.get()} " +
                "rows=${rows.size} events=$domainEvents")
        } finally {
            releaseFirstWrite.complete(Unit)
            instrumentation.runOnMainSync { activity.lifecycle.removeObserver(lifecycleObserver) }
            eventScope.cancel()
            database.close()
        }
    }

    private fun captureStage(name: String) {
        val directory = File(composeRule.activity.getExternalFilesDir(null), "i18n-c43")
        assertTrue(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
