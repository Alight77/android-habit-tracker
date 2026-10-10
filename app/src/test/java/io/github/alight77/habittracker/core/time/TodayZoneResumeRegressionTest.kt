package io.github.alight77.habittracker.core.time

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class TodayZoneResumeRegressionTest {
    @Test
    fun reopeningTodaySourceAfterWestboundZoneChangeUsesNewLocalDate() = runBlocking {
        assertZoneChange("Pacific/Kiritimati", "Pacific/Honolulu")
    }

    @Test
    fun reopeningTodaySourceAfterEastboundZoneChangeUsesNewLocalDate() = runBlocking {
        assertZoneChange("Pacific/Honolulu", "Pacific/Kiritimati")
    }

    private suspend fun assertZoneChange(startZoneName: String, resumedZoneName: String) {
        val original = TimeZone.getDefault()
        val startZone = ZoneId.of(startZoneName)
        val resumedZone = ZoneId.of(resumedZoneName)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(startZone))
            val sourceCreatedBeforePause = systemTodayFlow()
            val before = sourceCreatedBeforePause.first()
            assertEquals(LocalDate.now(startZone), before)

            TimeZone.setDefault(TimeZone.getTimeZone(resumedZone))
            val afterResubscribe = sourceCreatedBeforePause.first()
            val expected = LocalDate.now(resumedZone)
            println("PR03 initialZone=$startZoneName initial=$before resumedZone=$resumedZoneName expected=$expected actual=$afterResubscribe")
            assertEquals("Resubscribing after a zone change should use the current local date", expected, afterResubscribe)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
