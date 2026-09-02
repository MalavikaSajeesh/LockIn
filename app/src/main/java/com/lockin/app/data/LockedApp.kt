package com.lockin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedApp(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    /** Total minutes allowed per day, once fully unlocked. */
    val dailyUsageMinutes: Int,
    /** Which epoch day the counters below belong to. */
    val usageEpochDay: Long = 0,
    /**
     * Seconds used today. Stored in seconds rather than minutes so short
     * sessions are not rounded away to nothing.
     */
    val secondsUsedToday: Int = 0
) {
    val minutesUsedToday: Int get() = secondsUsedToday / 60
}
