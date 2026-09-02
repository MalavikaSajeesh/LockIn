package com.lockin.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockin.app.data.*
import com.lockin.app.util.BackupManager
import com.lockin.app.util.UnlockCalculator
import com.lockin.app.util.UnlockRepository
import com.lockin.app.widget.LockInWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    /**
     * A flow, not a constant. Computed once at construction meant leaving the
     * app open past midnight kept filing tasks under yesterday.
     */
    private val todayKeyFlow = MutableStateFlow(UnlockRepository.todayKey())
    private val todayKey: String get() = todayKeyFlow.value

    fun refreshDay() {
        val key = UnlockRepository.todayKey()
        if (key != todayKeyFlow.value) todayKeyFlow.value = key
        rollForwardRecurring()
    }

    val tasksToday: StateFlow<List<Task>> = todayKeyFlow
        .flatMapLatest { db.taskDao().getTasksForDate(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lockedApps: StateFlow<List<LockedApp>> = db.lockedAppDao().getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val taskAppLinks: StateFlow<List<TaskAppLink>> = db.taskAppLinkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tokenState: StateFlow<TokenState?> = db.tokenDao().observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dayStats: StateFlow<List<DayStat>> = db.taskDao().observeDayStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Overall task progress today, ignoring per-app scoping. */
    val unlockSummary: StateFlow<UnlockCalculator.UnlockResult> = tasksToday
        .map { UnlockCalculator.calculateFrom(0, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnlockCalculator.calculate(0, 0, 0, 0))

    data class AppUnlockRow(
        val app: LockedApp,
        val unlockedMinutes: Int,
        val unlockedPercent: Int,
        val minutesUsed: Int,
        val minutesRemaining: Int,
        val scopedTaskCount: Int
    )

    /** Per-app unlocked minutes, honouring task-to-app links. */
    val appUnlockRows: StateFlow<List<AppUnlockRow>> =
        combine(lockedApps, tasksToday, taskAppLinks) { apps, tasks, links ->
            val today = LocalDate.now().toEpochDay()
            apps.map { app ->
                val scoped = UnlockCalculator.tasksForApp(app.packageName, tasks, links)
                val unlock = UnlockCalculator.calculateFrom(app.dailyUsageMinutes, scoped)
                val used = if (app.usageEpochDay == today) app.minutesUsedToday else 0
                AppUnlockRow(
                    app = app,
                    unlockedMinutes = unlock.unlockedMinutes,
                    unlockedPercent = unlock.unlockedPercent,
                    minutesUsed = used,
                    minutesRemaining = (unlock.unlockedMinutes - used).coerceAtLeast(0),
                    scopedTaskCount = scoped.size
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- tasks ----------

    fun saveTask(
        existingId: Long?,
        title: String,
        duration: TaskDuration,
        recurrence: TaskRecurrence,
        verification: VerificationMethod,
        expectedLabels: List<String>,
        timerMinutes: Int,
        latitude: Double?,
        longitude: Double?,
        radiusMeters: Int,
        placeLabel: String,
        weightage: Int,
        appPackages: List<String>
    ) {
        viewModelScope.launch {
            val currentTotal = if (existingId == null) {
                db.taskDao().getTotalWeightageForDate(todayKey)
            } else {
                db.taskDao().getTotalWeightageExcluding(todayKey, existingId)
            }
            val safeWeightage = if (UnlockCalculator.canAssignWeightage(currentTotal, weightage)) {
                weightage
            } else {
                (100 - currentTotal).coerceAtLeast(0)
            }

            val taskId: Long
            if (existingId == null) {
                val nextOrder = db.taskDao().maxSortOrderForDate(todayKey) + 1
                taskId = db.taskDao().insert(
                    Task(
                        title = title,
                        duration = duration,
                        recurrence = recurrence,
                        verificationMethod = verification,
                        expectedLabels = expectedLabels.joinToString(","),
                        timerMinutes = timerMinutes,
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = radiusMeters,
                        placeLabel = placeLabel,
                        weightagePercent = safeWeightage,
                        dateKey = todayKey,
                        sortOrder = nextOrder
                    )
                )
            } else {
                val existing = db.taskDao().getById(existingId) ?: return@launch
                taskId = existingId
                db.taskDao().update(
                    existing.copy(
                        title = title,
                        duration = duration,
                        recurrence = recurrence,
                        verificationMethod = verification,
                        expectedLabels = expectedLabels.joinToString(","),
                        timerMinutes = timerMinutes,
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = radiusMeters,
                        placeLabel = placeLabel,
                        weightagePercent = safeWeightage
                    )
                )
            }

            db.taskAppLinkDao().clearForTask(taskId)
            if (appPackages.isNotEmpty()) {
                db.taskAppLinkDao().insertAll(appPackages.map { TaskAppLink(taskId, it) })
            }
            refreshWidget()
        }
    }

    suspend fun linkedPackagesFor(taskId: Long): List<String> =
        db.taskAppLinkDao().forTask(taskId).map { it.packageName }

    suspend fun remainingWeightage(excludingTaskId: Long? = null): Int {
        val used = if (excludingTaskId == null) {
            db.taskDao().getTotalWeightageForDate(todayKey)
        } else {
            db.taskDao().getTotalWeightageExcluding(todayKey, excludingTaskId)
        }
        return (100 - used).coerceAtLeast(0)
    }

    suspend fun taskById(id: Long): Task? = db.taskDao().getById(id)

    fun markComplete(task: Task) {
        viewModelScope.launch {
            db.taskDao().update(
                task.copy(isCompleted = true, completedAtEpochMillis = System.currentTimeMillis())
            )
            refreshWidget()
        }
    }

    fun markCompleteById(taskId: Long) {
        viewModelScope.launch { db.taskDao().getById(taskId)?.let { markComplete(it) } }
    }

    fun undoComplete(task: Task) {
        viewModelScope.launch {
            db.taskDao().update(task.copy(isCompleted = false, completedAtEpochMillis = null))
            refreshWidget()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            db.taskAppLinkDao().clearForTask(task.id)
            db.taskDao().delete(task)
            refreshWidget()
        }
    }

    /** Moves a task up or down in today's list. */
    fun move(task: Task, up: Boolean) {
        viewModelScope.launch {
            val ordered = db.taskDao().getTasksForDateOnce(todayKey).toMutableList()
            val index = ordered.indexOfFirst { it.id == task.id }
            if (index < 0) return@launch
            val target = if (up) index - 1 else index + 1
            if (target !in ordered.indices) return@launch

            val a = ordered[index]
            val b = ordered[target]
            ordered[index] = b
            ordered[target] = a

            db.taskDao().updateAll(
                ordered.mapIndexed { position, t -> t.copy(sortOrder = position) }
            )
        }
    }

    // ---------- apps ----------

    fun addLockedApp(packageName: String, label: String, dailyMinutes: Int) {
        viewModelScope.launch {
            db.lockedAppDao().upsert(
                LockedApp(
                    packageName = packageName,
                    appLabel = label,
                    dailyUsageMinutes = dailyMinutes,
                    usageEpochDay = LocalDate.now().toEpochDay()
                )
            )
        }
    }

    fun updateBudget(app: LockedApp, minutes: Int) {
        viewModelScope.launch { db.lockedAppDao().update(app.copy(dailyUsageMinutes = minutes)) }
    }

    fun removeLockedApp(app: LockedApp) {
        viewModelScope.launch {
            db.taskAppLinkDao().clearForPackage(app.packageName)
            db.lockedAppDao().delete(app)
        }
    }

    // ---------- tokens ----------

    fun useEmergencyToken(minutes: Int, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val state = UnlockRepository.currentTokenState(getApplication())
            if (state == null || state.tokensRemaining <= 0) {
                onDone(false)
                return@launch
            }
            db.tokenDao().upsert(
                state.copy(
                    tokensRemaining = state.tokensRemaining - 1,
                    activeSkipExpiresAtEpochMillis =
                        System.currentTimeMillis() + minutes * 60_000L
                )
            )
            onDone(true)
        }
    }

    // ---------- backup ----------

    fun exportTo(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            BackupManager.export(getApplication(), uri)
                .onSuccess { onResult("Exported $it tasks.") }
                .onFailure { onResult("Export failed: ${it.message}") }
        }
    }

    fun importFrom(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            BackupManager.import(getApplication(), uri)
                .onSuccess {
                    refreshDay()
                    refreshWidget()
                    onResult("Restored $it tasks.")
                }
                .onFailure { onResult("Import failed: ${it.message}") }
        }
    }

    // ---------- internals ----------

    private fun refreshWidget() {
        runCatching { LockInWidgetProvider.refresh(getApplication()) }
    }

    /**
     * Copies the last recurring set into today, once. The RECURRING flag was
     * stored but nothing ever created the next day's instance.
     */
    private fun rollForwardRecurring() {
        viewModelScope.launch {
            val today = todayKey
            if (db.taskDao().countForDate(today) > 0) return@launch

            val lastKey = db.taskDao().latestRecurringDateKey() ?: return@launch
            if (lastKey == today) return@launch

            db.taskDao().getRecurringForDate(lastKey).forEachIndexed { index, template ->
                val newId = db.taskDao().insert(
                    template.copy(
                        id = 0,
                        dateKey = today,
                        sortOrder = index,
                        isCompleted = false,
                        completedAtEpochMillis = null,
                        templateOfDateKey = lastKey
                    )
                )
                // Carry the per-app scoping across to the new instance.
                val links = db.taskAppLinkDao().forTask(template.id)
                if (links.isNotEmpty()) {
                    db.taskAppLinkDao().insertAll(links.map { TaskAppLink(newId, it.packageName) })
                }
            }
            refreshWidget()
        }
    }
}
