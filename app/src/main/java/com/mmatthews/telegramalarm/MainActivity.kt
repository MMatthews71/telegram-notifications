package com.mmatthews.telegramalarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mmatthews.telegramalarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 13+ needs permission for US to post the alarm notification.
        requestPostNotificationsIfNeeded()

        binding.enabledSwitch.isChecked = prefs.enabled
        binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.enabled = isChecked
            if (isChecked) WatcherService.start(this) else WatcherService.stop(this)
        }

        binding.grantAccessButton.setOnClickListener { openNotificationAccessSettings() }
        binding.batteryButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.fullScreenButton.setOnClickListener { openFullScreenIntentSettings() }
        binding.testButton.setOnClickListener { Alarm.start(this) }
        binding.stopButton.setOnClickListener { Alarm.stop(this) }

        buildPackageCheckboxes()

        // Arm the sticky watcher as soon as the app is opened (if enabled).
        if (prefs.enabled) WatcherService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val hasAccess = hasNotificationAccess()
        binding.statusText.text = if (hasAccess) {
            getString(R.string.status_on)
        } else {
            getString(R.string.status_off)
        }
        binding.grantAccessButton.text = if (hasAccess) {
            getString(R.string.open_access_settings)
        } else {
            getString(R.string.grant_access)
        }

        binding.batteryButton.text = if (isIgnoringBatteryOptimizations()) {
            getString(R.string.battery_ok)
        } else {
            getString(R.string.battery_request)
        }

        binding.fullScreenButton.text = if (canUseFullScreenIntent()) {
            getString(R.string.fullscreen_ok)
        } else {
            getString(R.string.fullscreen_request)
        }
    }

    /** True once the user has ticked this app under Settings > Notification access. */
    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    @Suppress("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (isIgnoringBatteryOptimizations()) {
            // Already exempt — just show the battery settings so they can confirm.
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = getSystemService(NotificationManager::class.java)
        return nm.canUseFullScreenIntent()
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
                return
            } catch (_: Exception) {
            }
        }
        // Older Android grants this automatically; just open app settings.
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    /** One checkbox per Telegram client, so you can tailor exactly what triggers the alarm. */
    private fun buildPackageCheckboxes() {
        binding.packagesContainer.removeAllViews()
        Prefs.DEFAULT_TELEGRAM_PACKAGES.sorted().forEach { pkg ->
            val cb = CheckBox(this).apply {
                text = pkg
                isChecked = prefs.isWatched(pkg)
                setOnCheckedChangeListener { _, checked -> prefs.setWatched(pkg, checked) }
            }
            binding.packagesContainer.addView(cb)
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}
