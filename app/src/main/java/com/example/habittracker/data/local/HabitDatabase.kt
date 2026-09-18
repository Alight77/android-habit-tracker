package com.example.habittracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        RecordEntity::class
    ],
    version = 2
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun recordDao(): RecordDao

}