package com.example.habittracker.feature.dashboard

import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import org.junit.Assert.assertEquals
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

        assertEquals("打卡保存失败，请稍后重试", message)
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

        assertEquals("打卡状态冲突，请重试", message)
    }
}