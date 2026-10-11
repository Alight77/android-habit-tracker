package io.github.alight77.habittracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = NaturalGreen,
    onPrimary = Color.White,
    primaryContainer = PaleGreen,
    onPrimaryContainer = DeepGreen,
    secondary = SageGreen,
    onSecondary = Color.White,
    secondaryContainer = WarmCard,
    onSecondaryContainer = Ink,
    tertiary = StampRed,
    onTertiary = Color.White,
    tertiaryContainer = PaleStamp,
    onTertiaryContainer = Ink,
    background = CreamBackground,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = MutedInk,
    outlineVariant = WarmOutline,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Ink,
    inverseOnSurface = PaperSurface,
    inversePrimary = LightGreen,
    surfaceDim = Color(0xFFE9E1D4),
    surfaceBright = PaperSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = CreamBackground,
    surfaceContainerHigh = WarmCard,
    surfaceContainerHighest = Color(0xFFEEE6DA)
)

private val DarkColorScheme = darkColorScheme(
    primary = LightGreen,
    onPrimary = DeepGreen,
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = PaleGreen,
    secondary = LightSage,
    onSecondary = DarkBackground,
    secondaryContainer = DarkStampCard,
    onSecondaryContainer = DarkInk,
    tertiary = StampRed,
    onTertiary = Color.White,
    tertiaryContainer = DarkStampContainer,
    onTertiaryContainer = DarkInk,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkMutedInk,
    outlineVariant = DarkOutline,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = DarkInk,
    inverseOnSurface = DarkBackground,
    inversePrimary = NaturalGreen,
    surfaceDim = DarkBackground,
    surfaceBright = Color(0xFF39453B),
    surfaceContainerLowest = Color(0xFF121813),
    surfaceContainerLow = Color(0xFF1C241E),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF344137)
)

@Composable
fun HabitTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 品牌色固定由明暗主题决定，避免系统动态取色覆盖手帐配色。
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
