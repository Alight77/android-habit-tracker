package io.github.alight77.habittracker.feature

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import io.github.alight77.habittracker.feature.dashboard.DashboardContent
import io.github.alight77.habittracker.feature.dashboard.DashboardUiState
import io.github.alight77.habittracker.feature.stats.StatsContent
import io.github.alight77.habittracker.feature.stats.StatsUiState
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
                onCheckClick = { _ -> },
                onAddClick = {},
                onEditClick = {},
                onDeleteHabit = {},
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
                Box(Modifier.width(320.dp).height(240.dp)) {
                    StatsContent(
                        uiState = StatsUiState.Error("统计数据暂时无法读取，请检查本地数据状态后重试"),
                        onRetry = { retryCount.incrementAndGet() }
                    )
                }
            }
        }

        composeRule.onNodeWithText("重试").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retryCount.get())
    }
}
