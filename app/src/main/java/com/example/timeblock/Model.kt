package com.example.timeblock

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Task(
    val id: Int,
    val title: String,
    val date: LocalDate,
    val startMin: Int,      // minutes after midnight, 0..1439
    val durationMin: Int,
    val done: Boolean = false,
    val colorIndex: Int = 0,
    val eventId: Long = -1L, // Google Calendar event id, -1 if not synced
    val seriesId: Int = -1,  // >= 0 when this is one occurrence of a repeating task
    val carry: String = "",  // "take with me" items, comma separated
) {
    val endMin: Int get() = startMin + durationMin

    fun startMillis(): Long =
        date.atTime(startMin / 60, startMin % 60)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}

/** One study session recorded by the stopwatch. */
data class FocusSession(val date: LocalDate, val minutes: Int, val taskId: Int)

/** A submission / deadline. [timeMin] is minutes after midnight, or -1 when only the day matters. */
data class Submission(
    val id: Int,
    val title: String,
    val date: LocalDate,
    val timeMin: Int = -1,
    val note: String = "",
    val done: Boolean = false,
    val eventId: Long = -1L, // all-day Google Calendar event id, -1 if not synced
)

/** "charger,  ID card" -> ["charger", "ID card"] */
fun carryItems(carry: String): List<String> =
    carry.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }

fun cleanCarry(carry: String): String = carryItems(carry).joinToString(", ")

fun daysLeftText(due: LocalDate, today: LocalDate = LocalDate.now()): String {
    val n = java.time.temporal.ChronoUnit.DAYS.between(today, due).toInt()
    return when {
        n == 0 -> "Due today"
        n == 1 -> "Due tomorrow"
        n > 1 -> "$n days left"
        n == -1 -> "1 day overdue"
        else -> "${-n} days overdue"
    }
}

private val timeFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun fmtTime(min: Int): String = LocalTime.of((min / 60) % 24, min % 60).format(timeFormat)

fun fmtDuration(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

fun fmtDay(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
}

fun fmtFullDate(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))

/** First start time (multiple of 15 min) at or after [from] where [duration] fits between 8:00 and 22:00. */
fun findFreeSlot(
    existing: List<Task>,
    duration: Int,
    from: Int,
    dayStart: Int = 8 * 60,
    dayEnd: Int = 22 * 60,
): Int? {
    var start = (maxOf(from, dayStart) + 14) / 15 * 15
    while (start + duration <= dayEnd) {
        val clash = existing.firstOrNull { it.startMin < start + duration && it.endMin > start }
            ?: return start
        start = (clash.endMin + 14) / 15 * 15
    }
    return null
}

fun fmtHourShort(h: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return "$hh${if (h < 12) "a" else "p"}"
}
