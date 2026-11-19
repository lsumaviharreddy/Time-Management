package com.example.timeblock

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

class PlannerState(private val ctx: Context) {
    val store = Store(ctx)

    val tasks = mutableStateListOf<Task>().apply { addAll(store.loadTasks()) }
    val sessions = mutableStateListOf<FocusSession>().apply { addAll(store.loadSessions()) }

    var selectedDate by mutableStateOf(LocalDate.now())

    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    val nowMinute: Long by derivedStateOf { now / 60_000L }

    // Permissions banner
    var needsNotif by mutableStateOf(false)
        private set
    var needsExact by mutableStateOf(false)
        private set
    var onRequestNotif: () -> Unit = {}
    var onRequestExact: () -> Unit = {}
    var onRequestUsage: () -> Unit = {}
    var onRequestOverlay: () -> Unit = {}

    // App screen time + timers
    val limits = mutableStateMapOf<String, Int>().apply { putAll(store.loadLimits()) }
    var usage by mutableStateOf<UsageResult?>(null)
    var hasUsageAccess by mutableStateOf(false)
        private set
    var canOverlay by mutableStateOf(false)
        private set

    fun setLimit(key: String, minutes: Int) {
        if (minutes <= 0) limits.remove(key) else limits[key] = minutes
        store.saveLimits(limits.toMap())
        Monitor.sync(ctx)
    }

    // Focus timer
    var focusEnd by mutableLongStateOf(store.focusEnd)
        private set
    var focusTotalMin by mutableIntStateOf(store.focusTotal)
        private set
    var isBreak by mutableStateOf(store.focusBreak)
    var focusTaskId by mutableIntStateOf(store.focusTask)
    var workMin by mutableIntStateOf(25)
    var breakMin by mutableIntStateOf(5)
    val running: Boolean get() = focusEnd > 0L

    // ---- tasks ----
    fun tasksOn(date: LocalDate): List<Task> =
        tasks.filter { it.date == date }.sortedBy { it.startMin }

    fun newId(): Int = store.nextId()

    fun upsert(t: Task) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) tasks[i] = t else tasks.add(t)
        store.saveTasks(tasks.toList())
        Reminders.schedule(ctx, t)
    }

    fun delete(id: Int) {
        tasks.removeAll { it.id == id }
        store.saveTasks(tasks.toList())
        Reminders.cancel(ctx, id)
    }

    fun toggleDone(t: Task) = upsert(t.copy(done = !t.done))

    fun titleOf(id: Int): String? = tasks.firstOrNull { it.id == id }?.title

    // ---- focus ----
    private fun saveFocus() {
        store.focusEnd = focusEnd
        store.focusTotal = focusTotalMin
        store.focusBreak = isBreak
        store.focusTask = focusTaskId
    }

    private fun addSession(minutes: Int) {
        if (minutes < 1) return
        sessions.add(FocusSession(LocalDate.now(), minutes, focusTaskId))
        store.saveSessions(sessions.toList())
    }

    fun startFocus() {
        val minutes = if (isBreak) breakMin else workMin
        focusTotalMin = minutes
        focusEnd = System.currentTimeMillis() + minutes * 60_000L
        saveFocus()
        Reminders.scheduleFocusEnd(ctx, focusEnd, isBreak)
    }

    fun stopFocus() {
        if (!running) return
        if (!isBreak) {
            val startedAt = focusEnd - focusTotalMin * 60_000L
            addSession(((System.currentTimeMillis() - startedAt) / 60_000L).toInt())
        }
        focusEnd = 0L
        saveFocus()
        Reminders.cancelFocusEnd(ctx)
    }

    private fun finishFocus() {
        if (!isBreak) addSession(focusTotalMin)
        isBreak = !isBreak // suggest a break after work, work after a break
        focusEnd = 0L
        saveFocus()
    }

    /** Called once a second from the UI. */
    fun tick() {
        now = System.currentTimeMillis()
        if (focusEnd > 0L && now >= focusEnd) finishFocus()
    }

    fun refreshPermissions() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        needsNotif = !nm.areNotificationsEnabled()
        needsExact = Build.VERSION.SDK_INT >= 31 &&
            !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        hasUsageAccess = Usage.hasAccess(ctx)
        canOverlay = Usage.canOverlay(ctx)
    }
}
