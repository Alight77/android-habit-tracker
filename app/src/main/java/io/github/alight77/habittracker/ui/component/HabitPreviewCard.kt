package io.github.alight77.habittracker.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alight77.habittracker.R

/** 仅展示表单当前输入；后续接入正式图标 ID 时，在此处替换图标数据与映射。 */
@Composable
fun HabitPreviewCard(name: String, targetPerWeek: Int) {
    val colors = MaterialTheme.colorScheme
    val visibleName = name.trim().ifEmpty { stringResource(R.string.habit_preview_empty_name) }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("habit_preview_card").padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.habit_preview_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.primaryContainer
            ) {
                Text(
                    text = stringResource(R.string.habit_preview_badge),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // 预览植物是统一的临时装饰，不表示习惯已有可持久化图标。
                Image(
                    painter = painterResource(R.drawable.habit_form_seedling),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(52.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = visibleName,
                    modifier = Modifier.testTag("habit_preview_name"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.habit_target_summary, targetPerWeek),
                    modifier = Modifier.testTag("habit_preview_target"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = colors.outlineVariant.copy(alpha = 0.65f)
        )
    }
}
