package com.reelsguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.reelsguard.databinding.ActivityBlockerBinding

/**
 * Full-screen activity shown when the Reels time limit is exceeded.
 * Counts down the remaining break time using the persisted breakUntilMs timestamp,
 * so it is always accurate even if the service was restarted mid-break.
 */
class BlockerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BREAK_UNTIL_MS = "break_until_ms"
        const val EXTRA_LIMIT_MINUTES = "limit_minutes"
    }

    private lateinit var binding: ActivityBlockerBinding
    private var countdown: CountDownTimer? = null

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TimerService.ACTION_RESET_TODAY) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityBlockerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, AppPreferences.timeLimitMinutes)

        binding.tvTitle.text = "Take a Break 🧘"
        binding.tvMessage.text =
            "You've watched Reels for $limitMinutes minutes today.\n\nTime to rest your eyes."
        binding.tvBreakLabel.text = "Break ends in:"

        registerReceiver(
            resetReceiver,
            IntentFilter(TimerService.ACTION_RESET_TODAY),
            RECEIVER_NOT_EXPORTED
        )

        startCountdownFromPersistedTime()

        binding.btnGoHome.setOnClickListener { navigateHome() }
    }

    override fun onResume() {
        super.onResume()
        // If the activity is resumed (e.g. user tried to come back), restart countdown
        // accurately from the persisted break end time
        countdown?.cancel()
        startCountdownFromPersistedTime()
    }

    private fun startCountdownFromPersistedTime() {
        // Use the persisted timestamp so countdown is always accurate,
        // even if this Activity was recreated or the service restarted
        val breakUntilMs = AppPreferences.breakUntilMs
        val remainingMs = breakUntilMs - System.currentTimeMillis()

        if (remainingMs <= 0) {
            // Break already over
            onBreakComplete()
            return
        }

        countdown = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val m = (millisUntilFinished / 1000) / 60
                val s = (millisUntilFinished / 1000) % 60
                binding.tvCountdown.text = String.format("%02d:%02d", m, s)
            }

            override fun onFinish() {
                onBreakComplete()
            }
        }.start()
    }

    private fun onBreakComplete() {
        binding.tvCountdown.text = "00:00"
        binding.tvBreakLabel.text = "Break complete! You're good to go."
        binding.btnGoHome.text = "Close"
        binding.btnGoHome.setOnClickListener { finish() }
    }

    private fun navigateHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    @Deprecated("Overridden to block back navigation during break")
    override fun onBackPressed() {
        // Only allow back if break is over
        if (System.currentTimeMillis() >= AppPreferences.breakUntilMs) {
            super.onBackPressed()
        } else {
            navigateHome()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
        unregisterReceiver(resetReceiver)
    }
}