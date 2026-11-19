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
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class CalInfo(val id: Long, val name: String, val account: String)

data class CalEvent(val id: Long, val title: String, val start: Long, val end: Long)

/** A recurring event as stored in the calendar. */
private class RecEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val duration: String?,
    val rrule: String,
    val exdate: String?,
)

/** A recurring event translated into the app's own repeat model. */
private class ParsedSeries(
    val title: String,
    val startDate: LocalDate,
    val startMin: Int,
    val durationMin: Int,
    val rule: Int,
    val mask: Int,
    val endDate: LocalDate?,
    val skipped: Set<LocalDate>,
)

/**
 * Two-way sync between TimeBlock and one Google Calendar.
 *
 * - Tasks you add/edit/delete in the app are written to the calendar straight away, and the other way round.
 * - Repeating tasks become recurring events (daily, weekly on chosen days, monthly on a day).
 *   Skipped days become excluded dates. Recurring events with other patterns (every 2 weeks,
 *   yearly, "first Monday", a fixed number of repeats) are left alone.
 * - A finished task is marked with a leading check mark in the event title, so "done" syncs too.
 *   Done ticks on single days of a repeating task stay in the app.
 * - All-day events are ignored.
 */
object CalendarSync {
    val LOCK = Any()
    private const val PAST_DAYS = 14L
    private const val FUTURE_DAYS = 120L
    private const val DONE_PREFIX = "✓ "
    private const val JOB_ID = 4242
    private const val STATUS_CANCELED = 2

    private val DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    private val UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val NOT_CANCELED = "(${Events.STATUS} IS NULL OR ${Events.STATUS} != $STATUS_CANCELED)"

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

    // ---- writing single tasks ----

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

