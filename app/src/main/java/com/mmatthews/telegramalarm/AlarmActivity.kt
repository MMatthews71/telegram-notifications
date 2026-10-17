package com.mmatthews.telegramalarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.mmatthews.telegramalarm.databinding.ActivityAlarmBinding

/**
 * The big red "TELEGRAM!" screen with a STOP button. It is launched by the
 * full-screen intent posted in [Alarm], and it forces itself on top of the lock
 * screen and turns the display on, just like an alarm clock or incoming call.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If we somehow got here without the sound running (e.g. launched from a
        // stale notification), make sure it is going so there is something to stop.
        if (!Alarm.isRunning) Alarm.start(this)

        binding.stopButton.setOnClickListener {
            Alarm.stop(this)
            finish()
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        // If this screen goes away for any reason, make sure the noise stops too.
        if (Alarm.isRunning) {
            Alarm.stop(this)
        }
    }
}
