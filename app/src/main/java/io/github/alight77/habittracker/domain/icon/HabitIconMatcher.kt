package io.github.alight77.habittracker.domain.icon

import java.util.Locale

/** 离线名称联想；只使用名称字符串，不依赖 Android 资源或当前 UI 状态。 */
object HabitIconMatcher {
    private data class Rule(
        val key: IconKey,
        val specificChinese: List<String>,
        val exactChinese: Set<String> = emptySet(),
        val broadChinese: List<String> = emptyList(),
        val specificEnglish: Set<String> = emptySet(),
        val broadEnglish: Set<String> = emptySet()
    ) {
        fun score(name: String, englishWords: Set<String>): Int = when {
            name in exactChinese || specificChinese.any(name::contains) || specificEnglish.any(englishWords::contains) -> 2
            broadChinese.any(name::contains) || broadEnglish.any(englishWords::contains) -> 1
            else -> 0
        }
    }

    private val englishWord = Regex("[a-z]+")

    // 具体动作胜过宽泛类别词；同分命中多个类别时保守回退通用图标。
    private val rules = listOf(
        Rule(
            IconKey.READING,
            specificChinese = listOf("阅读", "读书", "看书", "读小说", "读报"),
            specificEnglish = setOf("read", "reading", "books", "novel")
        ),
        Rule(
            IconKey.EXERCISE,
            specificChinese = listOf("跑步", "晨跑", "夜跑", "健身", "锻炼", "游泳", "骑行", "散步", "瑜伽"),
            broadChinese = listOf("运动"),
            specificEnglish = setOf("run", "running", "jog", "jogging", "workout", "exercise", "swim", "cycling"),
            broadEnglish = setOf("fitness")
        ),
        Rule(
            IconKey.WATER,
            specificChinese = listOf("喝水", "补水", "饮水"),
            specificEnglish = setOf("water", "hydrate", "hydration")
        ),
        Rule(
            IconKey.SLEEP,
            specificChinese = listOf("早睡", "睡觉", "睡眠", "午睡", "入睡"),
            broadChinese = listOf("作息"),
            specificEnglish = setOf("sleep", "sleeping", "nap", "bedtime")
        ),
        Rule(
            IconKey.STUDY,
            specificChinese = listOf("学习", "复习", "背单词", "刷题", "备考", "写作业", "练字"),
            broadChinese = listOf("考试", "课程"),
            specificEnglish = setOf("study", "studying", "homework", "revision", "exam")
        ),
        Rule(
            IconKey.MINDFULNESS,
            specificChinese = listOf("冥想", "正念", "静坐", "打坐", "呼吸练习"),
            broadChinese = listOf("放松"),
            specificEnglish = setOf("meditate", "meditation", "mindfulness"),
            broadEnglish = setOf("relax")
        ),
        Rule(
            IconKey.HEALTH,
            specificChinese = listOf("体检", "吃药", "服药", "测血压", "量血压", "康复"),
            broadChinese = listOf("健康", "保健"),
            specificEnglish = setOf("medicine", "medication", "checkup", "recovery"),
            broadEnglish = setOf("health", "healthy")
        ),
        Rule(
            IconKey.NUTRITION,
            specificChinese = listOf("吃水果", "吃蔬菜", "饮食", "营养", "做饭", "少糖", "少盐", "健康餐"),
            specificEnglish = setOf("nutrition", "diet", "fruit", "vegetables", "meal")
        ),
        Rule(
            IconKey.CLEAN,
            specificChinese = listOf("打扫", "清洁", "扫地", "拖地", "擦地", "擦窗", "擦桌", "洗衣", "整理房间", "整理衣柜", "收拾房间"),
            exactChinese = setOf("整理", "收拾"),
            broadChinese = listOf("家务"),
            specificEnglish = setOf("clean", "cleaning", "sweep", "sweeping", "mop", "mopping", "tidy", "tidying")
        )
    )

    fun match(name: String): IconKey {
        val normalized = name.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return IconKey.GENERAL

        val englishWords = englishWord.findAll(normalized).mapTo(mutableSetOf()) { it.value }
        val scored = rules.mapNotNull { rule ->
            rule.score(normalized, englishWords).takeIf { it > 0 }?.let { rule.key to it }
        }
        val highestScore = scored.maxOfOrNull { it.second } ?: return IconKey.GENERAL
        val strongest = scored.filter { it.second == highestScore }
        return strongest.singleOrNull()?.first ?: IconKey.GENERAL
    }
}
