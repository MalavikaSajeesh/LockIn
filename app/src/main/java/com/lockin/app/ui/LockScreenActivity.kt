package com.lockin.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lockin.app.MainActivity
import com.lockin.app.data.AppDatabase
import com.lockin.app.ui.theme.Emerald
import com.lockin.app.ui.theme.Ink
import com.lockin.app.ui.theme.InkHigh
import com.lockin.app.ui.theme.LockInTheme
import com.lockin.app.ui.theme.ProgressRing
import com.lockin.app.ui.theme.Violet
import com.lockin.app.util.InstalledApps
import com.lockin.app.util.UnlockRepository
import kotlinx.coroutines.launch

/**
 * The lock screen shown over a locked app.
 *
 * Two things changed from the previous version:
 *  - It reads the real task state instead of passing hardcoded zeros into
 *    UnlockCalculator, which made it always report 0% / 0 minutes.
 *  - Neither button just calls finish() any more. Finishing dropped you
 *    straight back into the app you were trying to block.
 */
class LockScreenActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()

        // Back should go home, not reveal the app underneath.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            LockInTheme {
                LockScreenContent(
                    blockedPackage = blockedPackage,
                    onOpenTasks = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    },
                    onTokenUsed = { goHome() },
                    onDismiss = { goHome() }
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
private fun LockScreenContent(
    blockedPackage: String,
    onOpenTasks: () -> Unit,
    onTokenUsed: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UnlockRepository.AppLockState?>(null) }
    var appLabel by remember { mutableStateOf("") }
    var tokensLeft by remember { mutableStateOf(0) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(blockedPackage) {
        appLabel = InstalledApps.labelFor(context, blockedPackage)
        state = UnlockRepository.stateFor(context, blockedPackage)
        tokensLeft = UnlockRepository.currentTokenState(context)?.tokensRemaining ?: 0
    }

    val unlock = state?.unlock

    Box(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(InkHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Violet,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (appLabel.isBlank()) "This app is locked" else "$appLabel is locked",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            ProgressRing(percent = unlock?.unlockedPercent ?: 0, size = 168.dp, strokeWidth = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${unlock?.unlockedPercent ?: 0}%",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        "earned",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                when {
                    state == null -> "Checking your tasks…"
                    unlock!!.totalTasks == 0 ->
                        "You have no tasks for today. Add one to start earning time."
                    state!!.minutesRemaining > 0 ->
                        "${state!!.minutesRemaining} min left of the ${unlock.unlockedMinutes} you've earned."
                    else ->
                        "You've used all ${unlock.unlockedMinutes} min you earned today " +
                        "(${unlock.completedTasks} of ${unlock.totalTasks} tasks done). " +
                        "Finish another task to earn more."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            tokenError?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(30.dp))

            Button(
                onClick = onOpenTasks,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Open my tasks")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showTokenDialog = true },
                enabled = tokensLeft > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (tokensLeft > 0) "Use an emergency token ($tokensLeft left)"
                    else "No emergency tokens left this week"
                )
            }

            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss) {
                Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showTokenDialog) {
        TokenSkipDialog(
            onDismiss = { showTokenDialog = false },
            onConfirm = { minutes ->
                showTokenDialog = false
                scope.launch {
                    val db = AppDatabase.getInstance(context)
                    val current = UnlockRepository.currentTokenState(context)
                    if (current == null || current.tokensRemaining <= 0) {
                        tokenError = "No tokens left this week."
                        return@launch
                    }
                    db.tokenDao().upsert(
                        current.copy(
                            tokensRemaining = current.tokensRemaining - 1,
                            activeSkipExpiresAtEpochMillis =
                                System.currentTimeMillis() + minutes * 60_000L
                        )
                    )
                    onTokenUsed()
                }
            }
        )
    }
}

@Composable
private fun TokenSkipDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(15f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip the lock") },
        text = {
            Column {
                Text(
                    "This spends one of your weekly tokens and opens everything for a set time.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "${minutes.toInt()} min",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Emerald
                )
                Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..60f, steps = 10)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes.toInt()) }) { Text("Spend token") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
