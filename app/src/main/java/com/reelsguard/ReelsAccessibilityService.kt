package com.reelsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service that watches Instagram and detects when the user
 * is on the Reels tab.
 *
 * KEY FIX: On every Instagram event, this service checks if TimerService
 * is running and restarts it if monitoring is enabled but the service was
 * killed by the system. This means the user NEVER has to manually re-toggle.
 */
class ReelsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelsAccessibility"
        const val ACTION_REELS_STARTED = "com.reelsguard.REELS_STARTED"
        const val ACTION_REELS_STOPPED = "com.reelsguard.REELS_STOPPED"

        private val REELS_CONTENT_DESCRIPTIONS = setOf(
            "Reels", "Reels tab", "Reels, tab", "Reel"
        )
        private val REELS_VIEW_IDS = setOf(
            "com.instagram.android:id/clips_tab",
            "com.instagram.android:id/reels_tab",
            "com.instagram.android:id/feed_tab_icon_clips"
        )
    }

    private var isOnReels = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppPreferences.init(this)
        Log.d(TAG, "Accessibility service connected")
        // Ensure TimerService is running when accessibility service connects
        ensureTimerServiceRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        if (pkg != "com.instagram.android") {
            if (isOnReels) {
                isOnReels = false
                broadcastReelsStopped()
            }
            return
        }

        // Every time we get an Instagram event, make sure TimerService is alive
        ensureTimerServiceRunning()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> checkIfOnReels()
        }
    }

    /**
     * Ensures TimerService is running if monitoring is enabled.
     * This is the fix for the "have to re-toggle" bug — the accessibility service
     * acts as a watchdog that revives the timer service whenever Instagram is used.
     */
    private fun ensureTimerServiceRunning() {
        if (AppPreferences.monitoringEnabled && !TimerServiceState.isRunning) {
            Log.d(TAG, "TimerService not running — restarting it")
            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
            }
            startForegroundService(intent)
        }
    }

    private fun checkIfOnReels() {
        val root = rootInActiveWindow ?: return
        val onReelsNow = isReelsTabActive(root)
        root.recycle()

        if (onReelsNow && !isOnReels) {
            isOnReels = true
            Log.d(TAG, "Reels tab DETECTED")
            broadcastReelsStarted()
        } else if (!onReelsNow && isOnReels) {
            isOnReels = false
            Log.d(TAG, "Reels tab EXITED")
            broadcastReelsStopped()
        }
    }

    private fun isReelsTabActive(root: AccessibilityNodeInfo): Boolean {
        for (id in REELS_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val isSelected = nodes.any { it.isSelected || it.isChecked }
                nodes.forEach { it.recycle() }
                if (isSelected) return true
            }
        }
        return findReelsTabByDescription(root)
    }

    private fun findReelsTabByDescription(node: AccessibilityNodeInfo): Boolean {
        val cd = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        if ((cd in REELS_CONTENT_DESCRIPTIONS || text in REELS_CONTENT_DESCRIPTIONS)
            && (node.isSelected || node.isChecked)
        ) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findReelsTabByDescription(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun broadcastReelsStarted() =
        sendBroadcast(Intent(ACTION_REELS_STARTED).setPackage(packageName))

    private fun broadcastReelsStopped() =
        sendBroadcast(Intent(ACTION_REELS_STOPPED).setPackage(packageName))

    override fun onInterrupt() {
        if (isOnReels) {
            isOnReels = false
            broadcastReelsStopped()
        }
    }
}