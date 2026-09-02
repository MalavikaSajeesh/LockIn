package com.lockin.app.data

import androidx.room.Entity

/**
 * Ties a task to a specific locked app, so "go to the gym" can unlock
 * Instagram without also unlocking YouTube.
 *
 * A task with NO rows here applies to every locked app. That keeps the
 * common case zero-effort and makes the feature opt-in.
 */
@Entity(tableName = "task_app_links", primaryKeys = ["taskId", "packageName"])
data class TaskAppLink(
    val taskId: Long,
    val packageName: String
)
