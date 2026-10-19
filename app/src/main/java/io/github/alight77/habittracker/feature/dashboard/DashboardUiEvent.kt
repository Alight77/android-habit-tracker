package io.github.alight77.habittracker.feature.dashboard

import androidx.annotation.StringRes
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase

sealed interface DashboardUiEvent {
    data class ShowResourceMessage(@param:StringRes val messageResId: Int) : DashboardUiEvent
}

internal fun dashboardMessageFor(event: SetTodayHabitCheckedUseCase.DomainEvent): Int? {
    return when (event) {
        is SetTodayHabitCheckedUseCase.DomainEvent.Reverted -> when (event.reason) {
            SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED -> R.string.check_in_persist_failed
            SetTodayHabitCheckedUseCase.RollbackReason.CONVERGENCE_TIMEOUT -> R.string.check_in_convergence_timeout
            SetTodayHabitCheckedUseCase.RollbackReason.CANCELLED -> R.string.check_in_cancelled
        }

        is SetTodayHabitCheckedUseCase.DomainEvent.Rejected -> when (event.reason) {
            SetTodayHabitCheckedUseCase.RejectionReason.HABIT_NOT_FOUND -> R.string.check_in_habit_not_found
            SetTodayHabitCheckedUseCase.RejectionReason.CONFLICT -> R.string.check_in_conflict
        }

        else -> null
    }
}
