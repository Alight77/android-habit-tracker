package io.github.alight77.habittracker.feature.dashboard

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardActivityRecreationInFlightTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: HabitDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun suspendedWriteCompletesOnceAfterActivityRecreation() {
        verifyInFlightRecreation(failFirstWrite = false)
    }

    @Test
    fun failedWriteRollsBackAfterActivityRecreationAndRetrySucceeds() {
        verifyInFlightRecreation(failFirstWrite = true)
    }

    private fun verifyInFlightRecreation(failFirstWrite: Boolean) {
        val caseName = if (failFirstWrite) "failure" else "success"
        val habitName = "C42 $caseName"
        val today = LocalDate.now()
        val habitDao = database.habitDao()
        val recordDao = database.recordDao()
        runBlocking {
            habitDao.insertHabit(
                HabitEntity(
                    name = habitName,
                    description = "",
                    targetPerWeek = 3,
                    createdAt = 0L
                )
            )
        }
        val habitId = runBlocking { habitDao.getAllHabits().first().single().id }
        val repository = HabitRepository(habitDao, recordDao)
        val firstWriteEntered = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val applyCalls = AtomicInteger()
        val repositoryWrites = AtomicInteger()
        val viewModelCreations = AtomicInteger()
        val domainEvents = CopyOnWriteArrayList<SetTodayHabitCheckedUseCase.DomainEvent>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                val call = applyCalls.incrementAndGet()
                if (call == 1) {
                    firstWriteEntered.complete(Unit)
                    releaseFirstWrite.await()
                    if (failFirstWrite) error("controlled write failure")
                }
                repository.setTodayRecordChecked(
                    command.habitId,
                    command.today.toEpochDay(),
                    command.targetChecked
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
            useCase.events.collect { event ->
                domainEvents.add(event)
            }
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                assertEquals(DashboardViewModel::class.java, modelClass)
                viewModelCreations.incrementAndGet()
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uncheckedDescription = context.getString(
            R.string.habit_check_content_description,
            context.getString(R.string.check_in),
            habitName
        )
        val checkedDescription = context.getString(
            R.string.habit_check_content_description,
            context.getString(R.string.completed),
            habitName
        )

        fun attachToCurrentActivity(): Pair<ComponentActivity, DashboardViewModel> {
            lateinit var activity: ComponentActivity
            lateinit var viewModel: DashboardViewModel
            composeRule.activityRule.scenario.onActivity { current ->
                activity = current
                viewModel = ViewModelProvider(current, factory)[DashboardViewModel::class.java]
                current.setContent {
                    HabitTrackerTheme {
                        DashboardScreen(viewModel, onAddClick = {}, onEditClick = {})
                    }
                }
            }
            return activity to viewModel
        }

        try {
            val (firstActivity, firstViewModel) = attachToCurrentActivity()
            composeRule.waitUntil(5_000L) {
                composeRule.onAllNodesWithContentDescription(uncheckedDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(uncheckedDescription).performClick()
            composeRule.waitUntil(5_000L) {
                firstWriteEntered.isCompleted &&
                    composeRule.onAllNodesWithContentDescription(checkedDescription)
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(checkedDescription).assertIsDisplayed()
            assertNull(runBlocking { recordDao.getRecordByDate(habitId, today.toEpochDay()) })
            assertEquals(1, applyCalls.get())
            assertEquals(0, repositoryWrites.get())
            captureStage("$caseName-pending-before")

            // 真实 ActivityScenario 重建；写入闸门仍关闭，旧 ViewModel 必须留在 Activity ViewModelStore。
            composeRule.activityRule.scenario.recreate()
            val (secondActivity, secondViewModel) = attachToCurrentActivity()
            assertNotSame(firstActivity, secondActivity)
            assertSame(firstViewModel, secondViewModel)
            assertEquals(1, viewModelCreations.get())
            assertEquals(Lifecycle.State.RESUMED, secondActivity.lifecycle.currentState)
            composeRule.waitUntil(5_000L) {
                composeRule.onAllNodesWithContentDescription(checkedDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription(checkedDescription).assertIsDisplayed()
            assertNull(runBlocking { recordDao.getRecordByDate(habitId, today.toEpochDay()) })
            assertEquals(1, applyCalls.get())
            assertEquals(0, repositoryWrites.get())
            captureStage("$caseName-pending-after")

            releaseFirstWrite.complete(Unit)
            if (failFirstWrite) {
                verifyFailureAndRetry(
                    firstViewModel, habitId, today, uncheckedDescription,
                    checkedDescription, domainEvents, applyCalls, repositoryWrites
                )
            } else {
                composeRule.waitUntil(5_000L) {
                    domainEvents.any { it is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed } &&
                        currentItem(firstViewModel)?.isDoneToday == true
                }
                assertEquals(1, applyCalls.get())
                assertEquals(1, repositoryWrites.get())
                assertEquals(1, domainEvents.count {
                    it is SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied
                })
                assertEquals(1, domainEvents.count {
                    it is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed
                })
                assertFalse(domainEvents.any { it is SetTodayHabitCheckedUseCase.DomainEvent.Reverted })
                assertFinalChecked(firstViewModel, habitId, today, checkedDescription)
                val errorText = context.getString(R.string.check_in_persist_failed)
                assertTrue(composeRule.onAllNodesWithContentDescription(uncheckedDescription)
                    .fetchSemanticsNodes().isEmpty())
                assertTrue(composeRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isEmpty())
                captureStage("success-final")
            }
            println(
                "C42 $caseName firstActivity=${System.identityHashCode(firstActivity)} " +
                    "secondActivity=${System.identityHashCode(secondActivity)} " +
                    "viewModel=${System.identityHashCode(firstViewModel)} " +
                    "applyCalls=${applyCalls.get()} repositoryWrites=${repositoryWrites.get()} " +
                    "events=$domainEvents"
            )
        } finally {
            releaseFirstWrite.complete(Unit)
            eventScope.cancel()
        }
    }

    private fun verifyFailureAndRetry(
        viewModel: DashboardViewModel,
        habitId: Int,
        today: LocalDate,
        uncheckedDescription: String,
        checkedDescription: String,
        domainEvents: List<SetTodayHabitCheckedUseCase.DomainEvent>,
        applyCalls: AtomicInteger,
        repositoryWrites: AtomicInteger
    ) {
        val errorText = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.check_in_persist_failed)
        composeRule.waitUntil(5_000L) {
            domainEvents.any {
                it is SetTodayHabitCheckedUseCase.DomainEvent.Reverted &&
                    it.reason == SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED
            } && currentItem(viewModel)?.isDoneToday == false
        }
        composeRule.onNodeWithContentDescription(uncheckedDescription).assertIsDisplayed()
        composeRule.onNodeWithText(errorText).assertIsDisplayed()
        assertNull(runBlocking { database.recordDao().getRecordByDate(habitId, today.toEpochDay()) })
        assertEquals(1, applyCalls.get())
        assertEquals(0, repositoryWrites.get())
        assertEquals(1, domainEvents.count { it is SetTodayHabitCheckedUseCase.DomainEvent.Reverted })
        captureStage("failure-rolled-back")

        // 第二次点击是明确的新用户意图，用它验证首次失败没有留下阻塞后续写入的状态。
        composeRule.onNodeWithContentDescription(uncheckedDescription).performClick()
        composeRule.waitUntil(5_000L) {
            applyCalls.get() == 2 && repositoryWrites.get() == 1 &&
                domainEvents.any { it is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed } &&
                currentItem(viewModel)?.isDoneToday == true
        }
        assertEquals(2, domainEvents.count {
            it is SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied
        })
        assertEquals(1, domainEvents.count { it is SetTodayHabitCheckedUseCase.DomainEvent.Reverted })
        assertEquals(1, domainEvents.count { it is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed })
        assertFinalChecked(viewModel, habitId, today, checkedDescription)
        captureStage("failure-retry-final")
    }

    private fun assertFinalChecked(
        viewModel: DashboardViewModel,
        habitId: Int,
        today: LocalDate,
        checkedDescription: String
    ) {
        val rows = runBlocking { database.recordDao().getAllRecords().first() }
        assertEquals(1, rows.count { it.habitId == habitId && it.date == today.toEpochDay() })
        assertEquals(true, rows.single().isDone)
        val success = viewModel.uiState.value as DashboardUiState.Success
        assertEquals(today, success.today)
        assertEquals(1, success.items.count { it.isDoneToday })
        assertEquals(1, success.items.single().streak)
        assertEquals(today, success.items.single().recentDays.last().date)
        assertEquals(RecentDayStatus.CHECKED, success.items.single().recentDays.last().status)
        composeRule.onNodeWithContentDescription(checkedDescription).assertIsDisplayed()
    }

    private fun currentItem(viewModel: DashboardViewModel): HabitItemUiState? =
        (viewModel.uiState.value as? DashboardUiState.Success)?.items?.singleOrNull()

    private fun captureStage(name: String) {
        val directory = File(composeRule.activity.getExternalFilesDir(null), "i18n-c42")
        assertTrue(directory.exists() || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
