package com.example.timeblock

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalInfo(val id: Long, val name: String, val account: String)

data class CalEvent(val id: Long, val title: String, val start: Long, val end: Long)

/**
 * Two-way sync between TimeBlock tasks and one Google Calendar.
 *
 * - Tasks you add/edit/delete in the app are written to the calendar straight away.
 * - Events added/edited/deleted in the calendar (e.g. on your laptop) are pulled into the app.
 * - A finished task is marked with a leading check mark in the event title, so "done" syncs too.
 * - Recurring and all-day events are ignored.
 */
object CalendarSync {
    val LOCK = Any()
    private const val PAST_DAYS = 14L
    private const val FUTURE_DAYS = 120L
    private const val DONE_PREFIX = "✓ "
    private const val JOB_ID = 4242

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun listCalendars(ctx: Context): List<CalInfo> {
        if (!hasPermission(ctx)) return emptyList()
        val result = mutableListOf<CalInfo>()
        try {
            ctx.contentResolver.query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME),
                "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result.add(CalInfo(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: ""))
                }
            }
        } catch (e: Exception) {
            // ignore, empty list
        }
        return result
    }

    // ---- writing to the calendar ----

    private fun eventTitle(t: Task) = if (t.done) DONE_PREFIX + t.title else t.title

    private fun timeValues(t: Task): ContentValues {
        val s = t.startMillis()
        return ContentValues().apply {
            put(Events.TITLE, eventTitle(t))
            put(Events.DTSTART, s)
            put(Events.DTEND, s + t.durationMin * 60_000L)
            put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        }
    }

    private fun insertEvent(ctx: Context, calId: Long, t: Task): Long = try {
        val values = timeValues(t).apply { put(Events.CALENDAR_ID, calId) }
        ctx.contentResolver.insert(Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: -1L
    } catch (e: Exception) {
        -1L
    }

    private fun updateEvent(ctx: Context, t: Task): Boolean = try {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, t.eventId)
        ctx.contentResolver.update(uri, timeValues(t), null, null) > 0
    } catch (e: Exception) {
        false
    }

    fun deleteEvent(ctx: Context, eventId: Long) {
        try {
            ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Writes [t] to the calendar (insert or update) and returns it with its event id filled in. */
    fun push(ctx: Context, calId: Long, t: Task): Task {
        if (t.eventId > 0 && updateEvent(ctx, t)) return t
        val id = insertEvent(ctx, calId, t)
        return t.copy(eventId = id)
    }

    // ---- reading from the calendar ----

    private fun readEvents(ctx: Context, calId: Long, fromMs: Long, toMs: Long): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val sel = "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.ALL_DAY} = 0 " +
            "AND ${Events.RRULE} IS NULL AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ?"
        ctx.contentResolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.TITLE, Events.DTSTART, Events.DTEND),
            sel,
            arrayOf(calId.toString(), fromMs.toString(), toMs.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val start = c.getLong(2)
                val end = if (c.isNull(3)) start + 30 * 60_000L else c.getLong(3)
                out.add(CalEvent(c.getLong(0), c.getString(1) ?: "", start, end))
            }
        }
        return out
    }

    private fun applyEvent(t: Task, ev: CalEvent): Task {
        val start = Instant.ofEpochMilli(ev.start).atZone(ZoneId.systemDefault())
        val startMin = start.hour * 60 + start.minute
        val dur = ((ev.end - ev.start) / 60_000L).toInt().coerceAtLeast(15).coerceAtMost(1440 - startMin)
        val raw = ev.title.trim()
        val title = raw.removePrefix(DONE_PREFIX.trim()).trim().ifEmpty { "(no title)" }
        return t.copy(
            title = title,
            date = start.toLocalDate(),
            startMin = startMin,
            durationMin = dur,
            done = raw.startsWith(DONE_PREFIX.trim()),
        )
    }

    /**
     * Merges calendar and app. Runs from the app and from the background job, so it works on the
     * stored data directly. Returns true if the stored tasks changed.
     */
    fun syncStore(ctx: Context): Boolean = synchronized(LOCK) {
        val store = Store(ctx)
        val calId = store.calendarId
        if (calId <= 0 || !hasPermission(ctx)) return@synchronized false

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.minusDays(PAST_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(FUTURE_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = readEvents(ctx, calId, from, to)
        val byId = events.associateBy { it.id }

        var changed = false
        val out = ArrayList<Task>()
        for (t in store.loadTasks()) {
            if (t.eventId > 0) {
                val ev = byId[t.eventId]
                if (ev == null) {
                    if (t.startMillis() in from until to) {
                        // deleted in the calendar
                        Reminders.cancel(ctx, t.id)
                        changed = true
                    } else {
                        out.add(t)
                    }
                } else {
                    val merged = applyEvent(t, ev)
                    if (merged != t) {
                        changed = true
                        Reminders.schedule(ctx, merged)
                    }
                    out.add(merged)
                }
            } else if (t.date >= today.minusDays(PAST_DAYS)) {
                // created in the app before sync was on
                val pushed = push(ctx, calId, t)
                if (pushed.eventId != t.eventId) changed = true
                out.add(pushed)
            } else {
                out.add(t)
            }
        }

        // new events from the calendar
        val known = out.map { it.eventId }.filter { it > 0 }.toSet()
        for (ev in events) {
            if (ev.id in known) continue
            val task = applyEvent(
                Task(id = store.nextId(), title = "", date = today, startMin = 0, durationMin = 30, eventId = ev.id),
                ev,
            )
            out.add(task)
            Reminders.schedule(ctx, task)
            changed = true
        }

        if (changed) store.saveTasks(out)
        changed
    }

    // ---- background trigger ----

    /** (Re)arms a job that runs when the calendar changes, so laptop edits reach the app and its reminders. */
    fun armJob(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (Store(ctx).calendarId <= 0 || !hasPermission(ctx)) {
            js.cancel(JOB_ID)
            return
        }
        val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, CalendarJobService::class.java))
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(Events.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)
            )
            .setTriggerContentUpdateDelay(2_000)
            .setTriggerContentMaxDelay(10_000)
            .build()
        js.schedule(info)
    }
}

class CalendarJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                CalendarSync.syncStore(applicationContext)
            } catch (e: Exception) {
                // try again on the next change
            }
            CalendarSync.armJob(applicationContext) // content-trigger jobs fire once, so re-arm
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
