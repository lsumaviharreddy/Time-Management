@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

@Composable
fun PlanScreen(state: PlannerState) {
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var addAt by remember { mutableStateOf<Pair<LocalDate, Int?>?>(null) }
    var showSync by remember { mutableStateOf(false) }
    var showRepeats by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.needsNotif || state.needsExact) PermissionBanner(state)
            PlanHeader(
                state,
                onSync = { showSync = true },
                onRepeats = { showRepeats = true },
                onTheme = { showTheme = true },
            )
            ModeChips(state)
            when (state.viewMode) {
                0 -> {
                    NextUpCard(state)
                    Timeline(
                        state = state,
                        tasks = state.tasksOn(state.selectedDate),
                        onTaskClick = { editing = it },
                        onAddAt = { minutes -> addAt = state.selectedDate to minutes },
                        modifier = Modifier.weight(1f),
                    )
                }
                1 -> WeekView(
                    state,
                    onTaskClick = { editing = it },
                    onAddAt = { d, minutes -> addAt = d to minutes },
                    modifier = Modifier.weight(1f),
                )
                else -> MonthView(
                    state,
                    onTaskClick = { editing = it },
                    onAddOn = { d -> addAt = d to null },
                    modifier = Modifier.weight(1f),
                )
            }
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
    if (showTheme) ThemeDialog(state) { showTheme = false }
    if (showRepeats) {
        RepeatListDialog(
            state,
            onEdit = { s ->
                showRepeats = false
                editing = s.occurrence(s.startDate)
            },
            onDismiss = { showRepeats = false },
        )
    }
    if (creating) TaskDialog(state, null) { creating = false }
    addAt?.let { (d, minutes) ->
        key(d, minutes) { TaskDialog(state, null, presetDate = d, presetStart = minutes) { addAt = null } }
    }
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
private fun PlanHeader(
    state: PlannerState,
    onSync: () -> Unit,
    onRepeats: () -> Unit,
    onTheme: () -> Unit,
) {
    val date = state.selectedDate
    val today = LocalDate.now()
    val mode = state.viewMode
    val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    val title: String
    val summary: String
    when (mode) {
        0 -> {
            val ts = state.tasksOn(date)
            title = fmtDay(date)
            summary = if (ts.isEmpty()) {
                fmtFullDate(date)
            } else {
                "${fmtFullDate(date)} · ${ts.size} tasks · ${fmtDuration(ts.sumOf { it.durationMin })} planned · ${ts.count { it.done }} done"
            }
        }
        1 -> {
            val monday = mondayOf(date)
            val sunday = monday.plusDays(6)
            val ts = state.tasksBetween(monday, sunday)
            title = if (monday == mondayOf(today)) "This week" else "Week of ${monday.format(dayFmt)}"
            summary = "${monday.format(dayFmt)} – ${sunday.format(dayFmt)} · ${ts.size} tasks · ${ts.count { it.done }} done"
        }
        else -> {
            val first = date.withDayOfMonth(1)
            val ts = state.tasksBetween(first, first.plusDays(first.lengthOfMonth() - 1L))
            title = date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            summary = "${ts.size} tasks · ${ts.count { it.done }} done"
        }
    }

    fun shift(dir: Long) {
        state.selectedDate = when (mode) {
            0 -> date.plusDays(dir)
            1 -> date.plusWeeks(dir)
            else -> date.plusMonths(dir)
        }
    }

    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { shift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        IconButton(onClick = { shift(1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Repeating tasks (${state.series.size})") },
                    onClick = { menu = false; onRepeats() },
                )
                DropdownMenuItem(
                    text = { Text(if (state.syncOn) "Google Calendar sync (on)" else "Google Calendar sync") },
                    onClick = { menu = false; onSync() },
                )
                DropdownMenuItem(
                    text = { Text("Background colour") },
                    onClick = { menu = false; onTheme() },
                )
            }
        }
    }
}

@Composable
private fun ModeChips(state: PlannerState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Day", "Week", "Month").forEachIndexed { i, label ->
            FilterChip(
                selected = state.viewMode == i,
                onClick = { state.viewMode = i },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun NextUpCard(state: PlannerState) {
    val nowMs = state.nowMinute * 60_000L
    val today = LocalDate.now()
    val upcoming = state.tasksBetween(today, today.plusDays(14))
    val next = upcoming
        .filter { !it.done && it.startMillis() > nowMs }
        .minByOrNull { it.startMillis() }
    val current = upcoming.firstOrNull {
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
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Column {
                if (current != null) {
                    Text(
                        "Now: ${current.title}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Until ${fmtTime(current.endMin % 1440)}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (next != null) {
                    val mins = ((next.startMillis() - nowMs) / 60_000L).toInt()
                    val whenText = if (mins < 60) "in $mins min" else "in ${fmtDuration(mins)}"
                    val dayText = if (next.date == today) "" else "${fmtDay(next.date)} "
                    Text(
                        "Next: ${next.title}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = if (current == null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$dayText${fmtTime(next.startMin)} ($whenText) · reminder ${Reminders.LEAD_MIN} min before",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

internal data class Placed(val task: Task, val lane: Int, val lanes: Int)

/** Puts overlapping tasks side by side. */
internal fun layoutLanes(tasks: List<Task>): List<Placed> {
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

/** One coloured task block. [compact] shows only the title in small type (week view). */
@Composable
internal fun TaskBlock(t: Task, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    val color = TaskColors[t.colorIndex.coerceIn(0, TaskColors.lastIndex)]
    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 4.dp else 8.dp))
            .background(color.copy(alpha = if (t.done) 0.4f else 0.92f))
            .clickable { onClick() }
            .padding(horizontal = if (compact) 2.dp else 8.dp, vertical = if (compact) 1.dp else 4.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.done && !compact) {
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
                    fontSize = if (compact) 9.sp else 13.sp,
                    lineHeight = if (compact) 10.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                )
            }
            if (!compact && t.durationMin >= 30) {
                Text(
                    "${fmtTime(t.startMin)} – ${fmtTime(t.endMin % 1440)}" + if (t.seriesId >= 0) " · repeats" else "",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Timeline(
    state: PlannerState,
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onAddAt: (Int) -> Unit,
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
            val hourPx = with(density) { hourH.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(hourH * 24)
                    .pointerInput(date) {
                        // tap an empty spot to add a task at that time (snapped down to 15 minutes)
                        detectTapGestures { offset ->
                            val minutes = (offset.y / hourPx * 60f).toInt() / 15 * 15
                            onAddAt(minutes.coerceIn(0, 23 * 60 + 45))
                        }
                    }
            ) {
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
                        val blockH = (hourH * (t.durationMin / 60f)).coerceAtLeast(28.dp)
                        TaskBlock(
                            t,
                            Modifier
                                .offset(x = laneW * p.lane, y = hourH * (t.startMin / 60f))
                                .width(laneW - 2.dp)
                                .height(blockH - 2.dp),
                            compact = false,
                            onClick = { onTaskClick(t) },
                        )
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
