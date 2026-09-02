package com.lockin.app.util

import com.lockin.app.data.DayStat
import java.time.LocalDate

/**
 * Streaks from the day-completion records. A day counts toward a streak when
 * every task defined for it was finished.
 *
 * Today is treated leniently: an unfinished today does not break the streak,
 * it just doesn't extend it yet. Otherwise the number would read zero every
 * morning, which is exactly the wrong feedback.
 */
object StreakCalculator {

    data class Streaks(
        val current: Int,
        val longest: Int,
        val perfectDays: Int,
        val totalTasksCompleted: Int
    )

    fun from(stats: List<DayStat>): Streaks {
        if (stats.isEmpty()) return Streaks(0, 0, 0, 0)

        val byDate = stats.associateBy { it.dateKey }
        val today = LocalDate.now()

        // Walk backwards from today (or yesterday, if today isn't done yet).
        var cursor = if (byDate[today.toString()]?.isPerfect == true) today else today.minusDays(1)
        var current = 0
        while (true) {
            val stat = byDate[cursor.toString()] ?: break
            if (!stat.isPerfect) break
            current++
            cursor = cursor.minusDays(1)
        }

        // Longest run anywhere in the record.
        val sorted = stats.sortedBy { it.dateKey }
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (stat in sorted) {
            val date = runCatching { LocalDate.parse(stat.dateKey) }.getOrNull() ?: continue
            run = if (stat.isPerfect) {
                if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            } else 0
            if (run > longest) longest = run
            previous = date
        }

        return Streaks(
            current = current,
            longest = maxOf(longest, current),
            perfectDays = stats.count { it.isPerfect },
            totalTasksCompleted = stats.sumOf { it.completed }
        )
    }
}
