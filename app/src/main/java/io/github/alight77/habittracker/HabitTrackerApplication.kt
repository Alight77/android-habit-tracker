package io.github.alight77.habittracker

import android.app.Application

class HabitTrackerApplication : Application() {

    val appContainer: AppContainer by lazy {
        AppContainer(applicationContext)
    }
}
