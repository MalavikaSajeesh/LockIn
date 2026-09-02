package com.lockin.app.data

/** One day's completion record, for the streaks and history screen. */
data class DayStat(
    val dateKey: String,
    val total: Int,
    val completed: Int
) {
    val isPerfect: Boolean get() = total > 0 && completed == total
    val percent: Int get() = if (total == 0) 0 else (completed * 100) / total
}
