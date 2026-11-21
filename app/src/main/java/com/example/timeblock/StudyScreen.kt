@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Study stopwatch. While it runs, TimeBlock's own reminders are held back and (optionally) Do Not Disturb is on. */
@Composable
fun StudyScreen(state: PlannerState) {
    val running = state.studying
    val elapsedMs = if (running) (state.now - state.studyStart).coerceAtLeast(0L) else 0L
    val secs = elapsedMs / 1000
    val clock = "%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60)

    val today = LocalDate.now()
    val todaySessions = state.sessions.filter { it.date == today }
    val studiedMin = todaySessions.sumOf { it.minutes } + (elapsedMs / 60_000L).toInt()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (running) "Studying" else "Study stopwatch",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(clock, fontSize = 64.sp, fontWeight = FontWeight.Light)

        if (!running) {
            Button(
                onClick = { state.startStudy() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Start studying") }
        } else {
            OutlinedButton(
                onClick = { state.stopStudy() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Stop") }
            Text(
                "TimeBlock reminders are paused until you stop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Silence all notifications", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Turns on Do Not Disturb while the stopwatch runs (alarms still ring). It goes back to how it was when you stop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.studyDnd,
                        enabled = !running,
                        onCheckedChange = { state.changeStudyDnd(it) },
                    )
                }
                if (state.studyDnd && !state.hasDndAccess) {
                    Text(
                        "Android needs your permission before an app can change Do Not Disturb. Without it, only TimeBlock's own reminders are paused.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { state.onRequestDnd() }) { Text("Allow Do Not Disturb access") }
                }
            }
        }

        Text(
            if (studiedMin == 0 && todaySessions.isEmpty()) {
                "No study time yet today"
            } else {
                "Studied today: ${fmtDuration(studiedMin)}" +
                    if (todaySessions.isNotEmpty()) " in ${todaySessions.size} session" + (if (todaySessions.size == 1) "" else "s") else ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        todaySessions.asReversed().take(6).forEach { s ->
            Text(
                fmtDuration(s.minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
