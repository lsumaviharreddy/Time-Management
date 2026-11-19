@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PlanScreen(state: PlannerState) {
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    val date = state.selectedDate
    val dayTasks = state.tasksOn(date)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.needsNotif || state.needsExact) PermissionBanner(state)
            DateHeader(state, dayTasks, onSync = { showSync = true })
            NextUpCard(state)
            Timeline(
                state = state,
                tasks = dayTasks,
                onTaskClick = { editing = it },
                modifier = Modifier.weight(1f),
            )
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add task")
        }
    }

    if (showSync) SyncDialog(state) { showSync = false }
    if (creating) TaskDialog(state, null) { creating = false }
    editing?.let { t -> key(t.id) { TaskDialog(state, t) { editing = null } } }
}

@Composable
private fun PermissionBanner(state: PlannerState) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.needsNotif) {
                Text(
                    "Notifications are off, so you won't get the 5-minute reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { state.onRequestNotif() }) { Text("Turn on notifications") }
            }
            if (state.needsExact) {
                Text(
                    "Allow exact alarms so reminders arrive right on time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { state.onRequestExact() }) { Text("Allow exact alarms") }
            }
        }
    }
}

@Composable
private fun DateHeader(state: PlannerState, dayTasks: List<Task>, onSync: () -> Unit) {
    val date = state.selectedDate
    val today = LocalDate.now()
    val planned = dayTasks.sumOf { it.durationMin }
    val done = dayTasks.count { it.done }
    val summary = if (dayTasks.isEmpty()) {
        fmtFullDate(date)
    } else {
        "${fmtFullDate(date)} · ${dayTasks.size} tasks · ${fmtDuration(planned)} planned · $done done"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { state.selectedDate = date.minusDays(1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmtDay(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (date != today) {
            TextButton(onClick = { state.selectedDate = today }) { Text("Today") }
        }
        IconButton(onClick = { state.selectedDate = date.plusDays(1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
        }
        IconButton(onClick = onSync) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Google Calendar sync",
                tint = if (state.syncOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextUpCard(state: PlannerState) {
    val nowMs = state.nowMinute * 60_000L
    val next = state.tasks
        .filter { !it.done && it.startMillis() > nowMs }
        .minByOrNull { it.startMillis() }
    val current = state.tasks.firstOrNull {
        !it.done && it.startMillis() <= nowMs && nowMs < it.startMillis() + it.durationMin * 60_000L
    }
    if (next == null && current == null) return

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                if (current != null) {
                    Text("Now: ${current.title}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Until ${fmtTime(current.endMin % 1440)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (next != null) {
                    val mins = ((next.startMillis() - nowMs) / 60_000L).toInt()
                    val whenText = if (mins < 60) "in $mins min" else "in ${fmtDuration(mins)}"
                    val dayText = if (next.date == LocalDate.now()) "" else "${fmtDay(next.date)} "
                    Text(
                        "Next: ${next.title}",
                        fontWeight = if (current == null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$dayText${fmtTime(next.startMin)} ($whenText) · reminder ${Reminders.LEAD_MIN} min before",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private data class Placed(val task: Task, val lane: Int, val lanes: Int)

/** Puts overlapping tasks side by side. */
private fun layoutLanes(tasks: List<Task>): List<Placed> {
    val result = mutableListOf<Placed>()
    var cluster = mutableListOf<Pair<Task, Int>>()
    var laneEnds = mutableListOf<Int>()
    var clusterEnd = -1

    fun flush() {
        val n = laneEnds.size
        cluster.forEach { result.add(Placed(it.first, it.second, n)) }
        cluster = mutableListOf()
        laneEnds = mutableListOf()
        clusterEnd = -1
    }

    for (t in tasks.sortedBy { it.startMin }) {
        if (cluster.isNotEmpty() && t.startMin >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= t.startMin }
        if (lane == -1) {
            laneEnds.add(t.endMin)
            lane = laneEnds.size - 1
        } else {
            laneEnds[lane] = t.endMin
        }
        cluster.add(t to lane)
        clusterEnd = maxOf(clusterEnd, t.endMin)
    }
    flush()
    return result
}

@Composable
private fun Timeline(
    state: PlannerState,
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourH: Dp = 64.dp
    val density = LocalDensity.current
    val date = state.selectedDate
    val isToday = date == LocalDate.now()
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    key(date) {
        val firstHour = if (isToday) {
            (LocalTime.now().hour - 1).coerceAtLeast(0)
        } else {
            ((tasks.minOfOrNull { it.startMin / 60 } ?: 8) - 1).coerceAtLeast(0)
        }
        val scroll = rememberScrollState(with(density) { (hourH * firstHour).roundToPx() })

        Box(modifier.fillMaxWidth().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(hourH * 24)) {
                for (h in 0..23) {
                    Box(
                        Modifier
                            .offset(y = hourH * h)
                            .fillMaxWidth()
                            .height(hourH)
                    ) {
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = lineColor)
                        Text(
                            fmtTime(h * 60),
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                BoxWithConstraints(
                    Modifier
                        .padding(start = 60.dp, end = 8.dp)
                        .fillMaxSize()
                ) {
                    val fullWidth = maxWidth
                    layoutLanes(tasks).forEach { p ->
                        val t = p.task
                        val laneW = fullWidth / p.lanes
                        val color = TaskColors[t.colorIndex.coerceIn(0, TaskColors.lastIndex)]
                        val blockH = (hourH * (t.durationMin / 60f)).coerceAtLeast(28.dp)
                        Box(
                            Modifier
                                .offset(x = laneW * p.lane, y = hourH * (t.startMin / 60f))
                                .width(laneW - 2.dp)
                                .height(blockH - 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color.copy(alpha = if (t.done) 0.4f else 0.92f))
                                .clickable { onTaskClick(t) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (t.done) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Done",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        t.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                                    )
                                }
                                if (t.durationMin >= 30) {
                                    Text(
                                        "${fmtTime(t.startMin)} – ${fmtTime(t.endMin % 1440)}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                if (isToday) {
                    val minuteOfDay = remember(state.nowMinute) {
                        LocalTime.now().let { it.hour * 60 + it.minute }
                    }
                    Row(
                        Modifier
                            .offset(y = hourH * (minuteOfDay / 60f) - 4.dp)
                            .fillMaxWidth()
                            .padding(start = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE53935)))
                        Box(Modifier.weight(1f).height(2.dp).background(Color(0xFFE53935)))
                    }
                }
            }
        }
    }
}

@Composable
fun TaskDialog(state: PlannerState, initial: Task?, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = LocalDate.now()
    fun nowMin(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: state.selectedDate) }
    var duration by remember { mutableStateOf(initial?.durationMin ?: 30) }
    var start by remember {
        mutableStateOf(
            initial?.startMin
                ?: (findFreeSlot(state.tasksOn(date), 30, if (date == today) nowMin() else 0) ?: 9 * 60)
        )
    }
    var color by remember { mutableStateOf(initial?.colorIndex ?: 0) }
    var done by remember { mutableStateOf(initial?.done ?: false) }

    val clash = state.tasksOn(date).firstOrNull {
        it.id != initial?.id && it.startMin < start + duration && it.endMin > start
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New task" else "Edit task") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What do you need to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            android.app.DatePickerDialog(
                                ctx,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                date.year, date.monthValue - 1, date.dayOfMonth,
                            ).show()
                        },
                    ) { Text(fmtDay(date), maxLines = 1) }
                    androidx.compose.material3.OutlinedButton(
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
                        androidx.compose.material3.FilterChip(
                            selected = duration == m,
                            onClick = { duration = m },
                            label = { Text(fmtDuration(m)) },
                        )
                    }
                }

                TextButton(onClick = {
                    val others = state.tasksOn(date).filter { it.id != initial?.id }
                    findFreeSlot(others, duration, if (date == today) nowMin() else 0)?.let { start = it }
                }) { Text("Find next free slot") }

                if (clash != null) {
                    Text(
                        "Overlaps with “${clash.title}” (${fmtTime(clash.startMin)})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        androidx.compose.material3.Checkbox(checked = done, onCheckedChange = { done = it })
                        Text("Completed")
                    }
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
                    val t = Task(
                        id = initial?.id ?: state.newId(),
                        title = title.trim(),
                        date = date,
                        startMin = start.coerceIn(0, 1439),
                        durationMin = duration.coerceAtMost(1440 - start.coerceIn(0, 1439)),
                        done = done,
                        colorIndex = color,
                    )
                    state.upsert(t)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
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
