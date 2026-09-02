package com.lockin.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lockin.app.ui.TodoViewModel
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.ui.theme.Violet

@Composable
fun LockedAppsScreen(viewModel: TodoViewModel, onAddApps: () -> Unit) {
    val rows by viewModel.appUnlockRows.collectAsState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddApps,
                containerColor = Violet,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add apps")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("Locked apps", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Each app gets a daily budget. You earn a share of it as you finish tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            if (rows.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps locked yet.\nTap Add apps and pick them from your phone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(rows, key = { it.app.packageName }) { row ->
                        LockedAppRow(
                            packageName = row.app.packageName,
                            label = row.app.appLabel,
                            budget = row.app.dailyUsageMinutes,
                            earned = row.unlockedMinutes,
                            used = row.minutesUsed,
                            remaining = row.minutesRemaining,
                            scopedTasks = row.scopedTaskCount,
                            onRemove = { viewModel.removeLockedApp(row.app) },
                            onBudgetChange = { viewModel.updateBudget(row.app, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedAppRow(
    packageName: String,
    label: String,
    budget: Int,
    earned: Int,
    used: Int,
    remaining: Int,
    scopedTasks: Int,
    onRemove: () -> Unit,
    onBudgetChange: (Int) -> Unit
) {
    var editingBudget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val painter = remember(packageName) {
        runCatching {
            val d = context.packageManager.getApplicationIcon(packageName)
            BitmapPainter(d.toBitmap(144, 144).asImageBitmap())
        }.getOrNull()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = label,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                    )
                } else {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(InkHigh),
                        contentAlignment = Alignment.Center
                    ) { Text(label.take(1).uppercase(), color = Violet) }
                }

                Spacer(Modifier.width(13.dp))

                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$budget min/day budget · $used used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "${remaining}m",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (remaining > 0) Emerald else MaterialTheme.colorScheme.error
                )
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Change budget") },
                            onClick = { menuOpen = false; editingBudget = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (budget == 0) 0f else (earned.toFloat() / budget).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Emerald,
                trackColor = InkHigh
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "$earned of $budget min earned · gated by $scopedTasks " +
                    if (scopedTasks == 1) "task" else "tasks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (editingBudget) {
        BudgetEditDialog(
            current = budget,
            onDismiss = { editingBudget = false },
            onConfirm = { editingBudget = false; onBudgetChange(it) }
        )
    }
}

@Composable
private fun BudgetEditDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily budget") },
        text = {
            Column {
                Text(
                    "${minutes.toInt()} min",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Violet
                )
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    valueRange = 5f..240f,
                    steps = 46
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes.toInt()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
