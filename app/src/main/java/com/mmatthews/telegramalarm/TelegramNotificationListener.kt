package com.mmatthews.telegramalarm

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * The heart of the app. Once the user grants "Notification access" in Settings,
 * Android binds this service and calls [onNotificationPosted] for EVERY notification
 * that appears on the phone. We only react to the ones coming from Telegram.
 */
class TelegramNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (!prefs.enabled) return

        val pkg = sbn.packageName ?: return
        if (!prefs.isWatched(pkg)) return

        // Telegram keeps a permanent, silent "connecting / running" notification alive.
        // That one is "ongoing" and is NOT a new message, so skip it (otherwise the
        // alarm would fire constantly). Real message notifications are not ongoing.
        if (sbn.isOngoing) return

        Log.i(TAG, "Telegram notification from $pkg -> sounding the alarm")
        Alarm.start(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        // Make sure the sticky "armed" service is running too.
        WatcherService.start(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected — asking the system to rebind")
        // If the system unbinds us (low memory, etc.), ask it to reconnect.
        requestRebind(this)
    }

    companion object {
        private const val TAG = "TelegramAlarm"

        /** Ask the system to (re)connect this notification listener. */
        fun requestRebind(context: Context) {
            try {
                val component = ComponentName(context, TelegramNotificationListener::class.java)
                NotificationListenerService.requestRebind(component)
            } catch (_: Exception) {
            }
        }
    }
}
