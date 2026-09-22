package io.github.alight77.habittracker.feature.dashboard

import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase

sealed interface DashboardUiEvent {
    data class ShowMessage(val message: String) : DashboardUiEvent
}

internal fun dashboardMessageFor(event: SetTodayHabitCheckedUseCase.DomainEvent): String? {
    return when (event) {
        is SetTodayHabitCheckedUseCase.DomainEvent.Reverted -> when (event.reason) {
            SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED -> "打卡保存失败，请稍后重试"
            SetTodayHabitCheckedUseCase.RollbackReason.CONVERGENCE_TIMEOUT -> "打卡状态同步超时，请重试"
        }

        is SetTodayHabitCheckedUseCase.DomainEvent.Rejected -> when (event.reason) {
            SetTodayHabitCheckedUseCase.RejectionReason.HABIT_NOT_FOUND -> "习惯不存在或已删除"
            SetTodayHabitCheckedUseCase.RejectionReason.CONFLICT -> "打卡状态冲突，请重试"
        }

        else -> null
    }
}