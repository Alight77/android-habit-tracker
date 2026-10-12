package io.github.alight77.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class RapidTapPipelineRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun twoClicksBeforeRecompositionLeaveRoomUnchecked() {
        verifyRapidClicks(false, 2, listOf(true, false), false)
    }

    @Test
    fun threeClicksBeforeRecompositionLeaveRoomChecked() {
        // PR-04 会合并同日期等待命令，因此实际 Room 写入为第一笔与最后一笔。
        verifyRapidClicks(false, 3, listOf(true, true), true)
    }

    @Test
    fun twoClicksFromCompletedStateLeaveRoomChecked() {
        verifyRapidClicks(true, 2, listOf(false, true), true)
    }

    private fun verifyRapidClicks(
        initialChecked: Boolean,
        clickCount: Int,
        expectedWrites: List<Boolean>,
        expectedFinal: Boolean
    ) {
        val database = Room.inMemoryDatabaseBuilder(
            composeRule.activity,
            HabitDatabase::class.java
        ).build()
        val dispatcher = PausingDispatcher()
        try {
            val date = LocalDate.of(2026, 9, 19)
            runBlocking {
                database.habitDao().insertHabit(
                    HabitEntity(name = "阅读", description = "", targetPerWeek = 7, createdAt = 0L)
                )
            }
            val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
            if (initialChecked) {
                runBlocking {
                    database.recordDao().setRecordChecked(habitId, date.toEpochDay(), true)
                }
            }
            val repository = HabitRepository(database.habitDao(), database.recordDao())
            val appliedCommands = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
            val useCase = SetTodayHabitCheckedUseCase(
                applyCommand = { command ->
                    repository.setTodayRecordChecked(
                        command.habitId, command.today.toEpochDay(), command.targetChecked
                    )
                    synchronized(appliedCommands) { appliedCommands += command }
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                },
                observeSourceChecked = { command ->
                    repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
                },
                dispatcher = dispatcher
            )
            val viewModel = DashboardViewModel(
                habitDao = database.habitDao(),
                recordDao = database.recordDao(),
                repository = repository,
                setTodayHabitChecked = useCase,
                todaySource = flowOf(date),
                currentDate = { date }
            )
            composeRule.setContent {
                DashboardScreen(
                    viewModel = viewModel,
                    onAddClick = {},
                    onEditClick = {}
                )
            }
            val checkButtonMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
            composeRule.waitUntil(5_000L) {
                composeRule.onAllNodes(checkButtonMatcher).fetchSemanticsNodes().size == 1
            }
            val checkButton = composeRule.onNode(checkButtonMatcher)
            composeRule.mainClock.autoAdvance = false
            // 固定 Compose 帧，验证同一渲染状态下的每次点击都代表一次切换。
            repeat(clickCount) { checkButton.performClick() }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitUntil(5_000L) { dispatcher.queuedCount >= 2 }
            dispatcher.release()

            // 等待预期的写入数后再读取 Room，避免仅观察到第一笔提交。
            composeRule.waitUntil(5_000L) {
                synchronized(appliedCommands) { appliedCommands.size >= expectedWrites.size }
            }
            composeRule.waitForIdle()
            val finalChecked = runBlocking {
                database.recordDao().getRecordByDate(habitId, date.toEpochDay())?.isDone
            }
            println("PR05 pipeline commands=$appliedCommands finalDatabaseChecked=$finalChecked")
            assertEquals(expectedWrites, synchronized(appliedCommands) {
                appliedCommands.map { it.targetChecked }
            })
            assertEquals(
                "Room must reflect the last accepted click",
                expectedFinal,
                finalChecked
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
            dispatcher.release()
            database.close()
        }
    }

    private class PausingDispatcher : CoroutineDispatcher() {
        private data class Task(val context: CoroutineContext, val runnable: Runnable)
        private val queued = ConcurrentLinkedQueue<Task>()
        @Volatile private var paused = true
        val queuedCount: Int get() = queued.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (paused) queued.add(Task(context, block))
            else Dispatchers.IO.dispatch(context, block)
        }

        fun release() {
            paused = false
            while (true) {
                val task = queued.poll() ?: break
                Dispatchers.IO.dispatch(task.context, task.runnable)
            }
        }
    }
}


