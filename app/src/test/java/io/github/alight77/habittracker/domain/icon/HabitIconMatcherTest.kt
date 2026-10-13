package io.github.alight77.habittracker.domain.icon

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitIconMatcherTest {
    @Test
    fun recognizesEachCategoryFromCommonChineseNames() {
        val examples = mapOf(
            "读书 30 分钟" to IconKey.READING,
            "每周跑步" to IconKey.EXERCISE,
            "记得喝水" to IconKey.WATER,
            "早睡" to IconKey.SLEEP,
            "背单词" to IconKey.STUDY,
            "冥想" to IconKey.MINDFULNESS,
            "定期体检" to IconKey.HEALTH,
            "多吃水果" to IconKey.NUTRITION,
            "整理房间" to IconKey.CLEAN
        )

        examples.forEach { (name, key) -> assertEquals(name, key, HabitIconMatcher.match(name)) }
    }

    @Test
    fun recognizesEnglishWordsRegardlessOfCase() {
        assertEquals(IconKey.READING, HabitIconMatcher.match("READING every night"))
        assertEquals(IconKey.EXERCISE, HabitIconMatcher.match("Morning RUNNING"))
        assertEquals(IconKey.CLEAN, HabitIconMatcher.match("Weekly CLEANING"))
    }

    @Test
    fun specificActionWinsOverBroadCategory() {
        assertEquals(IconKey.NUTRITION, HabitIconMatcher.match("健康饮食"))
        assertEquals(IconKey.EXERCISE, HabitIconMatcher.match("健康跑步"))
    }

    @Test
    fun healthyMealWinsOverBroadHealthKeyword() {
        assertEquals(IconKey.NUTRITION, HabitIconMatcher.match("健康餐"))
        assertEquals(IconKey.NUTRITION, HabitIconMatcher.match("每周准备健康餐"))
    }

    @Test
    fun sameStrengthConflictFallsBackToGeneral() {
        assertEquals(IconKey.GENERAL, HabitIconMatcher.match("阅读和跑步"))
        assertEquals(IconKey.GENERAL, HabitIconMatcher.match("READ and RUN"))
    }

    @Test
    fun blankUnknownAndEnglishPartialWordsFallBackToGeneral() {
        listOf("", " \t\n ", "水彩画", "watercolor painting", "readingroom", "明天更好", "整理文件", "Weekly Organise")
            .forEach { name -> assertEquals(name, IconKey.GENERAL, HabitIconMatcher.match(name)) }
    }

    @Test
    fun cleanIncludesCleaningAndHouseholdTidying() {
        listOf("打扫卫生", "清洁厨房", "每周扫地", "整理", "收拾房间", "Sweep the floor")
            .forEach { name -> assertEquals(name, IconKey.CLEAN, HabitIconMatcher.match(name)) }
    }

    @Test
    fun longNameAndRepeatedCallsAreDeterministic() {
        val name = "每天坚持一点点".repeat(200) + "喝水"
        repeat(3) { assertEquals(IconKey.WATER, HabitIconMatcher.match(name)) }
    }
}
