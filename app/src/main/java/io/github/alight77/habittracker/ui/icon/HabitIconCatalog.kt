package io.github.alight77.habittracker.ui.icon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.icon.IconKey

/** 卡片局部配色；删除、错误、未打卡和创建前状态由使用方保留独立语义。 */
data class HabitIconPalette(
    val iconContainer: Color,
    val cardWash: Color,
    val checkedDay: Color,
    val uncheckedAction: Color,
    val completedContainer: Color,
    val onCompletedContainer: Color
)

data class HabitIconDefinition(
    val key: IconKey,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int,
    val light: HabitIconPalette,
    val dark: HabitIconPalette
) {
    fun palette(isDark: Boolean): HabitIconPalette = if (isDark) dark else light
}

/** 把展示回退与原始 Key 分开，避免未来版本的 Key 因当前资源缺失而被覆盖。 */
data class ResolvedHabitIcon(
    val originalKey: IconKey,
    val definition: HabitIconDefinition
)

object HabitIconCatalog {
    private val darkInk = Color(0xFF171D19)

    private fun light(accent: Color, iconContainer: Color) = HabitIconPalette(
        iconContainer = iconContainer,
        cardWash = accent.copy(alpha = 0.08f),
        checkedDay = accent,
        uncheckedAction = accent,
        completedContainer = accent,
        onCompletedContainer = Color.White
    )

    private fun dark(accent: Color, iconContainer: Color) = HabitIconPalette(
        iconContainer = iconContainer,
        cardWash = accent.copy(alpha = 0.10f),
        checkedDay = accent,
        uncheckedAction = accent,
        completedContainer = accent,
        onCompletedContainer = darkInk
    )

    val knownDefinitions: List<HabitIconDefinition> = listOf(
        HabitIconDefinition(
            IconKey.GENERAL, R.drawable.habit_icon_general, R.string.habit_icon_general,
            light(Color(0xFF2E7D32), Color(0xFFD8EBDD)),
            dark(Color(0xFFA9D6A4), Color(0xFF244B2E))
        ),
        HabitIconDefinition(
            IconKey.READING, R.drawable.habit_icon_reading, R.string.habit_icon_reading,
            light(Color(0xFF256E68), Color(0xFFDCECE7)),
            dark(Color(0xFF9CCDC3), Color(0xFF254640))
        ),
        HabitIconDefinition(
            IconKey.EXERCISE, R.drawable.habit_icon_exercise, R.string.habit_icon_exercise,
            light(Color(0xFFA75032), Color(0xFFF4E4D8)),
            dark(Color(0xFFF1B596), Color(0xFF52382D))
        ),
        HabitIconDefinition(
            IconKey.WATER, R.drawable.habit_icon_water, R.string.habit_icon_water,
            light(Color(0xFF166A9C), Color(0xFFDCEEF7)),
            dark(Color(0xFF9BCBE5), Color(0xFF234052))
        ),
        HabitIconDefinition(
            IconKey.SLEEP, R.drawable.habit_icon_sleep, R.string.habit_icon_sleep,
            light(Color(0xFF67589B), Color(0xFFEBE4F4)),
            dark(Color(0xFFC5B5E4), Color(0xFF383149))
        ),
        HabitIconDefinition(
            IconKey.STUDY, R.drawable.habit_icon_study, R.string.habit_icon_study,
            light(Color(0xFF355D91), Color(0xFFE0E9F6)),
            dark(Color(0xFFAFC6EA), Color(0xFF283A52))
        ),
        HabitIconDefinition(
            IconKey.MINDFULNESS, R.drawable.habit_icon_mindfulness, R.string.habit_icon_mindfulness,
            light(Color(0xFF715C96), Color(0xFFEEE5F4)),
            dark(Color(0xFFCFB8E9), Color(0xFF41354D))
        ),
        HabitIconDefinition(
            IconKey.HEALTH, R.drawable.habit_icon_health, R.string.habit_icon_health,
            light(Color(0xFF247A70), Color(0xFFDDEFEA)),
            dark(Color(0xFF9ED5CC), Color(0xFF264740))
        ),
        HabitIconDefinition(
            IconKey.NUTRITION, R.drawable.habit_icon_nutrition, R.string.habit_icon_nutrition,
            light(Color(0xFFA35A25), Color(0xFFF5E7D6)),
            dark(Color(0xFFF0C196), Color(0xFF513B2A))
        ),
        HabitIconDefinition(
            IconKey.CLEAN, R.drawable.habit_icon_clean, R.string.habit_icon_clean,
            light(Color(0xFF775D32), Color(0xFFEDE8D8)),
            dark(Color(0xFFDBC494), Color(0xFF453D2C))
        )
    )

    private val byKey = knownDefinitions.associateBy(HabitIconDefinition::key)

    init {
        check(byKey.keys == IconKey.known.toSet()) { "图标目录与已知 IconKey 不一致" }
    }

    fun resolveForDisplay(originalKey: IconKey): ResolvedHabitIcon = ResolvedHabitIcon(
        originalKey = originalKey,
        definition = byKey[originalKey] ?: requireNotNull(byKey[IconKey.GENERAL])
    )
}
