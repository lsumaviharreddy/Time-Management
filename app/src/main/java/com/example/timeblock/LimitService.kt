package com.example.timeblock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Watches which app is on screen. When a monitored app passes its daily timer it is covered with a
 * "time's up" screen; 5 minutes before that you get a heads-up notification.
 */
class LimitService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var executor: ScheduledExecutorService? = null
    private var overlay: View? = null
    private var overlayFor: String? = null
    private val lastLimitNotice = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Reminders.createChannels(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, Reminders.CH_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("App timers are on")
            .setContentText("TimeBlock is watching your app limits")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FG_ID, n)
        }

        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor().also {
                it.scheduleWithFixedDelay({ safeTick() }, 0, 3, TimeUnit.SECONDS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        executor?.shutdownNow()
        executor = null
        hideOverlay()
        super.onDestroy()
    }

    private fun safeTick() {
        try {
            tick()
        } catch (e: Exception) {
            // Usage access revoked or a transient failure; try again next round.
        }
    }

    private fun tick() {
        val store = Store(this)
        val limits = store.loadLimits()
        if (limits.isEmpty() || !Usage.hasAccess(this)) {
            main.post { hideOverlay() }
            return
        }
        if (!getSystemService(PowerManager::class.java).isInteractive) return

        val result = Usage.compute(this, 1)
        val app = Usage.appFor(result.foreground)
        val limitMin = if (app == null) null else limits[app.key]
        if (app == null || limitMin == null) {
            main.post { hideOverlay() }
            return
        }

        val today = LocalDate.now()
        val usedMs = result.appMs(app, today)
        val limitMs = limitMin * 60_000L
        if (usedMs >= limitMs) {
            if (Usage.canOverlay(this)) {
                main.post { showOverlay(app, usedMs, limitMin) }
            } else {
                notifyLimitReached(app, limitMin)
            }
        } else {
            main.post { hideOverlay() }
            val leftMs = limitMs - usedMs
            if (limitMin > Reminders.LEAD_MIN && leftMs <= Reminders.LEAD_MIN * 60_000L &&
                !store.isWarned(app.key, today)
            ) {
                store.setWarned(app.key, today)
                notifyHeadsUp(app, limitMin)
            }
        }
    }

    // ---- notifications ----
    private fun notifyHeadsUp(app: MonitoredApp, limitMin: Int) {
        val n = Notification.Builder(this, Reminders.CH_LIMITS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${app.name}: ${Reminders.LEAD_MIN} minutes left")
            .setContentText("Your daily timer is ${fmtDuration(limitMin)}.")
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(3000 + app.key.hashCode() % 1000, n)
    }

    private fun notifyLimitReached(app: MonitoredApp, limitMin: Int) {
        val now = System.currentTimeMillis()
        if (now - (lastLimitNotice[app.key] ?: 0L) < 60_000L) return
        lastLimitNotice[app.key] = now
        val n = Notification.Builder(this, Reminders.CH_LIMITS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${app.name}: time's up")
            .setContentText("You've hit your ${fmtDuration(limitMin)} daily timer. Allow \"display over other apps\" in TimeBlock to block it.")
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(4000 + app.key.hashCode() % 1000, n)
    }

    // ---- block screen ----
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showOverlay(app: MonitoredApp, usedMs: Long, limitMin: Int) {
        if (overlay != null && overlayFor == app.key) return
        hideOverlay()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF2101018.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true
        }
        root.addView(TextView(this).apply {
            text = "Time's up"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(
            TextView(this).apply {
                text = "You've spent ${fmtDuration((usedMs / 60_000L).toInt())} on ${app.name} today.\n" +
                    "Your daily timer was ${fmtDuration(limitMin)}."
                textSize = 17f
                setTextColor(0xFFCCCCCC.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) },
        )
        root.addView(
            Button(this).apply {
                text = "Go to home screen"
                setOnClickListener { goHome() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(28) },
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try {
            getSystemService(WindowManager::class.java).addView(root, lp)
            overlay = root
            overlayFor = app.key
        } catch (e: Exception) {
            overlay = null
            overlayFor = null
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        try {
            getSystemService(WindowManager::class.java).removeView(v)
        } catch (e: Exception) {
            // already gone
        }
        overlay = null
        overlayFor = null
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        hideOverlay()
    }

    companion object {
        private const val FG_ID = 2
    }
}
