package com.mmatthews.telegramalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the armed watcher after the phone reboots (or the app is updated),
 * so you don't have to open the app again to re-arm it.
 *
 * The notification listener itself is automatically re-bound by the system after
 * boot, so message detection survives a reboot regardless; this just brings back
 * the persistent "armed" service too.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Prefs(context).enabled) {
                    WatcherService.start(context)
                }
            }
        }
    }
}
