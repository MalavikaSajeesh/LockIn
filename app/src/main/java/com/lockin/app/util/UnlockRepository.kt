package com.lockin.app.util

import android.content.Context
import com.lockin.app.data.AppDatabase
import com.lockin.app.data.LockedApp
import com.lockin.app.data.TokenState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Everything that decides "is this app allowed right now" lives here, so the
 * accessibility service, the lock screen, the widget and the UI all agree.
 */
object UnlockRepository {

    data class AppLockState(
        val lockedApp: LockedApp?,
        val unlock: UnlockCalculator.UnlockResult,
        val minutesUsed: Int,
        val minutesRemaining: Int,
        val skipActive: Boolean,
        val allowed: Boolean
    )

    fun todayKey(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    suspend fun stateFor(context: Context, packageName: String): AppLockState {
        val db = AppDatabase.getInstance(context)
        val lockedApp = db.lockedAppDao().getByPackage(packageName)
        val token = currentTokenState(context)
        val skipActive = (token?.activeSkipExpiresAtEpochMillis ?: 0L) > System.currentTimeMillis()

        if (lockedApp == null) {
            return AppLockState(
                lockedApp = null,
                unlock = UnlockCalculator.calculate(0, 0, 0, 0),
                minutesUsed = 0,
                minutesRemaining = 0,
                skipActive = skipActive,
                allowed = true
            )
        }

        val tasks = db.taskDao().getTasksForDateOnce(todayKey())
        val links = db.taskAppLinkDao().getAllOnce()
        val unlock = UnlockCalculator.calculateForApp(
            packageName, lockedApp.dailyUsageMinutes, tasks, links
        )

        val today = LocalDate.now().toEpochDay()
        val minutesUsed = if (lockedApp.usageEpochDay == today) lockedApp.minutesUsedToday else 0
        val remaining = (unlock.unlockedMinutes - minutesUsed).coerceAtLeast(0)

        return AppLockState(
            lockedApp = lockedApp,
            unlock = unlock,
            minutesUsed = minutesUsed,
            minutesRemaining = remaining,
            skipActive = skipActive,
            allowed = skipActive || remaining > 0
        )
    }

    /**
     * Reads the token row, applying the weekly reset lazily so the count is
     * right the instant the app opens rather than whenever WorkManager runs.
     */
    suspend fun currentTokenState(context: Context): TokenState? {
        val db = AppDatabase.getInstance(context)
        val state = db.tokenDao().get() ?: return null
        val today = LocalDate.now().toEpochDay()
        if (today - state.weekStartEpochDay >= 7) {
            val reset = state.copy(
                tokensRemaining = WEEKLY_TOKENS,
                weekStartEpochDay = today,
                activeSkipExpiresAtEpochMillis = null
            )
            db.tokenDao().upsert(reset)
            return reset
        }
        return state
    }

    /** Overall task progress for today, ignoring per-app scoping. */
    suspend fun todayProgress(context: Context): UnlockCalculator.UnlockResult {
        val tasks = AppDatabase.getInstance(context).taskDao().getTasksForDateOnce(todayKey())
        return UnlockCalculator.calculateFrom(0, tasks)
    }

    const val WEEKLY_TOKENS = 10
}
