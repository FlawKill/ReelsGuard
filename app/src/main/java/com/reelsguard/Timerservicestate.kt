package com.reelsguard

/**
 * Simple in-process singleton that tracks whether TimerService is currently running.
 * Used by ReelsAccessibilityService to decide if it needs to restart the service.
 *
 * Since both the service and the accessibility service run in the same process,
 * this flag is reliable within a single process lifetime.
 */
object TimerServiceState {
    var isRunning: Boolean = false
}