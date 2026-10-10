package io.github.alight77.habittracker.core.time

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Collections

/** 仅在模拟器传入 expectedDate；看到 READY 后由宿主机调整系统时间或时区。 */
@RunWith(AndroidJUnit4::class)
class TodaySystemChangeIntegrationTest {
    @Test
    fun systemBroadcastRefreshesToday() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expectedArgument = InstrumentationRegistry.getArguments().getString("expectedDate")
        assumeTrue(Build.MODEL.contains("sdk_gphone") && expectedArgument != null)
        val expected = LocalDate.parse(requireNotNull(expectedArgument))
        val dates = Collections.synchronizedList(mutableListOf<LocalDate>())
        val source = TodayDateSource(instrumentation.targetContext)
        val collector = launch(Dispatchers.Default) {
            source.dates.collect { dates += it }
        }
        try {
            withTimeout(5_000L) {
                while (dates.isEmpty()) delay(20L)
            }
            val initial = dates.last()
            assertNotEquals("The host must choose a different local date", initial, expected)
            println("PR03_SYSTEM_READY initial=$initial expected=$expected")

            withTimeout(20_000L) {
                while (dates.last() != expected) delay(20L)
            }
            println("PR03_SYSTEM_RESULT dates=$dates")
            assertEquals(expected, dates.last())
        } finally {
            collector.cancel()
        }
    }
}
