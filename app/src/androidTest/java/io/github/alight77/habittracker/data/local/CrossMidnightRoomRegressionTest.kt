package io.github.alight77.habittracker.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 与 JVM 时序相同，但实际通过 Room 事务读写两个日期的记录。 */
@RunWith(AndroidJUnit4::class)
class CrossMidnightRoomRegressionTest {
    @Test
    fun threeCommandsAcrossMidnightLeaveYesterdayUncheckedInRoom() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        try {
            database.habitDao().insertHabit(
                HabitEntity(name = "阅读", description = "", targetPerWeek = 7, createdAt = 0L)
            )
            val habitId = database.habitDao().getAllHabits().first().single().id
            val yesterday = LocalDate.of(2026, 9, 19)
            val today = yesterday.plusDays(1)
            val firstCommand = SetTodayHabitCheckedUseCase.Command(habitId, true, yesterday)
            val secondCommand = SetTodayHabitCheckedUseCase.Command(habitId, false, yesterday)
            val thirdCommand = SetTodayHabitCheckedUseCase.Command(habitId, true, today)
            val firstPersistStarted = CompletableDeferred<Unit>()
            val releaseFirstPersist = CompletableDeferred<Unit>()
            val repository = HabitRepository(database.habitDao(), database.recordDao())
            val applied = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
            val useCase = SetTodayHabitCheckedUseCase(
                applyCommand = { command ->
                    if (command == firstCommand) {
                        firstPersistStarted.complete(Unit)
                        releaseFirstPersist.await()
                    }
                    applied += command
                    repository.setTodayRecordChecked(
                        command.habitId, command.today.toEpochDay(), command.targetChecked
                    )
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                },
                observeSourceChecked = { command ->
                    repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
                },
                dispatcher = Dispatchers.Unconfined
            )
            val events = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000L) { useCase.events.take(5).toList() }
            }
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                useCase(firstCommand)
            }
            firstPersistStarted.await()
            useCase(secondCommand)
            useCase(thirdCommand)
            releaseFirstPersist.complete(Unit)
            first.await()
            val observedEvents = events.await()
            val rows = database.recordDao().getAllRecords().first()
                .associate { LocalDate.ofEpochDay(it.date) to it.isDone }
            println("PR04_ROOM applied=$applied rows=$rows events=$observedEvents")
            assertEquals(
                "The cancelled previous-day check-in must not survive",
                mapOf(yesterday to false, today to true),
                rows
            )
            assertEquals(listOf(firstCommand, secondCommand, thirdCommand), applied)
            assertEquals(
                listOf(
                    SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(habitId, true, yesterday),
                    SetTodayHabitCheckedUseCase.DomainEvent.Superseded(habitId, true, false, yesterday),
                    SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(habitId, true, today),
                    SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(habitId, false, yesterday),
                    SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(habitId, true, today)
                ),
                observedEvents
            )
        } finally {
            database.close()
        }
    }
}
