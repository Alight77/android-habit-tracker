package io.github.alight77.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.core.time.TodayDateSource
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class TodayForegroundResumeRegressionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun foregroundResumeBeforeSharingStopsUsesCurrentDate() = verifyResumeAfterZoneChange(1_000L)

    @Test
    fun foregroundResumeAfterSharingStopsUsesCurrentDate() = verifyResumeAfterZoneChange(6_000L)

    private fun verifyResumeAfterZoneChange(backgroundMillis: Long) {
        val originalZone = TimeZone.getDefault()
        val beforeZone = ZoneId.of("Pacific/Kiritimati")
        val afterZone = ZoneId.of("Pacific/Honolulu")
        val database = Room.inMemoryDatabaseBuilder(
            composeRule.activity, HabitDatabase::class.java
        ).build()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(beforeZone))
            val beforeDate = LocalDate.now(beforeZone)
            val afterDate = LocalDate.now(afterZone)
            assertNotEquals(beforeDate, afterDate)
            runBlocking {
                database.habitDao().insertHabit(
                    HabitEntity(name = "阅读", description = "", targetPerWeek = 7, createdAt = 0L)
                )
            }
            val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
            runBlocking {
                database.recordDao().setRecordChecked(habitId, afterDate.toEpochDay(), true)
            }
            val repository = HabitRepository(database.habitDao(), database.recordDao())
            val useCase = SetTodayHabitCheckedUseCase(
                applyCommand = { command ->
                    repository.setTodayRecordChecked(
                        command.habitId, command.today.toEpochDay(), command.targetChecked
                    )
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                },
                observeSourceChecked = { command ->
                    repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
                }
            )
            val dateSource = TodayDateSource(composeRule.activity)
            val viewModel = DashboardViewModel(
                database.habitDao(), database.recordDao(), repository, useCase,
                todaySource = dateSource.dates,
                currentDate = dateSource::today,
                requestTodayRefresh = dateSource::refresh
            )
            composeRule.setContent {
                DashboardScreen(viewModel, onAddClick = {}, onEditClick = {})
            }
            composeRule.waitUntil(5_000L) {
                (viewModel.uiState.value as? DashboardUiState.Success)
                    ?.items?.singleOrNull()?.isDoneToday == false
            }

            TimeZone.setDefault(TimeZone.getTimeZone(afterZone))
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(backgroundMillis)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(5_000L) {
                (viewModel.uiState.value as? DashboardUiState.Success)
                    ?.items?.singleOrNull()?.isDoneToday == true
            }
            val checkedAfterResume =
                (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday

            // 恢复后卡片代表当前日期，点击应只取消当天记录。
            composeRule.onNodeWithContentDescription("已完成：阅读").performClick()
            composeRule.waitUntil(5_000L) {
                runBlocking {
                    database.recordDao().getRecordByDate(habitId, afterDate.toEpochDay())?.isDone == false
                }
            }
            val rows = runBlocking {
                database.recordDao().getAllRecords().first()
                    .map { it.date to it.isDone }
            }
            println("PR03 beforeZone=$beforeZone beforeDate=$beforeDate afterZone=$afterZone afterDate=$afterDate checkedAfterResume=$checkedAfterResume rows=$rows")
            assertEquals("Today should follow the resumed time zone", true, checkedAfterResume)
            assertEquals(
                "A click after resume must not write the old-zone date",
                null,
                runBlocking {
                    database.recordDao().getRecordByDate(habitId, beforeDate.toEpochDay())
                }
            )
            assertEquals(
                false,
                runBlocking {
                    database.recordDao().getRecordByDate(habitId, afterDate.toEpochDay())?.isDone
                }
            )
        } finally {
            TimeZone.setDefault(originalZone)
            database.close()
        }
    }
}
