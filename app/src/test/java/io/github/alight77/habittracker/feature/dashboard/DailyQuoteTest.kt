package io.github.alight77.habittracker.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class DailyQuoteTest {
    private val quoteCount = 18

    @Test
    fun sameDateAlwaysSelectsSameQuoteAfterRecreatingDate() {
        val date = LocalDate.of(2026, 10, 11)

        assertEquals(
            dailyQuoteIndex(date, quoteCount),
            dailyQuoteIndex(LocalDate.parse("2026-10-11"), quoteCount)
        )
    }

    @Test
    fun adjacentDaysAdvanceAndCycleAfterAllQuotes() {
        val date = LocalDate.of(2026, 10, 11)
        val index = dailyQuoteIndex(date, quoteCount)

        assertEquals((index + 1) % quoteCount, dailyQuoteIndex(date.plusDays(1), quoteCount))
        assertNotEquals(index, dailyQuoteIndex(date.plusDays(1), quoteCount))
        assertEquals(index, dailyQuoteIndex(date.plusDays(quoteCount.toLong()), quoteCount))
    }

    @Test
    fun datesBeforeEpochAlsoMapToValidQuote() {
        assertEquals(quoteCount - 1, dailyQuoteIndex(LocalDate.of(1969, 12, 31), quoteCount))
    }

    @Test
    fun narrowLargeTextBalancesLongSecondClauseWithoutChangingWords() {
        val quote = "每一次认真，\n都在为明天积蓄力量。"
        val balanced = balanceQuoteForNarrowLargeText(quote)

        assertEquals("每一次认真，\n都在为明天\n积蓄力量。", balanced)
        assertEquals(quote.replace("\n", ""), balanced.replace("\n", ""))
        assertEquals(
            "微小的坚持，\n也在慢慢生根。",
            balanceQuoteForNarrowLargeText("微小的坚持，\n也在慢慢生根。")
        )
    }
}
