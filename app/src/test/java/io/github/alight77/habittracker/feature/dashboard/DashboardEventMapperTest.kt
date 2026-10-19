package io.github.alight77.habittracker.feature.dashboard

import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DashboardEventMapperTest {

    @Test
    fun dashboardMessageFor_returnsPersistFailureMessageForRollback() {
        val event = SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
            habitId = 1,
            failedTargetChecked = true,
            today = LocalDate.of(2026, 7, 7),
            reason = SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED
        )

        val message = dashboardMessageFor(event)

        assertEquals(R.string.check_in_persist_failed, message)
    }

    @Test
    fun dashboardMessageFor_distinguishesConvergenceTimeoutFromPersistFailure() {
        val event = SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
            habitId = 1,
            failedTargetChecked = true,
            today = LocalDate.of(2026, 7, 7),
            reason = SetTodayHabitCheckedUseCase.RollbackReason.CONVERGENCE_TIMEOUT
        )

        assertEquals(R.string.check_in_convergence_timeout, dashboardMessageFor(event))
    }

    @Test
    fun dashboardMessageFor_returnsConflictMessageForRejectedEvent() {
        val event = SetTodayHabitCheckedUseCase.DomainEvent.Rejected(
            habitId = 1,
            targetChecked = true,
            today = LocalDate.of(2026, 7, 7),
            reason = SetTodayHabitCheckedUseCase.RejectionReason.CONFLICT
        )

        val message = dashboardMessageFor(event)

        assertEquals(R.string.check_in_conflict, message)
    }

    @Test
    fun dashboardMessageFor_mapsMissingHabitRejection() {
        val event = SetTodayHabitCheckedUseCase.DomainEvent.Rejected(
            habitId = 1,
            targetChecked = true,
            today = LocalDate.of(2026, 7, 7),
            reason = SetTodayHabitCheckedUseCase.RejectionReason.HABIT_NOT_FOUND
        )

        assertEquals(R.string.check_in_habit_not_found, dashboardMessageFor(event))
    }

    @Test
    fun dashboardMessageFor_explainsCancelledCheckIn() {
        val event = SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
            habitId = 1,
            failedTargetChecked = true,
            today = LocalDate.of(2026, 7, 7),
            reason = SetTodayHabitCheckedUseCase.RollbackReason.CANCELLED
        )

        assertEquals(R.string.check_in_cancelled, dashboardMessageFor(event))
    }

    @Test
    fun dashboardMessageFor_keepsNormalDomainEventsSilent() {
        val date = LocalDate.of(2026, 7, 7)

        assertNull(dashboardMessageFor(
            SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, date)
        ))
        assertNull(dashboardMessageFor(
            SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, true, date)
        ))
        assertNull(dashboardMessageFor(
            SetTodayHabitCheckedUseCase.DomainEvent.Superseded(1, false, true, date)
        ))
    }
}
