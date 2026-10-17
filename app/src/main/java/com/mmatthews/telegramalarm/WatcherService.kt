package com.mmatthews.telegramalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * A permanently-running foreground service whose only job is to keep this app's
 * process alive and high priority, so Android is much less likely to kill the
 * notification listener while you're not looking.
 *
 * It shows a quiet, ongoing "armed" notification — which also doubles as an
 * at-a-glance confirmation that the alarm is still watching.
 *
 * It is started from the UI (when you arm the alarm) and re-started on boot,
 * so it never has to start from the background (which Android 12+ forbids).
 */
class WatcherService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Prefs(this).enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        // Nudge the system to (re)connect our notification listener in case it
        // was unbound while we weren't running.
        TelegramNotificationListener.requestRebind(this)

        // START_STICKY: if the OS kills us for memory, restart us when it can.
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildArmedNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildArmedNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watcher_channel_name),
                NotificationManager.IMPORTANCE_LOW // quiet, no sound/peek
            ).apply {
                description = getString(R.string.watcher_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watcher_notif_title))
            .setContentText(getString(R.string.watcher_notif_text))
            .setSmallIcon(R.drawable.ic_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val ACTION_STOP = "com.mmatthews.telegramalarm.STOP_WATCHER"
        private const val CHANNEL_ID = "telegram_alarm_watcher"
        private const val NOTIFICATION_ID = 2001

        /** Start (or keep) the armed watcher. Call this from a foreground context. */
        fun start(context: Context) {
            if (!Prefs(context).enabled) return
            val intent = Intent(context, WatcherService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
                // Some OEMs block background FGS starts; the listener still works.
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WatcherService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
