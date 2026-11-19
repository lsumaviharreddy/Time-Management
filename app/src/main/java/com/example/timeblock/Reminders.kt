package com.example.timeblock

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate

/**
 * Schedules a notification [LEAD_MIN] minutes before each task starts,
 * plus one alarm for the end of a focus/break session.
 */
object Reminders {
    const val LEAD_MIN = 5
    const val CH_TASKS = "task_reminders"
    const val CH_FOCUS = "focus_timer"
    const val CH_LIMITS = "app_limits"
    const val CH_MONITOR = "app_monitor"
    private const val FOCUS_REQ = 1_000_000_000

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TASKS, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Heads-up $LEAD_MIN minutes before a task starts"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a focus or break session ends"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LIMITS, "App timer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warnings when an app timer is about to run out or has run out"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "App timer monitor", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the app timers running in the background"
            }
        )
    }

    private fun pending(ctx: Context, req: Int, fill: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            req,
            Intent(ctx, ReminderReceiver::class.java).apply(fill),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(ctx: Context, atMillis: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun cancelRequest(ctx: Context, req: Int) {
        val existing = PendingIntent.getBroadcast(
            ctx,
            req,
            Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) {
            ctx.getSystemService(AlarmManager::class.java).cancel(existing)
            existing.cancel()
        }
    }

    fun schedule(ctx: Context, task: Task) {
        cancelRequest(ctx, task.id)
        if (task.done) return
        val at = task.startMillis() - LEAD_MIN * 60_000L
        if (at <= System.currentTimeMillis()) return
        val pi = pending(ctx, task.id) {
            putExtra("kind", "task")
            putExtra("id", task.id)
            putExtra("title", task.title)
            putExtra("text", "Starts in $LEAD_MIN min · ${fmtTime(task.startMin)}")
        }
        setAlarm(ctx, at, pi)
    }

    fun cancel(ctx: Context, taskId: Int) = cancelRequest(ctx, taskId)

    /** Reminders for repeating tasks are armed for the next [SERIES_DAYS] days and re-armed whenever one fires. */
    private const val SERIES_DAYS = 14

    fun scheduleSeries(ctx: Context, s: Series) {
        val today = LocalDate.now()
        for (i in 0..SERIES_DAYS) {
            val d = today.plusDays(i.toLong())
            if (s.occursOn(d)) schedule(ctx, s.occurrence(d)) else cancelRequest(ctx, occurrenceId(s.id, d))
        }
    }

    fun cancelSeries(ctx: Context, s: Series) {
        val today = LocalDate.now()
        for (i in 0..SERIES_DAYS) cancelRequest(ctx, occurrenceId(s.id, today.plusDays(i.toLong())))
    }

    fun scheduleFocusEnd(ctx: Context, endMillis: Long, isBreak: Boolean) {
        val pi = pending(ctx, FOCUS_REQ) {
            putExtra("kind", "focus")
            putExtra("title", if (isBreak) "Break is over" else "Focus session complete")
            putExtra("text", if (isBreak) "Ready for the next block?" else "Nice work. Time for a short break.")
        }
        setAlarm(ctx, endMillis, pi)
    }

    fun cancelFocusEnd(ctx: Context) = cancelRequest(ctx, FOCUS_REQ)

    fun rescheduleAll(ctx: Context) {
        val store = Store(ctx)
        store.loadTasks().forEach { schedule(ctx, it) }
        store.loadSeries().forEach { scheduleSeries(ctx, it) }
        if (store.focusEnd > System.currentTimeMillis()) {
            scheduleFocusEnd(ctx, store.focusEnd, store.focusBreak)
        }
    }
}
