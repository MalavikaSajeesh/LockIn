package com.lockin.app.worker

import android.content.Context
import androidx.work.*
import com.lockin.app.notify.Notifier
import com.lockin.app.util.UnlockRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Posts the morning "here's today's list" nudge. */
class MorningReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Notifier.ensureChannels(applicationContext)
        val progress = UnlockRepository.todayProgress(applicationContext)
        Notifier.morning(applicationContext, progress.totalTasks, progress.completedTasks)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "morning_reminder"

        /** Schedules a daily reminder at [hour]:00 local time. */
        fun schedule(context: Context, hour: Int = 8) {
            val now = LocalDateTime.now()
            var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val initialDelay = Duration.between(now, target).toMinutes()

            val request = PeriodicWorkRequestBuilder<MorningReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
