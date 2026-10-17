package com.mmatthews.telegramalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the STOP action from the alarm notification and silences the alarm.
 */
class StopAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Alarm.stop(context)
    }

    companion object {
        private const val ACTION_STOP = "com.mmatthews.telegramalarm.STOP"

        fun intent(context: Context): Intent =
            Intent(context, StopAlarmReceiver::class.java).apply { action = ACTION_STOP }
    }
}
