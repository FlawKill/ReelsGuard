package com.reelsguard

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.reelsguard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val seconds = intent.getLongExtra(TimerService.EXTRA_SECONDS_TODAY, 0)
            val onReels = intent.getBooleanExtra(TimerService.EXTRA_IS_ON_REELS, false)
            updateUsageDisplay(seconds, onReels)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        restoreSettings()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(tickReceiver, IntentFilter(TimerService.ACTION_TICK),
            RECEIVER_NOT_EXPORTED)
        refreshPermissionStatus()
        updateUsageDisplay(AppPreferences.reelsSecondsToday, false)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(tickReceiver)
    }

    private fun setupUI() {
        // Time limit slider (5–120 min)
        binding.sliderTimeLimit.apply {
            valueFrom = 5f
            valueTo = 120f
            stepSize = 5f
            value = AppPreferences.timeLimitMinutes.toFloat()
            addOnChangeListener { _, value, _ ->
                AppPreferences.timeLimitMinutes = value.toInt()
                binding.tvTimeLimitValue.text = "${value.toInt()} min"
            }
        }
        binding.tvTimeLimitValue.text = "${AppPreferences.timeLimitMinutes} min"

        // Break duration slider (5–60 min)
        binding.sliderBreakDuration.apply {
            valueFrom = 5f
            valueTo = 60f
            stepSize = 5f
            value = AppPreferences.breakDurationMinutes.toFloat()
            addOnChangeListener { _, value, _ ->
                AppPreferences.breakDurationMinutes = value.toInt()
                binding.tvBreakValue.text = "${value.toInt()} min"
            }
        }
        binding.tvBreakValue.text = "${AppPreferences.breakDurationMinutes} min"

        // Master toggle
        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasAllPermissions()) {
                    binding.switchMonitoring.isChecked = false
                    Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                AppPreferences.monitoringEnabled = true
                startTimerService()
            } else {
                AppPreferences.monitoringEnabled = false
                stopTimerService()
            }
        }

        // Permission buttons
        binding.btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnGrantOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            )
        }

        // Reset today's counter
        binding.btnResetToday.setOnClickListener {
            AppPreferences.reelsSecondsToday = 0
            sendBroadcast(Intent(TimerService.ACTION_RESET_TODAY).setPackage(packageName))
            updateUsageDisplay(0, false)
            Toast.makeText(this, "Today's Reels time reset!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreSettings() {
        binding.switchMonitoring.isChecked = AppPreferences.monitoringEnabled
    }

    private fun startTimerService() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopTimerService() {
        startService(Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        })
    }

    private fun refreshPermissionStatus() {
        val hasUsage = hasUsageStatsPermission()
        val hasAccessibility = isAccessibilityEnabled()
        val hasOverlay = Settings.canDrawOverlays(this)

        binding.tvUsageStatus.text = if (hasUsage) "✅ Granted" else "❌ Required"
        binding.tvAccessibilityStatus.text = if (hasAccessibility) "✅ Granted" else "❌ Required"
        binding.tvOverlayStatus.text = if (hasOverlay) "✅ Granted" else "❌ Required"

        binding.btnGrantUsage.visibility = if (hasUsage) View.GONE else View.VISIBLE
        binding.btnGrantAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
        binding.btnGrantOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        val allGranted = hasUsage && hasAccessibility && hasOverlay
        binding.switchMonitoring.isEnabled = allGranted
        binding.tvPermissionsNote.visibility = if (allGranted) View.GONE else View.VISIBLE
    }

    private fun updateUsageDisplay(seconds: Long, onReels: Boolean) {
        val m = seconds / 60
        val s = seconds % 60
        val limitSec = AppPreferences.timeLimitSeconds()
        val remaining = maxOf(0L, limitSec - seconds)
        val rm = remaining / 60
        val rs = remaining % 60

        binding.tvReelsToday.text = String.format("%02d:%02d", m, s)
        binding.tvRemaining.text = "Remaining: %02d:%02d".format(rm, rs)
        binding.tvReelsStatus.text = if (onReels) "🔴 Reels Active" else "⚪ Not on Reels"

        val pct = if (limitSec > 0) (seconds * 100 / limitSec).toInt() else 0
        binding.progressReels.progress = pct.coerceIn(0, 100)
    }

    private fun hasAllPermissions() =
        hasUsageStatsPermission() && isAccessibilityEnabled() && Settings.canDrawOverlays(this)

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        if (accessibilityEnabled == 0) return false

        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return services.contains("$packageName/${ReelsAccessibilityService::class.java.name}")
    }
}
