package com.lockin.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.app.ui.TodoViewModel
import com.lockin.app.ui.theme.Amber
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.HeroBrush
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.util.UnlockRepository
import kotlinx.coroutines.delay

@Composable
fun TokenScreen(viewModel: TodoViewModel) {
    val tokenState by viewModel.tokenState.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remaining = tokenState?.tokensRemaining ?: 0
    val expiresAt = tokenState?.activeSkipExpiresAtEpochMillis ?: 0L
    val skipActive = expiresAt > now
    val skipSecondsLeft = ((expiresAt - now) / 1000L).coerceAtLeast(0L).toInt()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Emergency tokens", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(HeroBrush)
                .padding(vertical = 30.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(UnlockRepository.WEEKLY_TOKENS) { index ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (index < remaining) Amber else InkHigh)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "$remaining",
                style = MaterialTheme.typography.displayMedium,
                color = if (remaining > 0) Amber else MaterialTheme.colorScheme.error
            )
            Text(
                "left this week",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "You get ${UnlockRepository.WEEKLY_TOKENS} a week. Each one opens every locked app " +
                "for as long as you choose, no tasks needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        if (skipActive) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Skip active", color = Emerald, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "%02d:%02d remaining".format(skipSecondsLeft / 60, skipSecondsLeft % 60),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        } else {
            Button(
                onClick = { showPicker = true },
                enabled = remaining > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = MaterialTheme.colorScheme.onTertiary),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (remaining > 0) "Spend a token" else "None left until next week")
            }
        }
    }

    if (showPicker) {
        MinutesPickerDialog(
            onDismiss = { showPicker = false },
            onConfirm = { minutes ->
                viewModel.useEmergencyToken(minutes)
                showPicker = false
            }
        )
    }
}

@Composable
private fun MinutesPickerDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(15f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How long?") },
        text = {
            Column {
                Text(
                    "Every locked app opens for this long, then the lock comes back.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "${minutes.toInt()} min",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Amber
                )
                Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..60f, steps = 10)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes.toInt()) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
