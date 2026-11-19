package com.example.timeblock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Tiny persistence layer: everything lives in SharedPreferences as JSON. */
class Store(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("timeblock", Context.MODE_PRIVATE)

    fun loadTasks(): List<Task> {
        val arr = JSONArray(prefs.getString("tasks", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Task(
                id = o.getInt("id"),
                title = o.getString("title"),
                date = LocalDate.parse(o.getString("date")),
                startMin = o.getInt("start"),
                durationMin = o.getInt("dur"),
                done = o.optBoolean("done", false),
                colorIndex = o.optInt("color", 0),
            )
        }
    }

    fun saveTasks(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("date", t.date.toString())
                    .put("start", t.startMin)
                    .put("dur", t.durationMin)
                    .put("done", t.done)
                    .put("color", t.colorIndex)
            )
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    fun loadSessions(): List<FocusSession> {
        val arr = JSONArray(prefs.getString("sessions", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FocusSession(LocalDate.parse(o.getString("date")), o.getInt("min"), o.optInt("task", -1))
        }
    }

    fun saveSessions(sessions: List<FocusSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(
                JSONObject()
                    .put("date", s.date.toString())
                    .put("min", s.minutes)
                    .put("task", s.taskId)
            )
        }
        prefs.edit().putString("sessions", arr.toString()).apply()
    }

    fun nextId(): Int {
        val n = prefs.getInt("nextId", 1)
        prefs.edit().putInt("nextId", n + 1).apply()
        return n
    }

    // App timers: app key -> daily limit in minutes
    fun loadLimits(): Map<String, Int> {
        val o = JSONObject(prefs.getString("limits", "{}"))
        return o.keys().asSequence().associateWith { o.getInt(it) }
    }

    fun saveLimits(limits: Map<String, Int>) {
        val o = JSONObject()
        limits.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("limits", o.toString()).apply()
    }

    // "2026-11-19|instagram,youtube": apps already given the 5-minute warning today
    fun isWarned(key: String, date: LocalDate): Boolean {
        val parts = (prefs.getString("warned", "") ?: "").split("|")
        return parts.size == 2 && parts[0] == date.toString() && key in parts[1].split(",")
    }

    fun setWarned(key: String, date: LocalDate) {
        val parts = (prefs.getString("warned", "") ?: "").split("|")
        val keys = if (parts.size == 2 && parts[0] == date.toString()) {
            parts[1].split(",").filter { it.isNotBlank() }.toMutableSet()
        } else {
            mutableSetOf()
        }
        keys.add(key)
        prefs.edit().putString("warned", "$date|${keys.joinToString(",")}").apply()
    }

    // Running focus/break timer
    var focusEnd: Long
        get() = prefs.getLong("focusEnd", 0L)
        set(v) { prefs.edit().putLong("focusEnd", v).apply() }

    var focusTotal: Int
        get() = prefs.getInt("focusTotal", 25)
        set(v) { prefs.edit().putInt("focusTotal", v).apply() }

    var focusBreak: Boolean
        get() = prefs.getBoolean("focusBreak", false)
        set(v) { prefs.edit().putBoolean("focusBreak", v).apply() }

    var focusTask: Int
        get() = prefs.getInt("focusTask", -1)
        set(v) { prefs.edit().putInt("focusTask", v).apply() }

    var notifAsked: Boolean
        get() = prefs.getBoolean("notifAsked", false)
        set(v) { prefs.edit().putBoolean("notifAsked", v).apply() }
}
