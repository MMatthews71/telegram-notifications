package com.mmatthews.telegramalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * The thing that actually makes the noise.
 *
 * This is a plain singleton instead of a Service on purpose: the notification
 * listener runs in the background, and Android 12+ forbids *starting* a
 * foreground service from the background. But it is perfectly allowed to play
 * alarm audio from the already-running listener process and to post a
 * full-screen notification. So that's what we do:
 *   1. Set the ALARM stream to max and play the alarm tone on a loop.
 *   2. Vibrate on a repeating pattern.
 *   3. Hold a partial wake lock so the CPU keeps the sound going.
 *   4. Post a full-screen-intent notification that pops [AlarmActivity] over
 *      the lock screen (like an incoming call) and carries a STOP action.
 *
 * Everything is kept in static fields so both the listener (which starts it)
 * and the alarm screen / STOP button (which stop it) can reach the same state.
 */
object Alarm {

    @Volatile
    var isRunning: Boolean = false
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private const val CHANNEL_ID = "telegram_alarm_channel"
    private const val NOTIFICATION_ID = 1001

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        val first = !isRunning
        isRunning = true

        // The notification + vibration only need to be set up once.
        if (first) {
            postAlarmNotification(app)
            startVibration(app)
        }

        // The wake lock and sound are (re)ensured on every call. This makes the
        // alarm self-healing: when the phone is asleep, the full-screen AlarmActivity
        // wakes the screen and calls start() again from the foreground — a context
        // Doze can't throttle — so even if the background attempt was frozen, the
        // sound reliably plays.
        acquireWakeLock(app)
        startSound(app)
    }

    @Synchronized
    fun stop(context: Context) {
        val app = context.applicationContext

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null

        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        isRunning = false
    }

    private fun postAlarmNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            setSound(null, null) // we play the sound ourselves; keep the channel silent
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = PendingIntent.getBroadcast(
            context, 1, StopAlarmReceiver.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.alarm_notif_title))
            .setContentText(context.getString(R.string.alarm_notif_text))
            .setSmallIcon(R.drawable.ic_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .addAction(R.drawable.ic_alarm, context.getString(R.string.stop), stopPending)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun startSound(context: Context) {
        // If a player is already going (e.g. the background attempt succeeded),
        // leave it running rather than restarting the loop.
        val alreadyPlaying = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
        if (alreadyPlaying) return

        // Drop any half-set-up player from a throttled earlier attempt.
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

        val uri: Uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                // Synchronous prepare is more reliable than prepareAsync when the
                // device is idle/dozing — no deferred callback to wait on.
                prepare()
                start()
            }
        } catch (_: Exception) {
            mediaPlayer = null
        }
    }

    private fun startVibration(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 1s on, 0.5s off, repeating forever (repeat index 0).
        val timings = longArrayOf(0, 1000, 500)
        val amplitudes = intArrayOf(0, 255, 0)
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
    }

    private fun acquireWakeLock(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TelegramAlarm::WakeLock").apply {
                setReferenceCounted(false)
            }
        }
        // (Re)acquire with a fresh 10-minute safety timeout.
        wakeLock?.acquire(10 * 60 * 1000L)
    }
}
