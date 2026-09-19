package com.example.habittracker.feature

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habittracker.feature.dashboard.DashboardContent
import com.example.habittracker.feature.dashboard.DashboardUiState
import com.example.habittracker.feature.stats.StatsContent
import com.example.habittracker.feature.stats.StatsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ErrorContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dashboardErrorContent_clickRetryInvokesCallback() {
        val retryCount = AtomicInteger(0)
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Error("加载习惯数据失败，请重试"),
                onCheckClick = { _, _ -> },
                onAddClick = {},
                onStatsClick = {},
                onRetry = { retryCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithText("重试").performClick()

        assertEquals(1, retryCount.get())
    }

    @Test
    fun statsErrorContent_clickRetryInvokesCallback() {
        val retryCount = AtomicInteger(0)
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Error("加载统计数据失败，请重试"),
                onBackClick = {},
                onRetry = { retryCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithText("重试").performClick()

        assertEquals(1, retryCount.get())
    }
}