    private fun insertValues(ctx: Context, values: ContentValues): Long = try {
        ctx.contentResolver.insert(Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: -1L
    } catch (e: Exception) {
        -1L
    }

    private fun updateValues(ctx: Context, eventId: Long, values: ContentValues): Boolean = try {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        ctx.contentResolver.update(uri, values, null, null) > 0
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
        if (t.eventId > 0 && updateValues(ctx, t.eventId, timeValues(t))) return t
        val id = insertValues(ctx, timeValues(t).apply { put(Events.CALENDAR_ID, calId) })
        return t.copy(eventId = id)
    }

    // ---- writing repeating tasks ----

    private fun utcStamp(date: LocalDate, minuteOfDay: Int): String =
        date.atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .format(UTC_STAMP)

    private fun rruleOf(s: Series): String {
        val base = when (s.rule) {
            REPEAT_DAILY -> "FREQ=DAILY"
            REPEAT_WEEKLY -> {
                val days = (0..6).filter { s.weekdayMask and (1 shl it) != 0 }
                    .ifEmpty { listOf(s.startDate.dayOfWeek.value - 1) }
                "FREQ=WEEKLY;BYDAY=" + days.joinToString(",") { DAY_CODES[it] }
            }
            else -> "FREQ=MONTHLY;BYMONTHDAY=${s.startDate.dayOfMonth}"
        }
        val until = s.endDate?.let { ";UNTIL=" + utcStamp(it, 23 * 60 + 59) } ?: ""
        return base + until
    }

    private fun seriesValues(s: Series): ContentValues {
        val zone = ZoneId.systemDefault()
        val first = s.firstOccurrence()
        val startMs = first.atTime(s.startMin / 60, s.startMin % 60).atZone(zone).toInstant().toEpochMilli()
        return ContentValues().apply {
            put(Events.TITLE, s.title)
            put(Events.DTSTART, startMs)
            putNull(Events.DTEND) // recurring events use DURATION instead
            put(Events.DURATION, "P${s.durationMin * 60}S")
            put(Events.EVENT_TIMEZONE, zone.id)
            put(Events.RRULE, rruleOf(s))
            if (s.skippedDates.isEmpty()) {
                putNull(Events.EXDATE)
            } else {
                put(Events.EXDATE, s.skippedDates.sorted().joinToString(",") { utcStamp(it, s.startMin) })
            }
        }
    }

    /** Writes a repeating task as one recurring event and returns it with its event id filled in. */
    fun pushSeries(ctx: Context, calId: Long, s: Series): Series {
        if (s.eventId > 0 && updateValues(ctx, s.eventId, seriesValues(s))) return s
        val id = insertValues(ctx, seriesValues(s).apply { put(Events.CALENDAR_ID, calId) })
        return s.copy(eventId = id)
    }

    // ---- reading from the calendar ----

    private fun readEvents(ctx: Context, calId: Long, fromMs: Long, toMs: Long): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val sel = "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.ALL_DAY} = 0 " +
            "AND ${Events.RRULE} IS NULL AND $NOT_CANCELED " +
            "AND ${Events.DTSTART} >= ? AND ${Events.DTSTART} < ?"
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

    private fun readRecurring(ctx: Context, calId: Long, toMs: Long): List<RecEvent> {
        val out = mutableListOf<RecEvent>()
        val sel = "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.ALL_DAY} = 0 " +
            "AND ${Events.RRULE} IS NOT NULL AND $NOT_CANCELED AND ${Events.DTSTART} < ?"
        ctx.contentResolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.TITLE, Events.DTSTART, Events.DURATION, Events.RRULE, Events.EXDATE),
            sel,
            arrayOf(calId.toString(), toMs.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    RecEvent(
                        id = c.getLong(0),
                        title = c.getString(1) ?: "",
                        start = c.getLong(2),
                        duration = c.getString(3),
                        rrule = c.getString(4) ?: continue,
                        exdate = c.getString(5),
                    )
                )
            }
        }
        return out
    }

    /**
     * Days where a single occurrence of a recurring event was changed or cancelled in the calendar
     * ("this event only"). Changed ones come back as normal events; the day is hidden from the series.
     */
    private fun readExceptions(ctx: Context, calId: Long): Map<Long, Set<LocalDate>> {
        val out = HashMap<Long, MutableSet<LocalDate>>()
        val zone = ZoneId.systemDefault()
        ctx.contentResolver.query(
            Events.CONTENT_URI,
            arrayOf(Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.ORIGINAL_ID} IS NOT NULL",
            arrayOf(calId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.isNull(0) || c.isNull(1)) continue
                val day = Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDate()
                out.getOrPut(c.getLong(0)) { mutableSetOf() }.add(day)
            }
        }
        return out
    }

    private fun parseDurationMin(d: String?): Int {
        if (d.isNullOrBlank()) return 30
        var seconds = 0L
        for (m in Regex("(\\d+)([WDHMS])").findAll(d.substringAfter('P'))) {
            val n = m.groupValues[1].toLong()
            seconds += when (m.groupValues[2]) {
                "W" -> n * 7 * 86_400
                "D" -> n * 86_400
                "H" -> n * 3_600
                "M" -> n * 60
                else -> n
            }
        }
        return (seconds / 60).toInt().coerceIn(15, 1440)
    }

    /** Date part of an iCalendar date / date-time value, in the phone's time zone. */
    private fun stampToDate(raw: String): LocalDate? {
        val v = raw.substringAfterLast(':').trim()
        return try {
            when {
                v.endsWith("Z") ->
                    LocalDateTime.parse(v, UTC_STAMP).atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                v.contains("T") -> LocalDateTime.parse(v, LOCAL_STAMP).toLocalDate()
                v.length == 8 -> LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Returns null for repeat patterns the app can't show (every 2 weeks, yearly, counted, ...). */
    private fun parseRecurring(ev: RecEvent): ParsedSeries? {
        val parts = ev.rrule.removePrefix("RRULE:").split(";").filter { it.contains("=") }
            .associate { it.substringBefore("=").uppercase() to it.substringAfter("=") }
        if (parts.keys.any { it !in setOf("FREQ", "BYDAY", "BYMONTHDAY", "UNTIL", "WKST") }) return null

        val start = Instant.ofEpochMilli(ev.start).atZone(ZoneId.systemDefault())
        val startDate = start.toLocalDate()
        var mask = 0
        val rule = when (parts["FREQ"]) {
            "DAILY" -> {
                if (parts.containsKey("BYDAY") || parts.containsKey("BYMONTHDAY")) return null
                REPEAT_DAILY
            }
            "WEEKLY" -> {
                if (parts.containsKey("BYMONTHDAY")) return null
                val codes = parts["BYDAY"]?.split(",")
                if (codes == null) {
                    mask = weekdayBit(startDate.dayOfWeek)
                } else {
                    for (code in codes) {
                        val i = DAY_CODES.indexOf(code.trim())
                        if (i < 0) return null // e.g. 1MO, -1FR
                        mask = mask or (1 shl i)
                    }
                }
                REPEAT_WEEKLY
            }
            "MONTHLY" -> {
                if (parts.containsKey("BYDAY")) return null
                val dom = parts["BYMONTHDAY"]
                if (dom != null && dom.toIntOrNull() != startDate.dayOfMonth) return null
                REPEAT_MONTHLY
            }
            else -> return null
        }
        return ParsedSeries(
            title = ev.title.trim().ifEmpty { "(no title)" },
            startDate = startDate,
            startMin = start.hour * 60 + start.minute,
            durationMin = parseDurationMin(ev.duration),
            rule = rule,
            mask = if (rule == REPEAT_WEEKLY) mask else 0,
            endDate = parts["UNTIL"]?.let { stampToDate(it) },
            skipped = (ev.exdate ?: "").split(",").mapNotNull { if (it.isBlank()) null else stampToDate(it) }.toSet(),
        )
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

    private fun mergeSeries(s: Series, p: ParsedSeries, exceptions: Set<LocalDate>?): Series = s.copy(
        title = p.title,
        startDate = p.startDate,
        startMin = p.startMin,
        durationMin = p.durationMin,
        rule = p.rule,
        weekdayMask = p.mask,
        endDate = p.endDate,
        skippedDates = p.skipped + (exceptions ?: emptySet()),
    )

    /**
     * Merges calendar and app. Runs from the app and from the background job, so it works on the
     * stored data directly. Returns true if anything stored changed.
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
        val recurring = readRecurring(ctx, calId, to)
        val exceptions = readExceptions(ctx, calId)
        val byId = events.associateBy { it.id }

        // ---- single tasks ----
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

        // new single events from the calendar
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

        // ---- repeating tasks ----
        var seriesChanged = false
        val parsed = HashMap<Long, ParsedSeries>()
        for (ev in recurring) parseRecurring(ev)?.let { parsed[ev.id] = it }
        val recurringIds = recurring.map { it.id }.toSet()

        val outSeries = ArrayList<Series>()
        for (s in store.loadSeries()) {
            if (s.eventId > 0) {
                val p = parsed[s.eventId]
                when {
                    p != null -> {
                        val merged = mergeSeries(s, p, exceptions[s.eventId])
                        if (merged != s) {
                            seriesChanged = true
                            Reminders.cancelSeries(ctx, s)
                            Reminders.scheduleSeries(ctx, merged)
                        }
                        outSeries.add(merged)
                    }
                    s.eventId in recurringIds -> outSeries.add(s) // changed to a pattern the app can't show: keep ours
                    else -> {
                        // deleted in the calendar
                        Reminders.cancelSeries(ctx, s)
                        seriesChanged = true
                    }
                }
            } else {
                // created in the app before sync was on
                val pushed = pushSeries(ctx, calId, s)
                if (pushed.eventId != s.eventId) seriesChanged = true
                outSeries.add(pushed)
            }
        }
        // new recurring events from the calendar
        for ((eventId, p) in parsed) {
            if (outSeries.any { it.eventId == eventId }) continue
            val series = mergeSeries(
                Series(id = store.nextId(), title = "", startDate = today, startMin = 0, durationMin = 30, eventId = eventId),
                p,
                exceptions[eventId],
            )
            outSeries.add(series)
            Reminders.scheduleSeries(ctx, series)
            seriesChanged = true
        }
        if (seriesChanged) store.saveSeries(outSeries)

        changed || seriesChanged
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
