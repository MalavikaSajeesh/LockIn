package com.lockin.app.util

import com.lockin.app.data.Task
import com.lockin.app.data.TaskAppLink

/**
 * The weightage -> unlock-time rule:
 *   - Each task has a weightage 0-100%.
 *   - Completing tasks whose weightage sums to X% unlocks X% of the app's
 *     daily allotted usage time.
 *   - Completing every task defined for the day removes the lock entirely.
 *
 * Worked example: 3 tasks A, B, C each weighted 10%.
 *   - A + B done -> 20% of the allotted time unlocked.
 *   - C done too -> every task for the day is done, so full unlock, even
 *     though the weightages only summed to 30 rather than a literal 100.
 */
object UnlockCalculator {

    data class UnlockResult(
        val allottedMinutes: Int,
        val unlockedPercent: Int,
        val unlockedMinutes: Int,
        val isFullyUnlocked: Boolean,
        val totalTasks: Int,
        val completedTasks: Int
    )

    fun calculate(
        dailyAllottedMinutes: Int,
        totalTasksToday: Int,
        completedTasksToday: Int,
        completedWeightageSum: Int
    ): UnlockResult {
        val isFullyUnlocked = totalTasksToday > 0 && completedTasksToday == totalTasksToday

        val effectivePercent = if (isFullyUnlocked) 100 else completedWeightageSum.coerceIn(0, 100)
        val minutes = (dailyAllottedMinutes * effectivePercent) / 100

        return UnlockResult(
            allottedMinutes = dailyAllottedMinutes,
            unlockedPercent = effectivePercent,
            unlockedMinutes = minutes,
            isFullyUnlocked = isFullyUnlocked,
            totalTasks = totalTasksToday,
            completedTasks = completedTasksToday
        )
    }

    /** Convenience overload that derives the counts from the day's task list. */
    fun calculateFrom(dailyAllottedMinutes: Int, tasks: List<Task>): UnlockResult = calculate(
        dailyAllottedMinutes = dailyAllottedMinutes,
        totalTasksToday = tasks.size,
        completedTasksToday = tasks.count { it.isCompleted },
        completedWeightageSum = tasks.filter { it.isCompleted }.sumOf { it.weightagePercent }
    )

    /**
     * Which of the day's tasks gate this particular app. A task with no
     * TaskAppLink rows is global and always counts; a task with links counts
     * only for the apps it names. So "go to the gym" can gate Instagram while
     * leaving your podcast app alone.
     */
    fun tasksForApp(
        packageName: String,
        tasks: List<Task>,
        links: List<TaskAppLink>
    ): List<Task> {
        val linkedTaskIds = links.map { it.taskId }.toSet()
        val idsForThisApp = links.filter { it.packageName == packageName }.map { it.taskId }.toSet()
        return tasks.filter { it.id !in linkedTaskIds || it.id in idsForThisApp }
    }

    fun calculateForApp(
        packageName: String,
        dailyAllottedMinutes: Int,
        tasks: List<Task>,
        links: List<TaskAppLink>
    ): UnlockResult = calculateFrom(
        dailyAllottedMinutes,
        tasksForApp(packageName, tasks, links)
    )

    /** Validates that weightages assigned across a day's tasks never exceed 100. */
    fun canAssignWeightage(currentTotalExcludingThisTask: Int, newWeightage: Int): Boolean =
        currentTotalExcludingThisTask + newWeightage <= 100

    /** Suggests likely ML Kit labels from a task title, e.g. "Have breakfast" -> food. */
    fun suggestLabelsForTitle(title: String): List<String> {
        val t = title.lowercase()
        return when {
            "breakfast" in t || "lunch" in t || "dinner" in t || "eat" in t || "meal" in t ->
                listOf("food", "plate", "dish", "meal", "table")
            "walk" in t || "run" in t || "jog" in t ->
                listOf("outdoor", "footwear", "road", "path", "sky")
            "gym" in t || "workout" in t || "exercise" in t ->
                listOf("gym", "fitness", "exercise equipment", "person")
            "water" in t || "hydrate" in t || "drink" in t ->
                listOf("bottle", "glass", "water", "cup")
            "read" in t || "book" in t || "study" in t ->
                listOf("book", "text", "page")
            "medicine" in t || "medication" in t || "vitamin" in t || "pill" in t ->
                listOf("pill", "medication", "bottle", "tablet")
            "clean" in t || "tidy" in t || "dishes" in t ->
                listOf("room", "furniture", "floor", "kitchen")
            "plant" in t || "garden" in t || "water the" in t ->
                listOf("plant", "flower", "leaf", "garden")
            else -> emptyList()
        }
    }
}
