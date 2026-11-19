@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun FocusScreen(state: PlannerState) {
    val running = state.running
    val idleMs = (if (state.isBreak) state.breakMin else state.workMin) * 60_000L
    val remainingMs = if (running) (state.focusEnd - state.now).coerceAtLeast(0L) else idleMs
    val totalMs = if (running) state.focusTotalMin * 60_000L else idleMs
    val progress = if (totalMs > 0) 1f - remainingMs.toFloat() / totalMs else 0f
    val secs = (remainingMs + 999) / 1000
    val clock = "%02d:%02d".format(secs / 60, secs % 60)

    val today = LocalDate.now()
    val todaySessions = state.sessions.filter { it.date == today }
    val focusedToday = todaySessions.sumOf { it.minutes }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.isBreak,
                    onClick = { state.isBreak = false },
                    label = { Text("Focus") },
                )
                FilterChip(
                    selected = state.isBreak,
                    onClick = { state.isBreak = true },
                    label = { Text("Break") },
                )
            }
        } else {
            Text(
                if (state.isBreak) "Break time" else "Focus time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(250.dp),
                strokeWidth = 12.dp,
                color = if (state.isBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(clock, fontSize = 56.sp, fontWeight = FontWeight.Light)
        }

        if (!running) {
            val options = if (state.isBreak) listOf(5, 10, 15) else listOf(15, 25, 50, 90)
            val selected = if (state.isBreak) state.breakMin else state.workMin
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { m ->
                    FilterChip(
                        selected = selected == m,
                        onClick = { if (state.isBreak) state.breakMin = m else state.workMin = m },
                        label = { Text("$m min") },
                    )
                }
            }

            if (!state.isBreak) TaskPicker(state)

            Button(
                onClick = { state.startFocus() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isBreak) "Start break" else "Start focus")
            }
        } else {
            val taskTitle = state.titleOf(state.focusTaskId)
            if (!state.isBreak && taskTitle != null) {
                Text(
                    "Working on: $taskTitle",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = { state.stopFocus() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Stop") }
            Text(
                "You'll get a notification when the timer ends, even if the app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            if (todaySessions.isEmpty()) {
                "No focus sessions yet today"
            } else {
                "Focused today: ${fmtDuration(focusedToday)} in ${todaySessions.size} session" +
                    if (todaySessions.size == 1) "" else "s"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskPicker(state: PlannerState) {
    var open by remember { mutableStateOf(false) }
    val todays = state.tasksOn(LocalDate.now()).filter { !it.done }
    val label = state.titleOf(state.focusTaskId) ?: "No task selected"

    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("No task") },
                onClick = {
                    state.focusTaskId = -1
                    open = false
                },
            )
            todays.forEach { t ->
                DropdownMenuItem(
                    text = { Text("${t.title} · ${fmtTime(t.startMin)}") },
                    onClick = {
                        state.focusTaskId = t.id
                        open = false
                    },
                )
            }
        }
    }
}
