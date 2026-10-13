package io.github.alight77.habittracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    val description: String,

    val targetPerWeek: Int,

    val createdAt: Long,

    // 保留数据库中的原始 Key；未知值只在展示层回退，不在读取时改写。
    @ColumnInfo(defaultValue = "'general'")
    val iconKey: String = "general"

)
