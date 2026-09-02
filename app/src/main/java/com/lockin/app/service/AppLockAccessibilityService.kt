package com.lockin.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.lockin.app.data.AppDatabase
import com.lockin.app.notify.Notifier
import com.lockin.app.ui.LockScreenActivity
import com.lockin.app.util.UnlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Watches the foreground app. Two jobs:
 *
 *  1. Bill time. While a locked app is in the foreground we remember when it
 *     came forward; when something else comes forward we add the elapsed
 *     seconds to that app's daily counter. (Nothing wrote to this counter
 *     before, so the daily minute budget never actually ticked down.)
 *
 *  2. Gate. If a locked app opens and there are no unlocked minutes left,
 *     launch LockScreenActivity over it.
 */
class AppLockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentLockedPackage: String? = null
    private var foregroundSinceMillis: Long = 0L

    /** Debounce: don't relaunch the lock screen for the same package repeatedly. */
    private var lastLockShownFor: String? = null
    private var lastLockShownAt: Long = 0L

    /** So the low-time nudge fires once per app, not once a minute. */
    private var lastNudgeFor: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        scope.launch { handleForegroundChange(packageName) }
    }

    private suspend fun handleForegroundChange(packageName: String) {
        // Bill whatever was open before, if it was a locked app.
        val previous = currentLockedPackage
        if (previous != null && previous != packageName) {
            flushUsage(previous)
        }

        val db = AppDatabase.getInstance(applicationContext)
        val lockedApp = db.lockedAppDao().getByPackage(packageName)

        if (lockedApp == null) {
            currentLockedPackage = null
            lastNudgeFor = null
            return
        }

        val state = UnlockRepository.stateFor(applicationContext, packageName)

        if (state.allowed) {
            // Start (or continue) the clock on this app.
            if (currentLockedPackage != packageName) {
                currentLockedPackage = packageName
                foregroundSinceMillis = System.currentTimeMillis()
            }
            return
        }

        currentLockedPackage = null

        val now = System.currentTimeMillis()
        if (lastLockShownFor == packageName && now - lastLockShownAt < LOCK_DEBOUNCE_MS) return
        lastLockShownFor = packageName
        lastLockShownAt = now

        val intent = Intent(applicationContext, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(LockScreenActivity.EXTRA_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    /**
     * Without this, running out of minutes mid-session did nothing, because no
     * new window event fires while you sit inside the app. Ticks once a minute,
     * bills the time so far, and locks as soon as the budget is gone.
     */
    private fun startWatchdog() {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val pkg = currentLockedPackage ?: continue
                flushUsage(pkg)
                foregroundSinceMillis = System.currentTimeMillis()

                val state = UnlockRepository.stateFor(applicationContext, pkg)

                // Nudge once when the budget is nearly gone.
                if (state.allowed && state.minutesRemaining in 1..LOW_TIME_MINUTES &&
                    lastNudgeFor != pkg
                ) {
                    lastNudgeFor = pkg
                    Notifier.budgetRunningOut(
                        applicationContext,
                        state.lockedApp?.appLabel ?: pkg,
                        state.minutesRemaining
                    )
                }

                if (!state.allowed) {
                    currentLockedPackage = null
                    startActivity(
                        Intent(applicationContext, LockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
                        }
                    )
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startWatchdog()
    }

    private suspend fun flushUsage(packageName: String) {
        val started = foregroundSinceMillis
        foregroundSinceMillis = 0L
        if (started <= 0L) return

        val seconds = ((System.currentTimeMillis() - started) / 1000L).toInt()
        if (seconds <= 0) return

        AppDatabase.getInstance(applicationContext)
            .lockedAppDao()
            .addUsage(packageName, seconds, LocalDate.now().toEpochDay())
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        // The old version leaked this scope.
        scope.cancel()
    }

    private companion object {
        const val LOCK_DEBOUNCE_MS = 1500L
        const val TICK_MS = 60_000L
        const val LOW_TIME_MINUTES = 5
    }
}
