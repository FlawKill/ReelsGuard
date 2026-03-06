package com.reelsguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts TimerService automatically when the phone boots up,
 * so the user doesn't have to reopen the app after a restart.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AppPreferences.init(context)
            if (AppPreferences.monitoringEnabled) {
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
