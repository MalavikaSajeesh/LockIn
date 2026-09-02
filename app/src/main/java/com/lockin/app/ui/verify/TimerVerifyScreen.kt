package com.lockin.app.ui.verify

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.ProgressRing
import com.lockin.app.ui.theme.Violet
import kotlinx.coroutines.delay

/**
 * The timer half of verification. The enum had a TIMER option before but no
 * screen behind it, so choosing it did nothing.
 */
@Composable
fun TimerVerifyScreen(
    taskTitle: String,
    totalMinutes: Int,
    onCancel: () -> Unit,
    onVerified: () -> Unit
) {
    val totalSeconds = (totalMinutes.coerceAtLeast(1)) * 60
    var remaining by remember { mutableStateOf(totalSeconds) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining <= 0 && !done) {
            done = true
            running = false
            onVerified()
        }
    }

    val elapsedPercent = (((totalSeconds - remaining).toFloat() / totalSeconds) * 100).toInt()

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            taskTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Stay on this screen until the ring fills.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        ProgressRing(percent = elapsedPercent, size = 220.dp) {
            Text(
                "%02d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.displayMedium,
                color = if (done) Emerald else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { if (!done) running = !running },
            enabled = !done,
            colors = ButtonDefaults.buttonColors(containerColor = if (done) Emerald else Violet),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                when {
                    done -> "Done"
                    running -> "Pause"
                    remaining == totalSeconds -> "Start"
                    else -> "Resume"
                }
            )
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onCancel) {
            Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
