package com.reelsguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that:
 * - Listens for Reels start/stop broadcasts from ReelsAccessibilityService
 * - Ticks a timer every second while Reels is active
 * - Persists today's total AND break period to SharedPreferences (survives restarts)
 * - Launches BlockerActivity when the user's limit is exceeded
 * - Resets the daily counter at midnight
 * - Restarts itself automatically if killed (START_STICKY)
 */
class TimerService : Service() {

    companion object {
        private const val TAG = "TimerService"
        private const val CHANNEL_ID = "reels_guard_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.reelsguard.START_SERVICE"
        const val ACTION_STOP = "com.reelsguard.STOP_SERVICE"
        const val ACTION_RESET_TODAY = "com.reelsguard.RESET_TODAY"
        const val ACTION_TICK = "com.reelsguard.TICK"

        const val EXTRA_SECONDS_TODAY = "seconds_today"
        const val EXTRA_IS_ON_REELS = "is_on_reels"
        const val EXTRA_IN_BREAK = "in_break"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingReels = false
    private var blockerShown = false

    // Always read break state from SharedPreferences — never from memory alone
    private val isInBreak: Boolean
        get() = System.currentTimeMillis() < AppPreferences.breakUntilMs

    private val reelsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ReelsAccessibilityService.ACTION_REELS_STARTED -> {
                    if (isInBreak) {
                        Log.d(TAG, "In break — blocking Reels access")
                        showBlocker()
                        return
                    }
                    Log.d(TAG, "Reels started — tracking")
                    isTrackingReels = true
                    blockerShown = false
                }
                ReelsAccessibilityService.ACTION_REELS_STOPPED -> {
                    Log.d(TAG, "Reels stopped")
                    isTrackingReels = false
                    updateNotification(AppPreferences.reelsSecondsToday, AppPreferences.timeLimitSeconds())
                }
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isTrackingReels && !blockerShown) {
                checkDailyReset()

                // Guard: re-check break on every tick (handles restarts mid-break)
                if (isInBreak) {
                    isTrackingReels = false
                    showBlocker()
                    handler.postDelayed(this, 1000)
                    return
                }

                AppPreferences.reelsSecondsToday += 1
                val elapsed = AppPreferences.reelsSecondsToday
                val limit = AppPreferences.timeLimitSeconds()

                updateNotification(elapsed, limit)
                broadcastTick(elapsed, false)

                if (elapsed >= limit) {
                    triggerBlocker()
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ReelsAccessibilityService.ACTION_REELS_STARTED)
            addAction(ReelsAccessibilityService.ACTION_REELS_STOPPED)
        }
        registerReceiver(reelsReceiver, filter, RECEIVER_NOT_EXPORTED)

        // KEY FIX: Restore break state on startup
        // If the service was killed while a break was active, resume enforcing it
        if (isInBreak) {
            Log.d(TAG, "Service restarted mid-break — enforcing break until ${AppPreferences.breakUntilMs}")
            blockerShown = true
            isTrackingReels = false
        }

        TimerServiceState.isRunning = true
        handler.post(tickRunnable)
        Log.d(TAG, "TimerService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESET_TODAY -> {
                AppPreferences.reelsSecondsToday = 0
                AppPreferences.breakUntilMs = 0L  // clear persisted break
                blockerShown = false
                isTrackingReels = false
                broadcastTick(0, false)
                updateNotification(0, AppPreferences.timeLimitSeconds())
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(AppPreferences.reelsSecondsToday, AppPreferences.timeLimitSeconds())
        )

        // START_STICKY: Android auto-restarts this service if the system kills it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        TimerServiceState.isRunning = false
        handler.removeCallbacks(tickRunnable)
        unregisterReceiver(reelsReceiver)
        Log.d(TAG, "TimerService destroyed — system will restart it")
    }

    // ── Block logic ───────────────────────────────────────────────────────────

    private fun triggerBlocker() {
        Log.d(TAG, "Limit reached — starting mandatory break")
        val breakMs = AppPreferences.breakDurationMinutes * 60_000L
        // PERSISTED to SharedPreferences — survives service restarts
        AppPreferences.breakUntilMs = System.currentTimeMillis() + breakMs
        blockerShown = true
        isTrackingReels = false
        showBlocker()
    }

    private fun showBlocker() {
        val intent = Intent(this, BlockerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockerActivity.EXTRA_BREAK_UNTIL_MS, AppPreferences.breakUntilMs)
            putExtra(BlockerActivity.EXTRA_LIMIT_MINUTES, AppPreferences.timeLimitMinutes)
        }
        startActivity(intent)

        // Push Instagram to background
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun checkDailyReset() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (AppPreferences.lastResetDate != today) {
            AppPreferences.reelsSecondsToday = 0
            AppPreferences.breakUntilMs = 0L
            AppPreferences.lastResetDate = today
            blockerShown = false
            Log.d(TAG, "Daily counter reset for $today")
        }
    }

    private fun broadcastTick(seconds: Long, inBreak: Boolean) {
        sendBroadcast(Intent(ACTION_TICK).apply {
            setPackage(packageName)
            putExtra(EXTRA_SECONDS_TODAY, seconds)
            putExtra(EXTRA_IS_ON_REELS, isTrackingReels)
            putExtra(EXTRA_IN_BREAK, inBreak)
        })
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "ReelsGuard", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks your Reels watch time"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSeconds: Long, limitSeconds: Long): Notification {
        val statusText = when {
            isInBreak -> {
                val remainingBreakSec = (AppPreferences.breakUntilMs - System.currentTimeMillis()) / 1000
                "🛑 Break: ${formatTime(remainingBreakSec)} remaining"
            }
            isTrackingReels -> "🔴 Reels: ${formatTime(elapsedSeconds)} / ${formatTime(limitSeconds)}"
            else -> "⚪ Today: ${formatTime(elapsedSeconds)} / ${formatTime(limitSeconds)}"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("ReelsGuard Active")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Long, limitSeconds: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(elapsedSeconds, limitSeconds))
    }

    private fun formatTime(seconds: Long): String {
        val s = maxOf(0L, seconds)
        return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
    }
}