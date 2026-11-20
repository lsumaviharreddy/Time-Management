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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Seven day columns on a shared time axis. Tap a day header to open that day. */
@Composable
fun WeekView(
    state: PlannerState,
    onTaskClick: (Task) -> Unit,
    onGroupClick: (List<Task>) -> Unit,
    onAddAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val monday = mondayOf(state.selectedDate)
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val hourH: Dp = 48.dp
    val labelW: Dp = 30.dp
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val weekTasks = days.map { state.tasksOn(it) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = labelW)) {
            days.forEach { d ->
                val isToday = d == today
                Column(
                    Modifier
                        .weight(1f)
                        .clickable {
                            state.selectedDate = d
                            state.viewMode = 0
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.format(dayFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = lineColor)

        key(monday) {
            val firstHour = if (monday <= today && today <= monday.plusDays(6)) {
                (LocalTime.now().hour - 1).coerceAtLeast(0)
            } else {
                ((weekTasks.flatten().minOfOrNull { it.startMin / 60 } ?: 8) - 1).coerceAtLeast(0)
            }
            val scroll = rememberScrollState(with(density) { (hourH * firstHour).roundToPx() })

            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                val hourPx = with(density) { hourH.toPx() }
                val labelPx = with(density) { labelW.toPx() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(hourH * 24)
                        .pointerInput(monday) {
                            // tap an empty spot in a day column to add a task there
                            detectTapGestures { offset ->
                                val x = offset.x - labelPx
                                if (x >= 0f) {
                                    val col = (x / ((size.width - labelPx) / 7f)).toInt().coerceIn(0, 6)
                                    val minutes = (offset.y / hourPx * 60f).toInt() / 15 * 15
                                    onAddAt(days[col], minutes.coerceIn(0, 23 * 60 + 45))
                                }
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
                            HorizontalDivider(Modifier.padding(start = labelW), color = lineColor)
                            Text(
                                fmtHourShort(h),
                                modifier = Modifier.padding(start = 4.dp, top = 1.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    BoxWithConstraints(Modifier.padding(start = labelW).fillMaxSize()) {
                        val colW = maxWidth / 7
                        val todayIndex = days.indexOf(today)
                        if (todayIndex >= 0) {
                            Box(
                                Modifier
                                    .offset(x = colW * todayIndex)
                                    .width(colW)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            )
                        }
                        days.forEachIndexed { i, _ ->
                            if (i > 0) {
                                Box(
                                    Modifier
                                        .offset(x = colW * i)
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(lineColor)
                                )
                            }
                            // tasks with the same title on one day are merged into a single block
                            val groups = groupSimilar(weekTasks[i])
                            val byId = groups.associateBy { it.merged.id }
                            layoutLanes(groups.map { it.merged }).forEach { p ->
                                val t = p.task
                                val g = byId[t.id] ?: return@forEach
                                val laneW = colW / p.lanes
                                val blockH = (hourH * (t.durationMin / 60f)).coerceAtLeast(18.dp)
                                TaskBlock(
                                    t,
                                    Modifier
                                        .offset(x = colW * i + laneW * p.lane, y = hourH * (t.startMin / 60f))
                                        .width(laneW - 1.dp)
                                        .height(blockH - 1.dp),
                                    compact = true,
                                    onClick = { if (g.tasks.size == 1) onTaskClick(g.tasks[0]) else onGroupClick(g.tasks) },
                                    onDoubleTap = {
                                        val allDone = g.tasks.all { it.done }
                                        g.tasks.forEach { if (it.done == allDone) state.toggleDone(it) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Month grid with a dot per task; tap a day to see its tasks underneath. */
@Composable
fun MonthView(
    state: PlannerState,
    onTaskClick: (Task) -> Unit,
    onAddOn: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedDate
    val first = selected.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1
    val gridStart = first.minusDays(offset.toLong())
    val rows = (offset + first.lengthOfMonth() + 6) / 7
    val byDay = state.tasksBetween(gridStart, gridStart.plusDays(rows * 7L - 1)).groupBy { it.date }
    val dayTasks = byDay[selected] ?: emptyList()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { n ->
                Text(
                    n,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val d = gridStart.plusDays((r * 7 + c).toLong())
                    DayCell(
                        date = d,
                        inMonth = d.month == first.month,
                        isSelected = d == selected,
                        tasks = byDay[d] ?: emptyList(),
                        onClick = { state.selectedDate = d },
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmtDay(selected) + " · " + fmtFullDate(selected),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { onAddOn(selected) }) { Text("Add task") }
            TextButton(onClick = { state.viewMode = 0 }) { Text("Open day") }
        }
        if (dayTasks.isEmpty()) {
            Text(
                "Nothing planned.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        dayTasks.forEach { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(t.id, t.done) {
                        detectTapGestures(
                            onDoubleTap = { state.toggleDone(t) },
                            onTap = { onTaskClick(t) },
                        )
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(TaskColors[t.colorIndex.coerceIn(0, TaskColors.lastIndex)])
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        t.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    )
                    Text(
                        "${fmtTime(t.startMin)} · ${fmtDuration(t.durationMin)}" + (if (t.seriesId >= 0) " · repeats" else "") +
                            (if (t.carry.isNotBlank()) " · take: ${cleanCarry(t.carry)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(88.dp)) // room for the add button
    }
}

@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    tasks: List<Task>,
    onClick: () -> Unit,
) {
    val isToday = date == LocalDate.now()
    Column(
        Modifier
            .weight(1f)
            .height(56.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            tasks.take(3).forEach { t ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TaskColors[t.colorIndex.coerceIn(0, TaskColors.lastIndex)])
                )
            }
            if (tasks.size > 3) {
                Text("+${tasks.size - 3}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
