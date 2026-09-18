package com.example.habittracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.repository.HabitRepository
import kotlinx.coroutines.launch

class HabitViewModel(
    private val repository: HabitRepository
) : ViewModel() {

    fun addHabit(name: String) {
        viewModelScope.launch {
            repository.addHabit(
                HabitEntity(
                    name = name,
                    description = "",
                    targetPerWeek = 7,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}