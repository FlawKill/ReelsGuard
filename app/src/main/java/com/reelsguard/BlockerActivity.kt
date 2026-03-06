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
 * It counts down the mandatory break, then dismisses itself.
 */
class BlockerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BREAK_DURATION_MINUTES = "break_duration_minutes"
        const val EXTRA_LIMIT_MINUTES = "limit_minutes"
    }

    private lateinit var binding: ActivityBlockerBinding
    private var countdown: CountDownTimer? = null

    // Dismiss blocker if the user manually resets from MainActivity
    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TimerService.ACTION_RESET_TODAY) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show above lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityBlockerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val breakMinutes = intent.getIntExtra(EXTRA_BREAK_DURATION_MINUTES, 10)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 30)

        binding.tvTitle.text = "Take a Break 🧘"
        binding.tvMessage.text =
            "You've watched Reels for $limitMinutes minutes today.\n\nTime to rest your eyes."
        binding.tvBreakLabel.text = "Break ends in:"

        registerReceiver(resetReceiver, IntentFilter(TimerService.ACTION_RESET_TODAY),
            RECEIVER_NOT_EXPORTED)

        startCountdown(breakMinutes * 60 * 1000L)

        binding.btnGoHome.setOnClickListener {
            navigateHome()
        }
    }

    private fun startCountdown(durationMs: Long) {
        countdown = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val m = (millisUntilFinished / 1000) / 60
                val s = (millisUntilFinished / 1000) % 60
                binding.tvCountdown.text = String.format("%02d:%02d", m, s)
            }

            override fun onFinish() {
                binding.tvCountdown.text = "00:00"
                binding.tvBreakLabel.text = "Break complete! You can resume."
                binding.btnGoHome.text = "Back to Instagram"
                binding.btnGoHome.setOnClickListener {
                    finish()
                }
            }
        }.start()
    }

    private fun navigateHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }

    override fun onBackPressed() {
        // Block back-press — user must wait out the timer
        navigateHome()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
        unregisterReceiver(resetReceiver)
    }
}
