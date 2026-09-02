package com.lockin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskDuration { SHORT_TERM, LONG_TERM }
enum class TaskRecurrence { ONE_TIME, RECURRING }

enum class VerificationMethod {
    CAMERA_SCAN,   // ML Kit image labeling on a photo you take
    TIMER,         // must run an in-app timer to completion
    LOCATION,      // must physically be at a saved place
    MANUAL         // honour-system checkbox
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val duration: TaskDuration,
    val recurrence: TaskRecurrence,
    val verificationMethod: VerificationMethod,
    /** Comma-separated expected ML Kit labels, only used when CAMERA_SCAN. */
    val expectedLabels: String = "",
    /** Minutes the timer should run for, only used when TIMER. */
    val timerMinutes: Int = 10,
    /** Saved place, only used when LOCATION. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = 150,
    val placeLabel: String = "",
    /** Weightage 0-100; tasks for a day must sum to <= 100. */
    val weightagePercent: Int = 0,
    /** Which calendar day (yyyy-MM-dd) this instance belongs to. */
    val dateKey: String,
    /** Manual ordering within the day. Lower sorts first. */
    val sortOrder: Int = 0,
    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val templateOfDateKey: String? = null
)
