package com.lockin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.ui.theme.Violet
import com.lockin.app.util.InstalledApp
import com.lockin.app.util.InstalledApps

/**
 * Replaces the old dialog that asked you to type "com.instagram.android" by
 * hand. Shows every launchable app on the phone as a tappable icon, with a
 * search box and multi-select, then asks once for the daily budget.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    alreadyLocked: Set<String>,
    onCancel: () -> Unit,
    onConfirm: (List<InstalledApp>, Int) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var showBudget by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = InstalledApps.load(context)
        loading = false
    }

    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose apps to lock") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showBudget = true },
                    containerColor = Violet,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lock ${selected.size}")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Violet)
                }

                visible.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (apps.isEmpty())
                            "No apps found. If this stays empty, the <queries> block is missing from the manifest."
                        else "Nothing matches \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        AppTile(
                            app = app,
                            isSelected = app.packageName in selected,
                            isAlreadyLocked = app.packageName in alreadyLocked,
                            onToggle = {
                                if (app.packageName in selected) selected.remove(app.packageName)
                                else selected.add(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBudget) {
        BudgetDialog(
            count = selected.size,
            onDismiss = { showBudget = false },
            onConfirm = { minutes ->
                showBudget = false
                onConfirm(apps.filter { it.packageName in selected }, minutes)
            }
        )
    }
}

@Composable
private fun AppTile(
    app: InstalledApp,
    isSelected: Boolean,
    isAlreadyLocked: Boolean,
    onToggle: () -> Unit
) {
    val painter = remember(app.packageName) {
        app.icon?.let { drawable ->
            runCatching { BitmapPainter(drawable.toBitmap(144, 144).asImageBitmap()) }.getOrNull()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isAlreadyLocked, onClick = onToggle)
            .background(if (isSelected) InkHigh else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = app.label,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.label.take(1).uppercase(), color = Violet)
                }
            }

            if (isSelected || isAlreadyLocked) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isAlreadyLocked) MaterialTheme.colorScheme.outline else Emerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isAlreadyLocked) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BudgetDialog(count: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(30f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily budget") },
        text = {
            Column {
                Text(
                    "How many minutes a day do you get on " +
                        (if (count == 1) "this app" else "these $count apps") +
                        " once all your tasks are done?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
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
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes.toInt()) }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}
