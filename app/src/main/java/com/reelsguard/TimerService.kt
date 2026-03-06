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
 * - Persists today's total to SharedPreferences
 * - Launches BlockerActivity when the user's limit is exceeded
 * - Resets the daily counter at midnight
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

        // Broadcast for UI updates
        const val EXTRA_SECONDS_TODAY = "seconds_today"
        const val EXTRA_IS_ON_REELS = "is_on_reels"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingReels = false
    private var blockerShown = false
    private var breakUntilMs = 0L   // grace period after being blocked

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isTrackingReels && !blockerShown) {
                checkDailyReset()
                AppPreferences.reelsSecondsToday += 1
                val elapsed = AppPreferences.reelsSecondsToday
                val limit = AppPreferences.timeLimitSeconds()

                Log.d(TAG, "Reels time today: ${elapsed}s / limit: ${limit}s")

                updateNotification(elapsed, limit)
                broadcastTick(elapsed)

                if (elapsed >= limit) {
                    triggerBlocker()
                    return // stop ticking until reset
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    // Receives signals from the Accessibility Service
    private val reelsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ReelsAccessibilityService.ACTION_REELS_STARTED -> {
                    if (System.currentTimeMillis() < breakUntilMs) {
                        Log.d(TAG, "In break period, ignoring Reels start")
                        return
                    }
                    Log.d(TAG, "Reels started")
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

    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ReelsAccessibilityService.ACTION_REELS_STARTED)
            addAction(ReelsAccessibilityService.ACTION_REELS_STOPPED)
        }
        registerReceiver(reelsReceiver, filter, RECEIVER_NOT_EXPORTED)
        handler.post(tickRunnable)
        Log.d(TAG, "TimerService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESET_TODAY -> {
                AppPreferences.reelsSecondsToday = 0
                blockerShown = false
                breakUntilMs = 0L
                broadcastTick(0)
                updateNotification(0, AppPreferences.timeLimitSeconds())
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification(
            AppPreferences.reelsSecondsToday,
            AppPreferences.timeLimitSeconds()
        ))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        unregisterReceiver(reelsReceiver)
        Log.d(TAG, "TimerService destroyed")
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun triggerBlocker() {
        blockerShown = true
        isTrackingReels = false
        val breakMs = AppPreferences.breakDurationMinutes * 60_000L
        breakUntilMs = System.currentTimeMillis() + breakMs

        val intent = Intent(this, BlockerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockerActivity.EXTRA_BREAK_DURATION_MINUTES, AppPreferences.breakDurationMinutes)
            putExtra(BlockerActivity.EXTRA_LIMIT_MINUTES, AppPreferences.timeLimitMinutes)
        }
        startActivity(intent)

        // Also navigate to home so Instagram goes to background
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)

        // Resume ticking so we can pick up next session after break
        handler.postDelayed(tickRunnable, 1000)
    }

    private fun checkDailyReset() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (AppPreferences.lastResetDate != today) {
            AppPreferences.reelsSecondsToday = 0
            AppPreferences.lastResetDate = today
            blockerShown = false
            breakUntilMs = 0L
            Log.d(TAG, "Daily counter reset for $today")
        }
    }

    private fun broadcastTick(seconds: Long) {
        sendBroadcast(Intent(ACTION_TICK).apply {
            setPackage(packageName)
            putExtra(EXTRA_SECONDS_TODAY, seconds)
            putExtra(EXTRA_IS_ON_REELS, isTrackingReels)
        })
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ReelsGuard",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks your Reels watch time"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSeconds: Long, limitSeconds: Long): Notification {
        val elapsed = formatTime(elapsedSeconds)
        val limit = formatTime(limitSeconds)
        val remaining = formatTime(maxOf(0, limitSeconds - elapsedSeconds))

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("ReelsGuard Active")
            .setContentText("Reels today: $elapsed / $limit  •  $remaining left")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Long, limitSeconds: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(elapsedSeconds, limitSeconds))
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
