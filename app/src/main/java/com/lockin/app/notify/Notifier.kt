package com.lockin.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lockin.app.MainActivity
import com.lockin.app.R

object Notifier {

    const val CHANNEL_DAILY = "lockin_daily"
    const val CHANNEL_BUDGET = "lockin_budget"

    private const val ID_MORNING = 1001
    private const val ID_BUDGET = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DAILY,
                "Daily reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "A morning nudge with today's task list" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUDGET,
                "App time running out",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Warns you when earned app time is nearly gone" }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun morning(context: Context, total: Int, completed: Int) {
        if (!canPost(context)) return
        val text = when {
            total == 0 -> "No tasks set for today. Add a few to start earning app time."
            completed >= total -> "Everything's already done. Apps are open."
            else -> "$completed of $total done. Finish the rest to unlock your apps."
        }
        post(context, CHANNEL_DAILY, ID_MORNING, "Today's list", text)
    }

    fun budgetRunningOut(context: Context, appLabel: String, minutesLeft: Int) {
        if (!canPost(context)) return
        post(
            context,
            CHANNEL_BUDGET,
            ID_BUDGET,
            "$minutesLeft min left on $appLabel",
            "Finish another task to earn more time."
        )
    }

    private fun post(context: Context, channel: String, id: Int, title: String, body: String) {
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
