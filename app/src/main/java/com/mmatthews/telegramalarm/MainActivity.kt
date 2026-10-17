package com.mmatthews.telegramalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
        }

        binding.grantAccessButton.setOnClickListener {
            openNotificationAccessSettings()
        }

        binding.testButton.setOnClickListener {
            Alarm.start(this)
        }

        binding.stopButton.setOnClickListener {
            Alarm.stop(this)
        }

        buildPackageCheckboxes()
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
    }

    /** True once the user has ticked this app under Settings > Notification access. */
    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
