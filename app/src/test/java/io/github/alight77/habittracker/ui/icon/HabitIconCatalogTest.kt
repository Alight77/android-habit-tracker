package io.github.alight77.habittracker.ui.icon

import androidx.compose.ui.graphics.Color
import io.github.alight77.habittracker.domain.icon.IconKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitIconCatalogTest {
    @Test
    fun everyKnownKeyHasExactlyOneCompleteDefinition() {
        val definitions = HabitIconCatalog.knownDefinitions
        assertEquals(IconKey.known.toSet(), definitions.map { it.key }.toSet())
        assertEquals(IconKey.known.size, definitions.size)
        definitions.forEach { definition ->
            assertTrue(definition.drawableRes != 0)
            assertTrue(definition.labelRes != 0)
        }
    }

    @Test
    fun unknownKeyUsesGeneralOnlyForDisplay() {
        val original = IconKey("cycling")
        val resolved = HabitIconCatalog.resolveForDisplay(original)

        assertEquals(original, resolved.originalKey)
        assertEquals(IconKey.GENERAL, resolved.definition.key)
        assertEquals(IconKey.READING, HabitIconCatalog.resolveForDisplay(IconKey.READING).definition.key)
        val previousDraftKey = HabitIconCatalog.resolveForDisplay(IconKey("organize"))
        assertEquals(IconKey("organize"), previousDraftKey.originalKey)
        assertEquals(IconKey.GENERAL, previousDraftKey.definition.key)
    }

    @Test
    fun actionColorsRemainLegibleOnLightAndDarkCards() {
        val lightSurface = Color(0xFFFFFCF7)
        val darkSurface = Color(0xFF202720)
        HabitIconCatalog.knownDefinitions.forEach { definition ->
            listOf(definition.light to lightSurface, definition.dark to darkSurface).forEach { (palette, surface) ->
                assertTrue(definition.key.value, contrast(palette.uncheckedAction, surface) >= 4.5)
                assertTrue(
                    definition.key.value,
                    contrast(palette.onCompletedContainer, palette.completedContainer) >= 4.5
                )
                assertTrue(definition.key.value, palette.cardWash.alpha <= 0.11f)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
