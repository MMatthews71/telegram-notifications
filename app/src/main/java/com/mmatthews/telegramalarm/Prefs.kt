package com.mmatthews.telegramalarm

import android.content.Context

/**
 * Tiny wrapper around SharedPreferences for the handful of settings this app has.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Master on/off switch. When false we ignore every notification. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Package names we treat as "Telegram" and alarm on. */
    var watchedPackages: Set<String>
        get() = sp.getStringSet(KEY_PACKAGES, DEFAULT_TELEGRAM_PACKAGES) ?: DEFAULT_TELEGRAM_PACKAGES
        set(value) = sp.edit().putStringSet(KEY_PACKAGES, value).apply()

    fun isWatched(packageName: String): Boolean = watchedPackages.contains(packageName)

    fun setWatched(packageName: String, watched: Boolean) {
        val current = watchedPackages.toMutableSet()
        if (watched) current.add(packageName) else current.remove(packageName)
        watchedPackages = current
    }

    companion object {
        private const val NAME = "telegram_alarm_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PACKAGES = "packages"

        /** All the common Telegram clients, so it works no matter which one you use. */
        val DEFAULT_TELEGRAM_PACKAGES: Set<String> = setOf(
            "org.telegram.messenger",       // official Telegram (Play Store)
            "org.telegram.messenger.web",   // official Telegram (website APK)
            "org.telegram.messenger.beta",  // Telegram Beta
            "org.telegram.plus",            // Telegram Plus / Plus Messenger
            "org.thunderdog.challegram"     // Telegram X
        )
    }
}
