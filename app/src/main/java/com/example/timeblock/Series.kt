package com.example.timeblock

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

const val REPEAT_NONE = 0
const val REPEAT_DAILY = 1
const val REPEAT_WEEKLY = 2
const val REPEAT_MONTHLY = 3

const val WEEKDAYS_MASK = 0b0011111 // Mon..Fri
const val ALL_DAYS_MASK = 0b1111111

/** Bit for a weekday in [Series.weekdayMask]: Monday = bit 0 ... Sunday = bit 6. */
fun weekdayBit(d: DayOfWeek): Int = 1 shl (d.value - 1)

/**
 * A repeating task. Occurrences are not stored; they are worked out for any date, so a series
 * never runs out. Done and skipped days are remembered per date.
 */
data class Series(
    val id: Int,
    val title: String,
    val startDate: LocalDate,
    val startMin: Int,
    val durationMin: Int,
    val colorIndex: Int = 0,
    val rule: Int = REPEAT_DAILY,
    val weekdayMask: Int = 0,
    val endDate: LocalDate? = null,
    val doneDates: Set<LocalDate> = emptySet(),
    val skippedDates: Set<LocalDate> = emptySet(),
) {
    fun occursOn(d: LocalDate): Boolean {
        if (d < startDate) return false
        if (endDate != null && d > endDate) return false
        if (d in skippedDates) return false
        return when (rule) {
            REPEAT_DAILY -> true
            REPEAT_WEEKLY -> weekdayMask and weekdayBit(d.dayOfWeek) != 0
            REPEAT_MONTHLY -> d.dayOfMonth == minOf(startDate.dayOfMonth, d.lengthOfMonth())
            else -> false
        }
    }

    fun occurrence(d: LocalDate): Task = Task(
        id = occurrenceId(id, d),
        title = title,
        date = d,
        startMin = startMin,
        durationMin = durationMin,
        done = d in doneDates,
        colorIndex = colorIndex,
        seriesId = id,
    )

    fun summary(): String = when (rule) {
        REPEAT_DAILY -> "Every day"
        REPEAT_WEEKLY -> when (weekdayMask) {
            WEEKDAYS_MASK -> "Weekdays"
            ALL_DAYS_MASK -> "Every day"
            else -> "Every " + DayOfWeek.values()
                .filter { weekdayMask and weekdayBit(it) != 0 }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        REPEAT_MONTHLY -> "Monthly on day ${startDate.dayOfMonth}"
        else -> ""
    }
}

/** Stable, unique (and negative, so it never clashes with a normal task id) id for one occurrence. */
fun occurrenceId(seriesId: Int, d: LocalDate): Int =
    -(((seriesId and 0x7FFF) shl 16) or ((d.toEpochDay() - 18262L).toInt() and 0xFFFF))
