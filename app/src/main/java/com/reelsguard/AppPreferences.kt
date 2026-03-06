package com.reelsguard

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "reels_guard_prefs"

    private const val KEY_TIME_LIMIT_MINUTES = "time_limit_minutes"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_REELS_SECONDS_TODAY = "reels_seconds_today"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val KEY_BREAK_DURATION_MINUTES = "break_duration_minutes"
    private const val KEY_BREAK_UNTIL_MS = "break_until_ms"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Time limit in minutes before blocking (default: 30)
    var timeLimitMinutes: Int
        get() = _prefs?.getInt(KEY_TIME_LIMIT_MINUTES, 30) ?: 30
        set(value) { _prefs?.edit()?.putInt(KEY_TIME_LIMIT_MINUTES, value)?.apply() }

    // Whether monitoring is active
    var monitoringEnabled: Boolean
        get() = _prefs?.getBoolean(KEY_MONITORING_ENABLED, false) ?: false
        set(value) { _prefs?.edit()?.putBoolean(KEY_MONITORING_ENABLED, value)?.apply() }

    // Accumulated Reels seconds today
    var reelsSecondsToday: Long
        get() = _prefs?.getLong(KEY_REELS_SECONDS_TODAY, 0L) ?: 0L
        set(value) { _prefs?.edit()?.putLong(KEY_REELS_SECONDS_TODAY, value)?.apply() }

    // Date string of last reset (yyyy-MM-dd)
    var lastResetDate: String
        get() = _prefs?.getString(KEY_LAST_RESET_DATE, "") ?: ""
        set(value) { _prefs?.edit()?.putString(KEY_LAST_RESET_DATE, value)?.apply() }

    // Break cooldown duration in minutes (default: 10)
    var breakDurationMinutes: Int
        get() = _prefs?.getInt(KEY_BREAK_DURATION_MINUTES, 10) ?: 10
        set(value) { _prefs?.edit()?.putInt(KEY_BREAK_DURATION_MINUTES, value)?.apply() }

    // Timestamp (epoch ms) until which break is active — persisted across restarts
    var breakUntilMs: Long
        get() = _prefs?.getLong(KEY_BREAK_UNTIL_MS, 0L) ?: 0L
        set(value) { _prefs?.edit()?.putLong(KEY_BREAK_UNTIL_MS, value)?.apply() }

    private var _prefs: SharedPreferences? = null

    fun init(context: Context) {
        _prefs = prefs(context)
    }

    fun timeLimitSeconds(): Long = timeLimitMinutes * 60L
}
