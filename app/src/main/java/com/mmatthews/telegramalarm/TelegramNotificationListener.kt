package com.mmatthews.telegramalarm

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * The heart of the app. Once the user grants "Notification access" in Settings,
 * Android binds this service and calls [onNotificationPosted] for EVERY notification
 * that appears on the phone. We only react to genuinely new Telegram *messages*.
 *
 * The tricky part: [onNotificationPosted] does NOT only fire for new messages. It
 * also fires every time Telegram *updates* an existing notification — group
 * summaries, read-state changes, reactions, the background "connecting"
 * notification, and so on. If we alarmed on all of those, it would go off
 * seemingly at random. So we filter hard and de-duplicate.
 */
class TelegramNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (!prefs.enabled) return

        val pkg = sbn.packageName ?: return
        if (!prefs.isWatched(pkg)) return

        val n = sbn.notification ?: return

        // 1. Skip the permanent, silent "connecting / running" service notification.
        if (sbn.isOngoing) return
        if (!sbn.isClearable) return

        // 2. Skip the group "summary" notification — it duplicates the real ones and
        //    gets re-posted whenever any message in the group changes.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // 3. Only react to actual message notifications. Telegram tags real messages
        //    with CATEGORY_MESSAGE; its service / progress / call notifications use
        //    other categories. If a category is set and it isn't a message, skip.
        val category = n.category
        if (category != null && category != Notification.CATEGORY_MESSAGE) return

        // 4. Must actually have message content.
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank() && bigText.isBlank()) return

        // 5. De-duplicate: Telegram re-posts the same notification on all sorts of
        //    updates. If the content for this exact notification hasn't changed since
        //    we last saw it, it's an update, not a new message — ignore it.
        val content = "$title\u0001$text\u0001$bigText"
        synchronized(seenContent) {
            if (seenContent[sbn.key] == content) return
            seenContent[sbn.key] = content
            // Keep the map from growing forever.
            if (seenContent.size > MAX_TRACKED) {
                val oldest = seenContent.keys.firstOrNull()
                if (oldest != null) seenContent.remove(oldest)
            }
        }

        Log.i(TAG, "New Telegram message from $pkg -> sounding the alarm")
        Alarm.start(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Forget dismissed notifications so the same conversation can alarm again later.
        synchronized(seenContent) { seenContent.remove(sbn.key) }
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
        private const val MAX_TRACKED = 200

        /** notification key -> last content we alarmed on, to ignore re-posts. */
        private val seenContent = LinkedHashMap<String, String>()

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
