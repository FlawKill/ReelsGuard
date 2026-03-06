package com.reelsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service that watches Instagram and detects when the user
 * is on the Reels tab. Communicates with TimerService via broadcasts.
 *
 * Detection strategy:
 * 1. Check the foreground package is com.instagram.android
 * 2. Scan the view hierarchy for the Reels tab indicator:
 *    - Content description "Reels" on a selected tab
 *    - Or the Reels BottomBar item being selected
 *    - Or the window class contains "Reels" view IDs
 */
class ReelsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelsAccessibility"
        const val ACTION_REELS_STARTED = "com.reelsguard.REELS_STARTED"
        const val ACTION_REELS_STOPPED = "com.reelsguard.REELS_STOPPED"

        // Instagram's known view identifiers for Reels navigation tab
        private val REELS_CONTENT_DESCRIPTIONS = setOf(
            "Reels",
            "Reels tab",
            "Reels, tab",
            "Reel"
        )

        // Instagram's resource IDs for the Reels tab (may change across versions)
        private val REELS_VIEW_IDS = setOf(
            "com.instagram.android:id/clips_tab",
            "com.instagram.android:id/reels_tab",
            "com.instagram.android:id/feed_tab_icon_clips"
        )
    }

    private var isOnReels = false
    private var currentPackage = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // Only process Instagram events
        if (pkg != "com.instagram.android") {
            if (isOnReels) {
                isOnReels = false
                broadcastReelsStopped()
            }
            return
        }

        currentPackage = pkg

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                checkIfOnReels()
            }
        }
    }

    private fun checkIfOnReels() {
        val root = rootInActiveWindow ?: return
        val onReelsNow = isReelsTabActive(root)
        root.recycle()

        if (onReelsNow && !isOnReels) {
            isOnReels = true
            Log.d(TAG, "Reels tab DETECTED — starting timer")
            broadcastReelsStarted()
        } else if (!onReelsNow && isOnReels) {
            isOnReels = false
            Log.d(TAG, "Reels tab EXITED — pausing timer")
            broadcastReelsStopped()
        }
    }

    private fun isReelsTabActive(root: AccessibilityNodeInfo): Boolean {
        // Strategy 1: Find selected tab with "Reels" content description
        for (id in REELS_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val isSelected = nodes.any { it.isSelected || it.isChecked }
                nodes.forEach { it.recycle() }
                if (isSelected) return true
            }
        }

        // Strategy 2: Traverse for selected Reels tab by content description
        return findReelsTabByDescription(root)
    }

    private fun findReelsTabByDescription(node: AccessibilityNodeInfo): Boolean {
        val cd = node.contentDescription?.toString() ?: ""
        if (cd in REELS_CONTENT_DESCRIPTIONS && (node.isSelected || node.isChecked)) {
            return true
        }
        // Also check text
        val text = node.text?.toString() ?: ""
        if (text in REELS_CONTENT_DESCRIPTIONS && (node.isSelected || node.isChecked)) {
            return true
        }

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

    private fun broadcastReelsStarted() {
        sendBroadcast(Intent(ACTION_REELS_STARTED).setPackage(packageName))
    }

    private fun broadcastReelsStopped() {
        sendBroadcast(Intent(ACTION_REELS_STOPPED).setPackage(packageName))
    }

    override fun onInterrupt() {
        if (isOnReels) {
            isOnReels = false
            broadcastReelsStopped()
        }
        Log.d(TAG, "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }
}
