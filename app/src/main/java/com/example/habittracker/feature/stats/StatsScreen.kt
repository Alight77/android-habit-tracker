package com.example.habittracker.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBackClick: () -> Unit
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    StatsContent(
        uiState = state,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    uiState: StatsUiState,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("‹")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        when (uiState) {
            StatsUiState.Loading -> {
                Text(
                    text = "加载中...",
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                )
            }

            is StatsUiState.Success -> {
                StatsSummaryList(
                    summary = uiState.summary,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun StatsSummaryList(
    summary: StatsSummary,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        StatRow("今日完成", "${summary.todayDoneCount} / ${summary.totalHabits}"),
        StatRow("近 7 天完成率", "${summary.recentSevenDayCompletionPercent}%"),
        StatRow("最高 streak", summary.bestCurrentStreak.toString()),
        StatRow("总打卡次数", summary.totalDoneCount.toString()),
        StatRow("Habit 数量", summary.totalHabits.toString())
    )

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            StatCard(item)
        }
    }
}

@Composable
private fun StatCard(item: StatRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.value,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class StatRow(
    val label: String,
    val value: String
)

@Preview(showBackground = true)
@Composable
fun StatsContentPreview() {
    StatsContent(
        uiState = StatsUiState.Success(
            summary = StatsSummary(
                totalHabits = 3,
                todayDoneCount = 2,
                totalDoneCount = 18,
                recentSevenDayCompletionPercent = 71,
                bestCurrentStreak = 5
            )
        ),
        onBackClick = {}
    )
}