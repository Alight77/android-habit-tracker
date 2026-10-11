package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import io.github.alight77.habittracker.ui.theme.Ink
import io.github.alight77.habittracker.ui.theme.MutedInk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val journalDateFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE)

@Composable
internal fun TodayJournalHeader(today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // 放大字号下文案可能换行，提前去掉内置笔刷图，避免窄屏时与文字贴近。
    val showPaperArtwork = LocalDensity.current.fontScale < 1.2f
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(1.dp, cardShape)
            .clip(cardShape)
            .background(colors.surfaceContainerHigh)
            .then(
                if (showPaperArtwork) Modifier else Modifier.border(1.dp, colors.outlineVariant, cardShape)
            )
    ) {
        if (showPaperArtwork) {
            // 图片只承担纸张与叶片装饰；真实日期和文案仍由 Compose 绘制并供 TalkBack 读取。
            Image(
                painter = painterResource(R.drawable.dashboard_header_paper),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = 18.dp,
                    end = if (showPaperArtwork) 64.dp else 20.dp,
                    // 素材里的绿色笔刷位于下缘，留白随卡片高度伸展以免压住鼓励文案。
                    bottom = if (showPaperArtwork) 40.dp else 20.dp
                )
        ) {
            Text(
                text = journalDateFormatter.format(today),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                color = if (showPaperArtwork) Ink else colors.onSurface
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.dashboard_encouragement),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                color = if (showPaperArtwork) MutedInk else colors.onSurfaceVariant
            )
        }
    }
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
