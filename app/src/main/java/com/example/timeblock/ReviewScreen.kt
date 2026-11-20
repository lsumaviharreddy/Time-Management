@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.timeblock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private data class DayStat(
    val date: LocalDate,
    val plannedMin: Int,
    val focusMin: Int,
    val tasks: Int,
    val doneTasks: Int,
)

@Composable
fun ReviewScreen(state: PlannerState) {
    var weekOffset by remember { mutableStateOf(0) }
    val monday = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks(weekOffset.toLong())
    val days = (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        val ts = state.tasksOn(d)
        DayStat(
            date = d,
            plannedMin = ts.sumOf { it.durationMin },
            focusMin = state.sessions.filter { it.date == d }.sumOf { it.minutes },
            tasks = ts.size,
            doneTasks = ts.count { it.done },
        )
    }

    val totalTasks = days.sumOf { it.tasks }
    val doneTasks = days.sumOf { it.doneTasks }
    val totalFocus = days.sumOf { it.focusMin }
    val totalPlanned = days.sumOf { it.plannedMin }
    val rate = if (totalTasks == 0) 0 else doneTasks * 100 / totalTasks
    val bestDay = days.maxByOrNull { it.focusMin }?.takeIf { it.focusMin > 0 }

    val planColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val focusColor = MaterialTheme.colorScheme.secondary
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weekOffset-- }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous week")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (weekOffset == 0) "This week" else if (weekOffset == -1) "Last week" else "Week of ${monday.format(fmt)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${monday.format(fmt)} – ${monday.plusDays(6).format(fmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { weekOffset++ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next week")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Tasks done", "$doneTasks / $totalTasks", "$rate% complete", Modifier.weight(1f))
            StatCard("Study time", fmtDuration(totalFocus), "of ${fmtDuration(totalPlanned)} planned", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Best study day",
                bestDay?.let { it.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } } ?: "—",
                bestDay?.let { fmtDuration(it.focusMin) } ?: "No sessions yet",
                Modifier.weight(1f),
            )
            StatCard(
                "Avg planned / day",
                fmtDuration(totalPlanned / 7),
                "across 7 days",
                Modifier.weight(1f),
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Planned vs studied", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(planColor, "Planned")
                    Legend(focusColor, "Studied")
                }

                val maxV = maxOf(60, days.maxOf { maxOf(it.plannedMin, it.focusMin) }).toFloat()
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val slot = size.width / days.size
                    val barW = slot * 0.28f
                    days.forEachIndexed { i, d ->
                        val center = i * slot + slot / 2f
                        val ph = size.height * (d.plannedMin / maxV)
                        val fh = size.height * (d.focusMin / maxV)
                        drawRoundRect(
                            color = planColor,
                            topLeft = Offset(center - barW - 2f, size.height - ph),
                            size = Size(barW, ph),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                        drawRoundRect(
                            color = focusColor,
                            topLeft = Offset(center + 2f, size.height - fh),
                            size = Size(barW, fh),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    days.forEach { d ->
                        Text(
                            d.date.dayOfWeek.name.first().toString(),
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (d.date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Box(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
