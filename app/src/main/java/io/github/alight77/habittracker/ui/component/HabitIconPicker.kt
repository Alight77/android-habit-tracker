package io.github.alight77.habittracker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.ui.icon.HabitIconCatalog

/** 仅回传用户明确点选的已知 Key；关闭和系统返回均不修改表单草稿。 */
@Composable
fun HabitIconPicker(
    selectedKey: IconKey,
    onSelect: (IconKey) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() <= 0.5f
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = colors.surface
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    text = stringResource(R.string.habit_icon_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HabitIconCatalog.knownDefinitions.forEach { definition ->
                    val isSelected = selectedKey == definition.key
                    val palette = definition.palette(isDark)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) palette.cardWash else colors.surface,
                        border = if (isSelected) BorderStroke(2.dp, palette.uncheckedAction) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onSelect(definition.key) }
                                .semantics { selected = isSelected }
                                .testTag("habit_icon_option_${definition.key.value}")
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(definition.drawableRes),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(definition.labelRes),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (isSelected) {
                                Text(
                                    text = stringResource(R.string.habit_icon_selected),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}
