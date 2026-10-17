package com.mmatthews.telegramalarm

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
    }

    companion object {
        private const val TAG = "TelegramAlarm"
    }
}
