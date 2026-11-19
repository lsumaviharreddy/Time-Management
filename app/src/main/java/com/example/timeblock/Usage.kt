package com.example.timeblock

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** An app we track. [packages] lists every known package id (stable, nightly, regional builds). */
data class MonitoredApp(val key: String, val name: String, val packages: List<String>)

val MonitoredApps = listOf(
    MonitoredApp("instagram", "Instagram", listOf("com.instagram.android", "com.instagram.lite")),
    MonitoredApp("youtube", "YouTube", listOf("com.google.android.youtube")),
    MonitoredApp(
        "kotatsu", "Kotatsu",
        listOf("org.koitharu.kotatsu", "org.koitharu.kotatsu.nightly", "org.koitharu.kotatsu.debug"),
    ),
    MonitoredApp("genshin", "Genshin Impact", listOf("com.miHoYo.GenshinImpact", "com.miHoYo.Yuanshen")),
    MonitoredApp("brawlstars", "Brawl Stars", listOf("com.supercell.brawlstars")),
)

class UsageResult(
    /** day -> package -> milliseconds in the foreground */
    private val perDay: Map<LocalDate, Map<String, Long>>,
    /** package currently on screen, if any */
    val foreground: String?,
) {
    fun appMs(app: MonitoredApp, day: LocalDate): Long {
        val m = perDay[day] ?: return 0L
        return app.packages.sumOf { m[it] ?: 0L }
    }
}

object Usage {
    // Event types: 1 = activity resumed, 2 = activity paused, 16 = screen turned off.
    private const val EV_RESUMED = 1
    private const val EV_PAUSED = 2
    private const val EV_SCREEN_OFF = 16

    @Suppress("DEPRECATION")
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    @Suppress("DEPRECATION")
    fun isInstalled(ctx: Context, app: MonitoredApp): Boolean =
        app.packages.any { pkg ->
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }

    fun appFor(pkg: String?): MonitoredApp? =
        if (pkg == null) null else MonitoredApps.firstOrNull { pkg in it.packages }

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Foreground time per monitored package for the last [days] days (today included). */
    fun compute(ctx: Context, days: Int): UsageResult {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val watched = MonitoredApps.flatMap { it.packages }.toSet()

        val totals = HashMap<LocalDate, HashMap<String, Long>>()
        val active = HashMap<String, MutableSet<String>>()
        val startAt = HashMap<String, Long>()
        val seen = HashSet<String>()
        var foreground: String? = null

        fun addInterval(pkg: String, s: Long, e: Long) {
            var cur = s
            while (cur < e) {
                val d = Instant.ofEpochMilli(cur).atZone(zone).toLocalDate()
                val dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val seg = minOf(e, dayEnd) - cur
                totals.getOrPut(d) { HashMap() }.merge(pkg, seg, Long::plus)
                cur += seg
            }
        }

        val events = usm.queryEvents(from, now)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val ts = ev.timeStamp
            when (ev.eventType) {
                EV_RESUMED -> {
                    val pkg = ev.packageName
                    foreground = pkg
                    if (pkg in watched) {
                        val set = active.getOrPut(pkg) { mutableSetOf() }
                        if (set.isEmpty()) startAt[pkg] = ts
                        set.add(ev.className ?: "")
                        seen.add(pkg)
                    }
                }
                EV_PAUSED -> {
                    val pkg = ev.packageName
                    if (foreground == pkg) foreground = null
                    if (pkg in watched) {
                        val set = active.getOrPut(pkg) { mutableSetOf() }
                        set.remove(ev.className ?: "")
                        if (set.isEmpty()) {
                            // App was already open when the window began: count from the window start.
                            val s = startAt.remove(pkg) ?: (if (pkg !in seen) from else null)
                            if (s != null) addInterval(pkg, s, ts)
                        }
                        seen.add(pkg)
                    }
                }
                EV_SCREEN_OFF -> {
                    foreground = null
                    for ((pkg, s) in startAt) addInterval(pkg, s, ts)
                    startAt.clear()
                    active.values.forEach { it.clear() }
                }
            }
        }
        // Still open right now
        for ((pkg, s) in startAt) {
            if (active[pkg]?.isNotEmpty() == true) addInterval(pkg, s, now)
        }
        return UsageResult(totals, foreground)
    }
}

object Monitor {
    /** Starts the limit-watching service if any timer is set and usage access is granted; otherwise stops it. */
    fun sync(ctx: Context) {
        val want = Store(ctx).loadLimits().isNotEmpty() && Usage.hasAccess(ctx)
        val intent = Intent(ctx, LimitService::class.java)
        try {
            if (want) ctx.startForegroundService(intent) else ctx.stopService(intent)
        } catch (e: Exception) {
            // Background-start restrictions: it will be retried next time the app opens.
        }
    }
}
