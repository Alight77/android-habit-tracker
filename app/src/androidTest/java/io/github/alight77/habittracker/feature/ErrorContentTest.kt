package io.github.alight77.habittracker.feature

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.feature.dashboard.DashboardContent
import io.github.alight77.habittracker.feature.dashboard.DashboardUiState
import io.github.alight77.habittracker.feature.stats.StatsContent
import io.github.alight77.habittracker.feature.stats.StatsUiState
import io.github.alight77.habittracker.feature.stats.ErrorStats
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
                uiState = DashboardUiState.Error(R.string.dashboard_load_failed),
                onCheckClick = { _ -> },
                onAddClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = { retryCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithText("加载习惯数据失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()

        assertEquals(1, retryCount.get())
    }

    @Test
    fun statsErrorContent_clickRetryInvokesCallback() {
        val retryCount = AtomicInteger(0)
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Error(R.string.stats_load_failed),
                onRetry = { retryCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithText("重试").performClick()

        assertEquals(1, retryCount.get())
    }

    @Test
    fun statsErrorRetryRemainsReachableOnShortNarrowScreenAtDoubleFont() {
        val retryCount = AtomicInteger(0)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Column(Modifier.width(320.dp).height(240.dp).verticalScroll(rememberScrollState())) {
                    ErrorStats(
                        message = "统计数据暂时无法读取，请检查本地数据状态后重试",
                        onRetry = { retryCount.incrementAndGet() },
                        modifier = Modifier.heightIn(min = 240.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithText("重试").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retryCount.get())
    }
}
