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
        val isFocus = kind == "focus"

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(
            context,
            if (isFocus) Reminders.CH_FOCUS else Reminders.CH_TASKS,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(if (isFocus) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        val id = if (isFocus) 1 else intent.getIntExtra("id", 0) + 10
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
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
