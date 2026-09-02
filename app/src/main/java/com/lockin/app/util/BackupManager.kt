package com.lockin.app.util

import android.content.Context
import android.net.Uri
import com.lockin.app.data.AppDatabase
import com.lockin.app.data.LockedApp
import com.lockin.app.data.Task
import com.lockin.app.data.TaskAppLink
import com.lockin.app.data.TaskDuration
import com.lockin.app.data.TaskRecurrence
import com.lockin.app.data.VerificationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup to a plain JSON file you choose with the system file picker.
 *
 * Deliberately no Google Drive, no account, no network permission. The file
 * lands wherever you point it: internal storage, an SD card, or whatever
 * cloud app you happen to have installed, because the picker treats them all
 * the same. LockIn never sees where it went.
 */
object BackupManager {

    private const val FORMAT_VERSION = 1

    suspend fun export(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.getInstance(context)
            val tasks = db.taskDao().getAllOnce()
            val apps = db.lockedAppDao().getAllOnce()
            val links = db.taskAppLinkDao().getAllOnce()
            val token = db.tokenDao().get()

            val root = JSONObject().apply {
                put("formatVersion", FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())

                put("tasks", JSONArray().apply {
                    tasks.forEach { t ->
                        put(JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("duration", t.duration.name)
                            put("recurrence", t.recurrence.name)
                            put("verificationMethod", t.verificationMethod.name)
                            put("expectedLabels", t.expectedLabels)
                            put("timerMinutes", t.timerMinutes)
                            if (t.latitude != null) put("latitude", t.latitude)
                            if (t.longitude != null) put("longitude", t.longitude)
                            put("radiusMeters", t.radiusMeters)
                            put("placeLabel", t.placeLabel)
                            put("weightagePercent", t.weightagePercent)
                            put("dateKey", t.dateKey)
                            put("sortOrder", t.sortOrder)
                            put("isCompleted", t.isCompleted)
                            t.completedAtEpochMillis?.let { put("completedAtEpochMillis", it) }
                        })
                    }
                })

                put("lockedApps", JSONArray().apply {
                    apps.forEach { a ->
                        put(JSONObject().apply {
                            put("packageName", a.packageName)
                            put("appLabel", a.appLabel)
                            put("dailyUsageMinutes", a.dailyUsageMinutes)
                        })
                    }
                })

                put("taskAppLinks", JSONArray().apply {
                    links.forEach { l ->
                        put(JSONObject().apply {
                            put("taskId", l.taskId)
                            put("packageName", l.packageName)
                        })
                    }
                })

                token?.let {
                    put("tokens", JSONObject().apply {
                        put("tokensRemaining", it.tokensRemaining)
                        put("weekStartEpochDay", it.weekStartEpochDay)
                    })
                }
            }

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(root.toString(2).toByteArray())
            } ?: error("Couldn't open that file for writing")

            tasks.size
        }
    }

    /**
     * Replaces everything currently stored. Task ids are preserved so the
     * per-app links still point at the right tasks.
     */
    suspend fun import(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Couldn't open that file for reading")

            val root = JSONObject(text)
            require(root.optInt("formatVersion") == FORMAT_VERSION) {
                "That file was made by a different version of LockIn"
            }

            val db = AppDatabase.getInstance(context)

            val tasks = root.getJSONArray("tasks").let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Task(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        duration = TaskDuration.valueOf(o.getString("duration")),
                        recurrence = TaskRecurrence.valueOf(o.getString("recurrence")),
                        verificationMethod =
                            VerificationMethod.valueOf(o.getString("verificationMethod")),
                        expectedLabels = o.optString("expectedLabels", ""),
                        timerMinutes = o.optInt("timerMinutes", 10),
                        latitude = if (o.has("latitude")) o.getDouble("latitude") else null,
                        longitude = if (o.has("longitude")) o.getDouble("longitude") else null,
                        radiusMeters = o.optInt("radiusMeters", 150),
                        placeLabel = o.optString("placeLabel", ""),
                        weightagePercent = o.optInt("weightagePercent", 0),
                        dateKey = o.getString("dateKey"),
                        sortOrder = o.optInt("sortOrder", 0),
                        isCompleted = o.optBoolean("isCompleted", false),
                        completedAtEpochMillis =
                            if (o.has("completedAtEpochMillis"))
                                o.getLong("completedAtEpochMillis") else null
                    )
                }
            }

            val apps = root.optJSONArray("lockedApps")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    LockedApp(
                        packageName = o.getString("packageName"),
                        appLabel = o.getString("appLabel"),
                        dailyUsageMinutes = o.getInt("dailyUsageMinutes")
                    )
                }
            } ?: emptyList()

            val links = root.optJSONArray("taskAppLinks")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    TaskAppLink(o.getLong("taskId"), o.getString("packageName"))
                }
            } ?: emptyList()

            db.taskDao().deleteAll()
            apps.forEach { db.lockedAppDao().upsert(it) }
            tasks.forEach { db.taskDao().insert(it) }
            if (links.isNotEmpty()) db.taskAppLinkDao().insertAll(links)

            tasks.size
        }
    }

    fun suggestedFileName(): String =
        "lockin-backup-${UnlockRepository.todayKey()}.json"
}
