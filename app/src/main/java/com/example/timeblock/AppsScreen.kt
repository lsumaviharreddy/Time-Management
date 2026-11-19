@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

private fun msToMin(ms: Long): Int = (ms / 60_000L).toInt()

@Composable
fun AppsScreen(state: PlannerState) {
    val ctx = LocalContext.current.applicationContext
    var editing by remember { mutableStateOf<MonitoredApp?>(null) }
    val installed = remember { MonitoredApps.associate { it.key to Usage.isInstalled(ctx, it) } }

    LaunchedEffect(state.hasUsageAccess) {
        while (true) {
            if (state.hasUsageAccess) {
                val r = withContext(Dispatchers.Default) {
                    try {
                        Usage.compute(ctx, 7)
                    } catch (e: Exception) {
                        null
                    }
                }
                state.usage = r
            }
            delay(5_000)
        }
    }

    val usage = state.usage
    val today = LocalDate.now()
    val totalToday = if (usage == null) 0L else MonitoredApps.sumOf { usage.appMs(it, today) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Screen time", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (!state.hasUsageAccess) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Allow usage access", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android only shows per-app screen time to apps you grant \"Usage access\". " +
                            "Find TimeBlock in the list and switch it on.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { state.onRequestUsage() }) { Text("Open usage access settings") }
                }
            }
        } else {
            Text(
                "Total across these apps today: ${fmtDuration(msToMin(totalToday))}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.limits.isNotEmpty() && !state.canOverlay) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Let timers block apps", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow \"Display over other apps\" so TimeBlock can cover an app when its timer runs out. " +
                                "Without it you only get a notification.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { state.onRequestOverlay() }) { Text("Allow display over apps") }
                    }
                }
            }
        }

        MonitoredApps.forEachIndexed { index, app ->
            AppCard(
                app = app,
                color = TaskColors[index % TaskColors.size],
                installed = installed[app.key] == true,
                usage = usage,
                limitMin = state.limits[app.key],
                onEdit = { editing = app },
            )
        }

        Text(
            "Timers reset at midnight. 5 minutes before a timer ends you'll get a heads-up, and when it ends " +
                "the app is covered until you go home. Times come from Android's usage stats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editing?.let { app ->
        TimerDialog(
            app = app,
            current = state.limits[app.key],
            onSave = { state.setLimit(app.key, it); editing = null },
            onRemove = { state.setLimit(app.key, 0); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun AppCard(
    app: MonitoredApp,
    color: Color,
    installed: Boolean,
    usage: UsageResult?,
    limitMin: Int?,
    onEdit: () -> Unit,
) {
    val today = LocalDate.now()
    val week = (6 downTo 0).map { usage?.appMs(app, today.minusDays(it.toLong())) ?: 0L }
    val todayMs = week.last()
    val usedMin = msToMin(todayMs)
    val yesterdayMin = msToMin(week[5])
    val avgMin = msToMin(week.sum() / 7)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(app.name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!installed) {
                        Text(
                            "Not installed on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    if (usage == null) "–" else fmtDuration(usedMin),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (limitMin != null) {
                val limitMs = limitMin * 60_000L
                val over = todayMs >= limitMs
                LinearProgressIndicator(
                    progress = { (todayMs.toFloat() / limitMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (over) {
                        "Timer of ${fmtDuration(limitMin)} reached"
                    } else {
                        "Timer ${fmtDuration(limitMin)} · ${fmtDuration(msToMin(limitMs - todayMs) + 1)} left"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // last 7 days, today on the right
            val maxMs = maxOf(week.max(), 60_000L).toFloat()
            val bar = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            val barToday = MaterialTheme.colorScheme.primary
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val slot = size.width / 7f
                week.forEachIndexed { i, ms ->
                    val h = (size.height * ms / maxMs).coerceAtLeast(3f)
                    drawRoundRect(
                        color = if (i == 6) barToday else bar,
                        topLeft = Offset(i * slot + slot * 0.2f, size.height - h),
                        size = Size(slot * 0.6f, h),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                }
            }
            Text(
                "Yesterday ${fmtDuration(yesterdayMin)} · 7-day average ${fmtDuration(avgMin)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = onEdit) {
                Text(if (limitMin == null) "Set timer" else "Edit timer")
            }
        }
    }
}

@Composable
private fun TimerDialog(
    app: MonitoredApp,
    current: Int?,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(current ?: 60) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${app.name} daily timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit per day", Modifier.weight(1f))
                    TextButton(onClick = { minutes = (minutes - 15).coerceAtLeast(15) }) { Text("−15") }
                    Text(fmtDuration(minutes), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { minutes = (minutes + 15).coerceAtMost(12 * 60) }) { Text("+15") }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(15, 30, 45, 60, 90, 120, 180).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text(fmtDuration(m)) },
                        )
                    }
                }
                Text(
                    "You'll get a heads-up ${Reminders.LEAD_MIN} minutes before time runs out. " +
                        "After that the app is covered until tomorrow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
