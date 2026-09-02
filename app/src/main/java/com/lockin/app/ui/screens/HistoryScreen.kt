package com.lockin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.app.data.DayStat
import com.lockin.app.ui.TodoViewModel
import com.lockin.app.ui.theme.Amber
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.HeroBrush
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.ui.theme.Violet
import com.lockin.app.util.StreakCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * completedAtEpochMillis was already being written and never read. This is
 * what it was for.
 */
@Composable
fun HistoryScreen(viewModel: TodoViewModel) {
    val stats by viewModel.dayStats.collectAsState()
    val streaks = remember(stats) { StreakCalculator.from(stats) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Your record", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(HeroBrush)
                    .padding(vertical = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streaks.current > 0) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${streaks.current}",
                        style = MaterialTheme.typography.displayMedium,
                        color = if (streaks.current > 0) Amber else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (streaks.current == 1) "day streak" else "day streak",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A day counts when every task on it got done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Longest", "${streaks.longest}", "days", Modifier.weight(1f))
                StatTile("Perfect", "${streaks.perfectDays}", "days", Modifier.weight(1f))
                StatTile("Done", "${streaks.totalTasksCompleted}", "tasks", Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Last 12 weeks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item { CompletionGrid(stats) }

        item {
            Text(
                "Recent days",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (stats.isEmpty()) {
            item {
                Text(
                    "Nothing recorded yet. Finish a task and it'll show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(stats.take(14), key = { it.dateKey }) { stat ->
                DayRow(stat)
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Violet)
        Text(
            unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Contribution-graph style grid: 12 weeks of columns, one square per day. */
@Composable
private fun CompletionGrid(stats: List<DayStat>) {
    val byDate = remember(stats) { stats.associateBy { it.dateKey } }
    val today = LocalDate.now()
    val weeks = 12

    // Start on the Monday 12 weeks back so columns line up as weeks.
    val start = today.minusWeeks((weeks - 1).toLong())
        .minusDays(((today.dayOfWeek.value - 1).toLong()))

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { week ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { day ->
                    val date = start.plusWeeks(week.toLong()).plusDays(day.toLong())
                    val stat = byDate[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]
                    val colour = when {
                        date.isAfter(today) -> InkHigh.copy(alpha = 0.35f)
                        stat == null -> InkHigh
                        stat.isPerfect -> Emerald
                        stat.percent >= 50 -> Emerald.copy(alpha = 0.55f)
                        stat.completed > 0 -> Emerald.copy(alpha = 0.28f)
                        else -> InkHigh
                    }
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colour)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRow(stat: DayStat) {
    val label = runCatching {
        LocalDate.parse(stat.dateKey).format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }.getOrDefault(stat.dateKey)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${stat.completed}/${stat.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (stat.isPerfect) Emerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
