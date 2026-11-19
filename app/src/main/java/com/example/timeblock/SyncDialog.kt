@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncDialog(state: PlannerState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val calendars = remember(state.hasCalendarPermission) { CalendarSync.listCalendars(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Calendar sync") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.hasCalendarPermission) {
                    Text(
                        "Sync your tasks with Google Calendar so you can add and move them from your laptop too.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { state.onRequestCalendar() }) { Text("Allow calendar access") }
                } else {
                    Text(
                        "Pick the calendar to sync with. Tasks appear there as events, and events you add " +
                            "there appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (calendars.isEmpty()) {
                        Text(
                            "No writable calendars found. Add your Google account in phone Settings and " +
                                "turn on Calendar sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    calendars.forEach { cal ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.selectCalendar(cal.id)
                                    scope.launch { state.syncNow() }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.calendarId == cal.id, onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(cal.name, fontWeight = FontWeight.SemiBold)
                                if (cal.account.isNotBlank()) {
                                    Text(
                                        cal.account,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (state.syncOn) {
                        OutlinedButton(onClick = { scope.launch { state.syncNow() } }) { Text("Sync now") }
                        if (state.lastSync > 0L) {
                            Text(
                                "Last synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastSync)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "Tip: in Google Calendar on your laptop, create a calendar called TimeBlock " +
                            "(Other calendars > + > Create new calendar) and pick it here, so your other events " +
                            "don't mix in. Recurring and all-day events are skipped. A finished task shows a " +
                            "check mark in its event title.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (state.syncOn) {
                TextButton(onClick = { state.disableSync() }) {
                    Text("Turn off sync", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
