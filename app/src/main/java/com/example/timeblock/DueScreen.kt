@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Submission dates: everything that is due, soonest first. */
@Composable
fun DueScreen(state: PlannerState) {
    var editing by remember { mutableStateOf<Submission?>(null) }
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val open = state.submissions.filter { !it.done }.sortedWith(compareBy<Submission>({ it.date }, { if (it.timeMin < 0) 1440 else it.timeMin }))
    val finished = state.submissions.filter { it.done }.sortedByDescending { it.date }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Submission dates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "You get a reminder the day before and on the day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.submissions.isEmpty()) {
                Text(
                    "Nothing due. Tap + to save a deadline.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            open.forEach { s -> SubmissionRow(state, s, today) { editing = s } }
            if (finished.isNotEmpty()) {
                Text(
                    "Done",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                finished.forEach { s -> SubmissionRow(state, s, today) { editing = s } }
            }
            Spacer(Modifier.height(88.dp))
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Add submission") }
    }

    if (creating) SubmissionDialog(state, null) { creating = false }
    editing?.let { s -> SubmissionDialog(state, s) { editing = null } }
}

@Composable
private fun SubmissionRow(state: PlannerState, s: Submission, today: LocalDate, onClick: () -> Unit) {
    val overdue = !s.done && s.date < today
    val soon = !s.done && !overdue && s.date <= today.plusDays(1)
    val container = when {
        s.done -> MaterialTheme.colorScheme.surfaceVariant
        overdue -> MaterialTheme.colorScheme.errorContainer
        soon -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.done, onCheckedChange = { state.toggleSubmission(s) })
            Column(Modifier.weight(1f)) {
                Text(
                    s.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (s.done) TextDecoration.LineThrough else null,
                )
                Text(
                    fmtDay(s.date) + " · " + fmtFullDate(s.date) + if (s.timeMin >= 0) " · ${fmtTime(s.timeMin)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.note.isNotBlank()) {
                    Text(
                        s.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!s.done) {
                Text(
                    daysLeftText(s.date, today),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
fun SubmissionDialog(state: PlannerState, initial: Submission?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(initial?.timeMin ?: -1) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New submission" else "Edit submission") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What is due?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            android.app.DatePickerDialog(
                                ctx,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                date.year, date.monthValue - 1, date.dayOfMonth,
                            ).show()
                        },
                    ) { Text(fmtDay(date) + " · " + date.dayOfMonth + "/" + date.monthValue, maxLines = 1) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val base = if (time >= 0) time else 17 * 60
                            android.app.TimePickerDialog(ctx, { _, h, m -> time = h * 60 + m }, base / 60, base % 60, false).show()
                        },
                    ) { Text(if (time >= 0) fmtTime(time) else "Add time", maxLines = 1) }
                }
                if (time >= 0) TextButton(onClick = { time = -1 }) { Text("Remove time") }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    state.upsertSubmission(
                        Submission(
                            id = initial?.id ?: state.newId(),
                            title = title.trim(),
                            date = date,
                            timeMin = time,
                            note = note.trim(),
                            done = initial?.done ?: false,
                            eventId = initial?.eventId ?: -1L,
                        )
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = {
                        state.deleteSubmission(initial.id)
                        onDismiss()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
