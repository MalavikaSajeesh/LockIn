package com.lockin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lockin.app.MainActivity
import com.lockin.app.R
import com.lockin.app.util.UnlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shows today's unlock percentage on the home screen, so the state is visible
 * without opening anything.
 *
 * Plain RemoteViews rather than Glance: no extra dependency, and no risk of a
 * Compose-compiler version mismatch.
 */
class LockInWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // The DB read is async, so hold the broadcast open until it finishes.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val progress = UnlockRepository.todayProgress(context)
                val views = buildViews(
                    context,
                    percent = progress.unlockedPercent,
                    total = progress.totalTasks,
                    completed = progress.completedTasks
                )
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        percent: Int,
        total: Int,
        completed: Int
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_lockin).apply {
        setTextViewText(R.id.widget_percent, "$percent%")
        setProgressBar(R.id.widget_progress, 100, percent, false)
        setTextViewText(
            R.id.widget_subtitle,
            when {
                total == 0 -> "No tasks yet"
                completed >= total -> "All done"
                else -> "$completed of $total done"
            }
        )
        setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        /** Call after any change that affects today's progress. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LockInWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, LockInWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
