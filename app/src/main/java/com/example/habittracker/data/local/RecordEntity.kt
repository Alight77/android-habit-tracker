package com.example.habittracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.util.TableInfo

@Entity(tableName = "records", indices = [Index(value = ["habitId", "date"], unique = true)])
data class RecordEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val habitId: Int,

    val date: Long,

    val isDone: Boolean,
)