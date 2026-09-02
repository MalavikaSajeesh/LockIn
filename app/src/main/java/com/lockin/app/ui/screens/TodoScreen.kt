package com.lockin.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lockin.app.data.Task
import com.lockin.app.data.VerificationMethod
import com.lockin.app.ui.TodoViewModel
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.HeroBrush
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.ui.theme.ProgressRing
import com.lockin.app.ui.theme.Violet

@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onAddTask: () -> Unit,
    onEditTask: (Task) -> Unit,
    onVerify: (Task) -> Unit
) {
    val tasks by viewModel.tasksToday.collectAsState()
    val summary by viewModel.unlockSummary.collectAsState()
    val rows by viewModel.appUnlockRows.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshDay() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTask,
                containerColor = Violet,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Task")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(HeroBrush)
                        .padding(vertical = 28.dp)
                ) {
                    ProgressRing(percent = summary.unlockedPercent) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${summary.unlockedPercent}%",
                                style = MaterialTheme.typography.displayMedium,
                                color = if (summary.isFullyUnlocked) Emerald
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "unlocked",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        when {
                            summary.totalTasks == 0 -> "Add a task to start unlocking"
                            summary.isFullyUnlocked -> "Everything done. Apps are fully open."
                            else -> "${summary.completedTasks} of ${summary.totalTasks} tasks done"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (rows.isNotEmpty()) {
                item {
                    Text(
                        "Time earned today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.take(3).forEach { row ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    row.app.appLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${row.minutesRemaining}m",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (row.minutesRemaining > 0) Emerald
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Today",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (tasks.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nothing here yet.\nTap Task to add your first one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                    TaskRow(
                        task = task,
                        canMoveUp = index > 0,
                        canMoveDown = index < tasks.lastIndex,
                        onMoveUp = { viewModel.move(task, up = true) },
                        onMoveDown = { viewModel.move(task, up = false) },
                        onEdit = { onEditTask(task) },
                        onPrimary = {
                            if (task.isCompleted) viewModel.undoComplete(task)
                            else if (task.verificationMethod == VerificationMethod.MANUAL)
                                viewModel.markComplete(task)
                            else onVerify(task)
                        },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    val accent by animateColorAsState(
        targetValue = if (task.isCompleted) Emerald else Violet,
        label = "taskAccent"
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (task.isCompleted) Emerald.copy(alpha = 0.18f) else InkHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        task.isCompleted -> Icons.Default.Check
                        task.verificationMethod == VerificationMethod.CAMERA_SCAN -> Icons.Default.CameraAlt
                        task.verificationMethod == VerificationMethod.TIMER -> Icons.Default.Timer
                        task.verificationMethod == VerificationMethod.LOCATION -> Icons.Default.LocationOn
                        else -> Icons.Default.TouchApp
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${task.weightagePercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = accent
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (task.verificationMethod) {
                            VerificationMethod.CAMERA_SCAN -> "Photo check"
                            VerificationMethod.TIMER -> "${task.timerMinutes} min timer"
                            VerificationMethod.LOCATION ->
                                if (task.placeLabel.isNotBlank()) "At ${task.placeLabel}"
                                else "Location check"
                            VerificationMethod.MANUAL -> "Tap to tick"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.outline
                    )
                }
            }

            TextButton(onClick = onPrimary) {
                Text(
                    if (task.isCompleted) "Undo" else "Do it",
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }

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
                        text = { Text("Edit") },
                        onClick = { menuOpen = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                    )
                }
            }
        }
    }
}
