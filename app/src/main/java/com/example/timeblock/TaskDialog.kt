@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TaskDialog(
    state: PlannerState,
    initial: Task?,
    presetDate: LocalDate? = null,
    presetStart: Int? = null,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    fun nowMin(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    // Editing one occurrence of a repeating task edits the whole series.
    val series: Series? = initial?.seriesId?.takeIf { it >= 0 }
        ?.let { sid -> state.series.firstOrNull { it.id == sid } }
    val isSeries = series != null

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: presetDate ?: state.selectedDate) }
    var duration by remember { mutableStateOf(initial?.durationMin ?: 30) }
    var start by remember {
        mutableStateOf(
            initial?.startMin
                ?: presetStart
                ?: (findFreeSlot(state.tasksOn(date), 30, if (date == today) nowMin() else 0) ?: 9 * 60)
        )
    }
    var color by remember { mutableStateOf(initial?.colorIndex ?: 0) }
    var done by remember { mutableStateOf(initial?.done ?: false) }
    var repeat by remember { mutableStateOf(series?.rule ?: REPEAT_NONE) }
    var mask by remember {
        mutableStateOf(
            series?.weekdayMask?.takeIf { it != 0 } ?: weekdayBit((initial?.date ?: presetDate ?: state.selectedDate).dayOfWeek)
        )
    }
    var until by remember { mutableStateOf(series?.endDate) }

    val clash = state.tasksOn(date).firstOrNull {
        it.id != initial?.id && it.startMin < start + duration && it.endMin > start
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial == null -> "New task"
                    isSeries -> "Edit repeating task"
                    else -> "Edit task"
                }
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What do you need to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isSeries) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                android.app.DatePickerDialog(
                                    ctx,
                                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                    date.year, date.monthValue - 1, date.dayOfMonth,
                                ).show()
                            },
                        ) { Text(fmtDay(date), maxLines = 1) }
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            android.app.TimePickerDialog(
                                ctx,
                                { _, h, m -> start = h * 60 + m },
                                start / 60, start % 60, false,
                            ).show()
                        },
                    ) { Text(fmtTime(start), maxLines = 1) }
                }
                if (isSeries && series != null) {
                    Text(
                        "Repeats from ${fmtDay(series.startDate)}. Changes apply to every day of the series.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Duration", Modifier.weight(1f))
                    TextButton(onClick = { duration = (duration - 15).coerceAtLeast(15) }) { Text("−15") }
                    Text(fmtDuration(duration), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { duration = (duration + 15).coerceAtMost(12 * 60) }) { Text("+15") }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                        FilterChip(
                            selected = duration == m,
                            onClick = { duration = m },
                            label = { Text(fmtDuration(m)) },
                        )
                    }
                }

                if (!isSeries) {
                    TextButton(onClick = {
                        val others = state.tasksOn(date).filter { it.id != initial?.id }
                        findFreeSlot(others, duration, if (date == today) nowMin() else 0)?.let { start = it }
                    }) { Text("Find next free slot") }
                }

                if (clash != null) {
                    Text(
                        "Overlaps with “${clash.title}” (${fmtTime(clash.startMin)})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider()
                Text("Repeat", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        REPEAT_NONE to "Never",
                        REPEAT_DAILY to "Daily",
                        REPEAT_WEEKLY to "Weekly",
                        REPEAT_MONTHLY to "Monthly",
                    ).forEach { (rule, label) ->
                        FilterChip(
                            selected = repeat == rule,
                            onClick = { repeat = rule },
                            label = { Text(label) },
                        )
                    }
                }
                if (repeat == REPEAT_WEEKLY) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DayOfWeek.values().forEach { dow ->
                            val bit = weekdayBit(dow)
                            FilterChip(
                                selected = mask and bit != 0,
                                onClick = {
                                    val next = mask xor bit
                                    if (next != 0) mask = next // keep at least one day
                                },
                                label = { Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { mask = WEEKDAYS_MASK }) { Text("Weekdays") }
                        TextButton(onClick = { mask = ALL_DAYS_MASK }) { Text("Every day") }
                    }
                }
                if (repeat == REPEAT_MONTHLY) {
                    val day = (series?.startDate ?: date).dayOfMonth
                    Text(
                        "Repeats on day $day of each month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (repeat != REPEAT_NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ends")
                        OutlinedButton(onClick = {
                            val base = until ?: (series?.startDate ?: date).plusMonths(1)
                            android.app.DatePickerDialog(
                                ctx,
                                { _, y, m, d -> until = LocalDate.of(y, m + 1, d) },
                                base.year, base.monthValue - 1, base.dayOfMonth,
                            ).show()
                        }) { Text(until?.let { fmtDay(it) } ?: "Never") }
                        if (until != null) TextButton(onClick = { until = null }) { Text("Clear") }
                    }
                }
                HorizontalDivider()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(if (i == color) 34.dp else 28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (initial != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { done = !done },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = done, onCheckedChange = { done = it })
                        Text(if (isSeries) "Completed on ${fmtDay(date)}" else "Completed")
                    }
                }

                if (isSeries && series != null) {
                    TextButton(onClick = {
                        state.skipOccurrence(series.id, date)
                        onDismiss()
                    }) { Text("Skip ${fmtDay(date)} only") }
                    TextButton(onClick = {
                        state.deleteSeries(series.id)
                        onDismiss()
                    }) { Text("Delete the whole series", color = MaterialTheme.colorScheme.error) }
                }

                Text(
                    "You'll get a notification ${Reminders.LEAD_MIN} minutes before this starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val st = start.coerceIn(0, 1439)
                    val dur = duration.coerceAtMost(1440 - st)
                    if (repeat == REPEAT_NONE) {
                        if (series != null) state.deleteSeries(series.id) // series turned into a single task
                        state.upsert(
                            Task(
                                id = if (initial != null && initial.seriesId < 0) initial.id else state.newId(),
                                title = title.trim(),
                                date = date,
                                startMin = st,
                                durationMin = dur,
                                done = done,
                                colorIndex = color,
                            )
                        )
                    } else {
                        val keepDone = series?.doneDates ?: emptySet()
                        val s = Series(
                            id = series?.id ?: state.newId(),
                            title = title.trim(),
                            startDate = series?.startDate ?: date,
                            startMin = st,
                            durationMin = dur,
                            colorIndex = color,
                            rule = repeat,
                            weekdayMask = if (repeat == REPEAT_WEEKLY) mask else 0,
                            endDate = until,
                            doneDates = if (initial == null) emptySet() else if (done) keepDone + date else keepDone - date,
                            skippedDates = series?.skippedDates ?: emptySet(),
                        )
                        // a normal task turned into a repeating one
                        if (initial != null && initial.seriesId < 0) state.delete(initial.id)
                        state.upsertSeries(s)
                    }
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null && !isSeries) {
                    TextButton(onClick = {
                        state.delete(initial.id)
                        onDismiss()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun RepeatListDialog(state: PlannerState, onEdit: (Series) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeating tasks") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.series.isEmpty()) {
                    Text(
                        "Nothing repeats yet. When you add a task, choose Daily, Weekly or Monthly under Repeat.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.series.sortedBy { it.startMin }.forEach { s ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(TaskColors[s.colorIndex.coerceIn(0, TaskColors.lastIndex)])
                            )
                            Text(
                                s.title,
                                Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "${s.summary()} · ${fmtTime(s.startMin)} · ${fmtDuration(s.durationMin)}" +
                                (s.endDate?.let { " · until ${fmtDay(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(onClick = { onEdit(s) }) { Text("Edit") }
                            TextButton(onClick = { state.deleteSeries(s.id) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun ThemeDialog(state: PlannerState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Background colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BgThemes.withIndex().chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { (i, theme) ->
                            Column(
                                Modifier.clickable { state.setTheme(i) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(theme.swatch)
                                        .border(
                                            width = if (state.themeIndex == i) 3.dp else 1.dp,
                                            color = if (state.themeIndex == i) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.themeIndex == i) {
                                        val onSwatch = if (theme.swatch == Color(0xFF000000) || theme.swatch == Color(0xFFC62828)) {
                                            Color.White
                                        } else {
                                            Color.Black
                                        }
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = onSwatch)
                                    }
                                }
                                Text(theme.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
