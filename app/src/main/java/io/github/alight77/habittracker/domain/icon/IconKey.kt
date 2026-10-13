package io.github.alight77.habittracker.domain.icon

/** 稳定业务标识；允许保留当前版本尚不认识的原始值。 */
@JvmInline
value class IconKey(val value: String) {
    companion object {
        val GENERAL = IconKey("general")
        val READING = IconKey("reading")
        val EXERCISE = IconKey("exercise")
        val WATER = IconKey("water")
        val SLEEP = IconKey("sleep")
        val STUDY = IconKey("study")
        val MINDFULNESS = IconKey("mindfulness")
        val HEALTH = IconKey("health")
        val NUTRITION = IconKey("nutrition")
        val CLEAN = IconKey("clean")

        val known: List<IconKey> = listOf(
            GENERAL, READING, EXERCISE, WATER, SLEEP,
            STUDY, MINDFULNESS, HEALTH, NUTRITION, CLEAN
        )
    }
}
