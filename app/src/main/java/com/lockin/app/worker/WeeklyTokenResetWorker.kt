package com.lockin.app.worker

import android.content.Context
import androidx.work.*
import com.lockin.app.util.UnlockRepository
import java.util.concurrent.TimeUnit

/**
 * Backstop for the weekly token reset. The real reset logic now also runs
 * lazily on read in UnlockRepository, so a missed or delayed run of this job
 * no longer means stale token counts.
 */
class WeeklyTokenResetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        UnlockRepository.currentTokenState(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "weekly_token_reset"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyTokenResetWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
