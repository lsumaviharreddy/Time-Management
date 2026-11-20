package com.example.timeblock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannels(context)
        val kind = intent.getStringExtra("kind") ?: "task"
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: ""
        val carry = intent.getStringExtra("carry") ?: ""

        // The study stopwatch holds back all of TimeBlock's own reminders.
        if (!Store(context).studying) {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = Notification.Builder(context, Reminders.CH_TASKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(if (carry.isNotEmpty()) "$text · Take: $carry" else text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
            if (carry.isNotEmpty()) {
                builder.setStyle(Notification.BigTextStyle().bigText("$text\nTake with you: $carry"))
            }
            val id = if (kind == "submission") intent.getIntExtra("nid", 5_000_000) else intent.getIntExtra("id", 0) + 10
            context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
        }
        if (kind == "task") Reminders.rescheduleAll(context) // keeps repeating-task reminders armed
    }
}

/** Re-arms every reminder after a reboot, app update or clock/timezone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannels(context)
        Reminders.rescheduleAll(context)
        Monitor.sync(context)
        CalendarSync.armJob(context)
    }
}
