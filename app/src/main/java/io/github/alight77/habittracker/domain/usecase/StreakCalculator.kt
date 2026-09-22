package io.github.alight77.habittracker.domain.usecase

import java.time.LocalDate


//将 streak 计算从 Flow 中抽离为纯函数，保证：
//逻辑可测试
//不依赖 UI
//可复用于未来统计功能（如周/月连续打卡）
fun calculateStreak(dates: List<LocalDate>, today: LocalDate): Int {
    val dateSet = dates.toSet()
    var streak = 0
    var day = today

    while(dateSet.contains(day)) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

fun calculateLongestStreak(dates: List<LocalDate>): Int {
    val sortedDates = dates.distinct().sorted()
    if (sortedDates.isEmpty()) return 0

    var longest = 1
    var current = 1
    for (index in 1 until sortedDates.size) {
        current = if (sortedDates[index] == sortedDates[index - 1].plusDays(1)) {
            current + 1
        } else {
            1
        }
        longest = maxOf(longest, current)
    }
    return longest
}
