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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PlannerState(private val ctx: Context) {
    val store = Store(ctx)

    val tasks = mutableStateListOf<Task>().apply { addAll(store.loadTasks()) }
    val sessions = mutableStateListOf<FocusSession>().apply { addAll(store.loadSessions()) }

    val series = mutableStateListOf<Series>().apply { addAll(store.loadSeries()) }

    var selectedDate by mutableStateOf(LocalDate.now())

    /** 0 = day, 1 = week, 2 = month */
    var viewMode by mutableIntStateOf(0)
    var themeIndex by mutableIntStateOf(store.theme)
        private set

    fun setTheme(i: Int) {
        themeIndex = i
        store.theme = i
    }

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
    var onRequestCalendar: () -> Unit = {}

    // App screen time + timers
    val limits = mutableStateMapOf<String, Int>().apply { putAll(store.loadLimits()) }
    var usage by mutableStateOf<UsageResult?>(null)
    var hasUsageAccess by mutableStateOf(false)
        private set
    var canOverlay by mutableStateOf(false)
        private set

    // Google Calendar sync
    var calendarId by mutableLongStateOf(store.calendarId)
        private set
    var hasCalendarPermission by mutableStateOf(false)
        private set
    var lastSync by mutableLongStateOf(0L)
        private set
    /** Bumped when the app comes back to the foreground, to trigger a sync. */
    var syncTick by mutableIntStateOf(0)
    val syncOn: Boolean get() = calendarId > 0 && hasCalendarPermission

    fun setLimit(key: String, minutes: Int) {
        if (minutes <= 0) limits.remove(key) else limits[key] = minutes
        store.saveLimits(limits.toMap())
        Monitor.sync(ctx)
    }

    // Study stopwatch
    var studyStart by mutableLongStateOf(store.studyStart)
        private set
    val studying: Boolean get() = studyStart > 0L
    var studyDnd by mutableStateOf(store.studyDnd)
        private set
    var hasDndAccess by mutableStateOf(false)
        private set
    var onRequestDnd: () -> Unit = {}

    // Submissions
    val submissions = mutableStateListOf<Submission>().apply { addAll(store.loadSubmissions()) }

    // ---- tasks ----
    /** Normal tasks plus occurrences of repeating tasks on [date], in time order. */
    fun tasksOn(date: LocalDate): List<Task> =
        (tasks.filter { it.date == date } + series.filter { it.occursOn(date) }.map { it.occurrence(date) })
            .sortedBy { it.startMin }

    fun tasksBetween(from: LocalDate, to: LocalDate): List<Task> {
        val out = mutableListOf<Task>()
        var d = from
        while (!d.isAfter(to)) {
            out.addAll(tasksOn(d))
            d = d.plusDays(1)
        }
        return out
    }

    // ---- repeating tasks ----
    private fun reloadSeries(list: List<Series>) {
        if (series.toList() != list) {
            series.clear()
            series.addAll(list)
        }
    }

    /** Applies a change to the stored series under the same lock the calendar sync uses. */
    private fun mutateSeries(block: (MutableList<Series>) -> Unit) {
        synchronized(CalendarSync.LOCK) {
            val list = store.loadSeries().toMutableList()
            block(list)
            store.saveSeries(list)
            reloadSeries(list)
        }
    }

    /** The parts of a series the calendar knows about (colour and done ticks stay in the app). */
    private fun Series.calendarKey() =
        listOf(title, startDate, startMin, durationMin, rule, weekdayMask, endDate, skippedDates, carry)

    fun upsertSeries(s: Series) {
        var saved = s
        var old: Series? = null
        mutateSeries { list ->
            val i = list.indexOfFirst { it.id == s.id }
            old = if (i >= 0) list[i] else null
            var x = if (s.eventId <= 0 && i >= 0) s.copy(eventId = list[i].eventId) else s
            if (syncOn && (x.eventId <= 0 || i < 0 || list[i].calendarKey() != x.calendarKey())) {
                x = CalendarSync.pushSeries(ctx, store.calendarId, x)
            }
            saved = x
            if (i >= 0) list[i] = x else list.add(x)
        }
        old?.let { Reminders.cancelSeries(ctx, it) }
        Reminders.scheduleSeries(ctx, saved)
    }

    fun deleteSeries(id: Int) {
        var removed: Series? = null
        mutateSeries { list ->
            val s = list.firstOrNull { it.id == id }
            if (s != null && s.eventId > 0 && syncOn) CalendarSync.deleteEvent(ctx, s.eventId)
            removed = s
            list.removeAll { it.id == id }
        }
        removed?.let { Reminders.cancelSeries(ctx, it) }
    }

    fun skipOccurrence(id: Int, date: LocalDate) {
        val s = series.firstOrNull { it.id == id } ?: return
        upsertSeries(s.copy(skippedDates = s.skippedDates + date))
    }

    fun newId(): Int = store.nextId()

    private fun reloadFrom(list: List<Task>) {
        if (tasks.toList() != list) {
            tasks.clear()
            tasks.addAll(list)
        }
    }

    /** Applies a change to the stored tasks under the same lock the calendar sync uses. */
    private fun mutate(block: (MutableList<Task>) -> Unit) {
        synchronized(CalendarSync.LOCK) {
            val list = store.loadTasks().toMutableList()
            block(list)
            store.saveTasks(list)
            reloadFrom(list)
        }
    }

    fun upsert(t: Task) {
        var saved = t
        mutate { list ->
            val i = list.indexOfFirst { it.id == t.id }
            // the edit dialog doesn't know the calendar event id, keep the existing one
            var task = if (t.eventId <= 0 && i >= 0) t.copy(eventId = list[i].eventId) else t
            if (syncOn) task = CalendarSync.push(ctx, store.calendarId, task)
            saved = task
            if (i >= 0) list[i] = task else list.add(task)
        }
        Reminders.schedule(ctx, saved)
    }

    fun delete(id: Int) {
        mutate { list ->
            val t = list.firstOrNull { it.id == id }
            if (t != null && t.eventId > 0 && syncOn) CalendarSync.deleteEvent(ctx, t.eventId)
            list.removeAll { it.id == id }
        }
        Reminders.cancel(ctx, id)
    }

    fun toggleDone(t: Task) {
        if (t.seriesId >= 0) {
            val s = series.firstOrNull { it.id == t.seriesId } ?: return
            val done = if (t.date in s.doneDates) s.doneDates - t.date else s.doneDates + t.date
            upsertSeries(s.copy(doneDates = done))
        } else {
            upsert(t.copy(done = !t.done))
        }
    }

    fun titleOf(id: Int): String? =
        tasks.firstOrNull { it.id == id }?.title
            ?: tasksOn(LocalDate.now()).firstOrNull { it.id == id }?.title

    // ---- submissions ----
    private fun reloadSubmissions(list: List<Submission>) {
        if (submissions.toList() != list) {
            submissions.clear()
            submissions.addAll(list)
        }
    }

    private fun mutateSubmissions(block: (MutableList<Submission>) -> Unit) {
        synchronized(CalendarSync.LOCK) {
            val list = store.loadSubmissions().toMutableList()
            block(list)
            store.saveSubmissions(list)
            reloadSubmissions(list)
        }
    }

    fun upsertSubmission(sub: Submission) {
        var saved = sub
        mutateSubmissions { list ->
            val i = list.indexOfFirst { it.id == sub.id }
            var x = if (sub.eventId <= 0 && i >= 0) sub.copy(eventId = list[i].eventId) else sub
            if (syncOn) x = CalendarSync.pushSubmission(ctx, store.calendarId, x)
            saved = x
            if (i >= 0) list[i] = x else list.add(x)
        }
        Reminders.scheduleSubmission(ctx, saved)
    }

    fun deleteSubmission(id: Int) {
        mutateSubmissions { list ->
            val sub = list.firstOrNull { it.id == id }
            if (sub != null && sub.eventId > 0 && syncOn) CalendarSync.deleteEvent(ctx, sub.eventId)
            list.removeAll { it.id == id }
        }
        Reminders.cancelSubmission(ctx, id)
    }

    fun toggleSubmission(sub: Submission) = upsertSubmission(sub.copy(done = !sub.done))

    // ---- calendar sync ----
    suspend fun syncNow() {
        if (store.calendarId <= 0 || !CalendarSync.hasPermission(ctx)) return
        val changed = withContext(Dispatchers.IO) {
            try {
                CalendarSync.syncStore(ctx)
            } catch (e: Exception) {
                false
            }
        }
        if (changed) {
            synchronized(CalendarSync.LOCK) {
                reloadFrom(store.loadTasks())
                reloadSeries(store.loadSeries())
                reloadSubmissions(store.loadSubmissions())
            }
        }
        lastSync = System.currentTimeMillis()
    }

    fun selectCalendar(id: Long) {
        synchronized(CalendarSync.LOCK) {
            if (store.calendarId != id) {
                // tasks belong to the old calendar's events: detach so they are pushed to the new one
                val detached = store.loadTasks().map { it.copy(eventId = -1L) }
                store.saveTasks(detached)
                reloadFrom(detached)
                val detachedSeries = store.loadSeries().map { it.copy(eventId = -1L) }
                store.saveSeries(detachedSeries)
                reloadSeries(detachedSeries)
                val detachedSubs = store.loadSubmissions().map { it.copy(eventId = -1L) }
                store.saveSubmissions(detachedSubs)
                reloadSubmissions(detachedSubs)
            }
            store.calendarId = id
        }
        calendarId = id
        CalendarSync.armJob(ctx)
    }

    fun disableSync() {
        selectCalendar(-1L)
    }

    // ---- study stopwatch ----
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)

    fun setStudyDnd(on: Boolean) {
        studyDnd = on
        store.studyDnd = on
    }

    fun startStudy() {
        if (studying) return
        studyStart = System.currentTimeMillis()
        store.studyStart = studyStart
        if (studyDnd && nm.isNotificationPolicyAccessGranted) {
            store.dndRestore = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS) // silences everything except alarms
        }
    }

    fun stopStudy() {
        if (!studying) return
        val minutes = ((System.currentTimeMillis() - studyStart) / 60_000L).toInt()
        if (minutes >= 1) {
            sessions.add(FocusSession(LocalDate.now(), minutes, -1))
            store.saveSessions(sessions.toList())
        }
        studyStart = 0L
        store.studyStart = 0L
        val restore = store.dndRestore
        if (restore != -1 && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(if (restore <= 0) NotificationManager.INTERRUPTION_FILTER_ALL else restore)
        }
        store.dndRestore = -1
    }

    /** Called once a second from the UI. */
    fun tick() {
        now = System.currentTimeMillis()
    }

    fun refreshPermissions() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        needsNotif = !nm.areNotificationsEnabled()
        needsExact = Build.VERSION.SDK_INT >= 31 &&
            !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        hasUsageAccess = Usage.hasAccess(ctx)
        canOverlay = Usage.canOverlay(ctx)
        hasCalendarPermission = CalendarSync.hasPermission(ctx)
        hasDndAccess = nm.isNotificationPolicyAccessGranted
    }
}
