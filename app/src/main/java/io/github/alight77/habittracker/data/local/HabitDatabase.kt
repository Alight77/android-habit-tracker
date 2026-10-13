package io.github.alight77.habittracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        RecordEntity::class
    ],
    version = 5
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun recordDao(): RecordDao

}
