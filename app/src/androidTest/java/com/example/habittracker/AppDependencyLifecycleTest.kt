package com.example.habittracker

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habittracker.feature.dashboard.DashboardViewModel
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDependencyLifecycleTest {

    @Test
    fun recreate_keepsApplicationContainerAndDashboardViewModel() {
        var firstContainer: AppContainer? = null
        var firstDashboardViewModel: DashboardViewModel? = null

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                firstContainer = activity.appContainer
                firstDashboardViewModel = ViewModelProvider(
                    activity,
                    activity.appContainer.viewModelFactory
                )[DashboardViewModel::class.java]
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertSame(firstContainer, activity.appContainer)
                assertSame(
                    firstDashboardViewModel,
                    ViewModelProvider(
                        activity,
                        activity.appContainer.viewModelFactory
                    )[DashboardViewModel::class.java]
                )
            }
        }
    }
}
