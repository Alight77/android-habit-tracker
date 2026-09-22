package com.example.habittracker

import android.app.Application

class HabitTrackerApplication : Application() {

    val appContainer: AppContainer by lazy {
        AppContainer(applicationContext)
    }
}
