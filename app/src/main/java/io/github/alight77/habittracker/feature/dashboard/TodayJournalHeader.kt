package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import android.icu.util.ChineseCalendar
import android.icu.util.ULocale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import io.github.alight77.habittracker.ui.theme.WarmPaperMiddle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)
private val weekdayFormatter = DateTimeFormatter.ofPattern("E", Locale.SIMPLIFIED_CHINESE)
private val lunarMonths = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
private val lunarDigits = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

@Composable
internal fun TodayJournalHeader(today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val isLight = colors.background.luminance() > 0.5f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = if (largeText) 16.dp else 24.dp, bottom = 10.dp)
    ) {
        Text(
            text = dateFormatter.format(today),
            color = colors.onBackground,
            fontSize = if (largeText) 36.sp else 46.sp,
            lineHeight = if (largeText) 44.sp else 54.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${weekdayFormatter.format(today)} · ${formatLunarDate(today)}",
            color = colors.onBackground,
            fontSize = if (largeText) 15.sp else 17.sp,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(if (largeText) 12.dp else 14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val narrow = maxWidth < 340.dp
            val compactPaper = narrow || fontScale > 1f
            val quote = stringResource(
                if (largeText || fontScale >= 1.25f) R.string.dashboard_encouragement_compact
                else R.string.dashboard_encouragement
            )

            if (isLight && fontScale < 1.25f) {
                // 纸条图片只作底纹，寄语仍由 Compose 绘制，字号变化时不会失真。
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (compactPaper) 0.9f else 0.78f)
                        .aspectRatio(3f)
                ) {
                    Image(
                        painter = painterResource(R.drawable.dashboard_quote_paper),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = quote,
                        color = colors.onSurface,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (compactPaper) 14.sp else 17.sp,
                        lineHeight = if (compactPaper) 23.sp else 27.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = if (compactPaper) 18.dp else 20.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(
                        if (largeText) 1f else if (fontScale >= 1.25f) 0.9f else 0.78f
                    ),
                    shape = RoundedCornerShape(5.dp),
                    color = if (isLight) WarmPaperMiddle else colors.surface,
                    shadowElevation = if (isLight) 1.dp else 0.dp
                ) {
                    Text(
                        text = quote,
                        color = colors.onSurface,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (fontScale >= 1.25f) 15.sp else 17.sp,
                        lineHeight = if (fontScale >= 1.25f) 25.sp else 27.sp,
                        modifier = Modifier.padding(
                            horizontal = if (fontScale >= 1.25f) 12.dp else 20.dp,
                            vertical = 16.dp
                        )
                    )
                }
            }
        }
    }
}

private fun formatLunarDate(date: LocalDate): String {
    val timezone = TimeZone.getTimeZone("Asia/Shanghai")
    val gregorian = GregorianCalendar(timezone, Locale.CHINA).apply {
        clear()
        set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
    }
    val lunar = ChineseCalendar(
        android.icu.util.TimeZone.getTimeZone("Asia/Shanghai"),
        ULocale.SIMPLIFIED_CHINESE
    ).apply { timeInMillis = gregorian.timeInMillis }
    val month = lunar.get(ChineseCalendar.MONTH)
    val day = lunar.get(ChineseCalendar.DAY_OF_MONTH)
    val leap = lunar.get(ChineseCalendar.IS_LEAP_MONTH) == 1

    // ICU 负责公历转农历；这里只把月、日数字写成中文手帐标题。
    val monthName = lunarMonths.getOrElse(month) { (month + 1).toString() }
    val dayName = when (day) {
        in 1..9 -> "初${lunarDigits[day - 1]}"
        10 -> "初十"
        in 11..19 -> "十${lunarDigits[day - 11]}"
        20 -> "二十"
        in 21..29 -> "廿${lunarDigits[day - 21]}"
        30 -> "三十"
        else -> day.toString()
    }
    return "农历${if (leap) "闰" else ""}${monthName}月$dayName"
}

@Preview(name = "Date header - Light", showBackground = true, widthDp = 360)
@Preview(name = "Date header - 320dp 1.15x", showBackground = true, widthDp = 320, fontScale = 1.15f)
@Preview(name = "Date header - 320dp 2x", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun TodayJournalHeaderLightPreview() {
    HabitTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayJournalHeader(today = LocalDate.of(2026, 10, 11))
        }
    }
}

@Preview(
    name = "Date header - Dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun TodayJournalHeaderDarkPreview() {
    HabitTrackerTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayJournalHeader(today = LocalDate.of(2026, 10, 11))
        }
    }
}
