# Telegram Alarm

An Android app that sets off a **loud alarm on your phone whenever you get a Telegram notification**.

It uses Android's notification-listener feature to watch notifications from Telegram. The moment one arrives, it blasts the system alarm tone at full volume, vibrates, and throws up a full-screen **STOP** screen (over the lock screen, like an incoming call) so you can silence it.

---

## How it works (the short version)

1. `TelegramNotificationListener` — a `NotificationListenerService` that the system hands **every** notification on the phone. It ignores everything except Telegram.
2. `Alarm` — when a Telegram notification lands, this plays the alarm tone on a loop, vibrates, holds a wake lock, and posts a full-screen-intent notification.
3. `AlarmActivity` — the red full-screen **STOP** screen that pops over the lock screen.
4. `MainActivity` — where you grant permission, arm/disarm the alarm, choose which Telegram apps to watch, and fire a test alarm.

---

## Build it and get it on your phone

You need [Android Studio](https://developer.android.com/studio) installed.

### Option A — run straight to your phone (easiest)

1. **Open the project**: Android Studio → *Open* → pick this `telegram-notifications` folder. Let it finish "Gradle sync" (it downloads what it needs the first time).
2. **Turn on developer mode on your phone**: Settings → About phone → tap **Build number** 7 times. Then in Settings → System → Developer options, turn on **USB debugging**.
3. **Plug the phone into your computer** with a USB cable and tap **Allow** on the "Allow USB debugging?" prompt.
4. In Android Studio, your phone should appear in the device dropdown at the top. Pick it and press the green **Run ▶** button.
5. The app installs and launches on your phone.

### Option B — build an APK you can copy over

1. Android Studio → **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   (Or from a terminal in this folder: `./gradlew assembleDebug`.)
2. The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
3. Copy that file to your phone (email it to yourself, Google Drive, USB, etc.) and tap it to install. You'll have to allow "install from unknown sources" for whichever app you opened it with.

---

## First-time setup on the phone (important!)

The app can't work until you grant it notification access — Android guards this behind a settings screen on purpose.

1. Open the **Telegram Alarm** app.
2. Tap **Grant notification access**. This opens Android's *Notification access* list.
3. Find **Telegram Alarm**, turn it **on**, and confirm the warning dialog.
4. Back in the app, the status should now read **✅ Notification access granted**.
5. Under **Keep it alive**, tap both buttons and allow them:
   - **Stop battery optimisation** — so your phone doesn't kill the watcher after a while.
   - **Allow full-screen alarm** — so the alarm takes over the whole screen instead of a small banner.
   Both should then show a green ✅.
6. Make sure **Alarm armed** is switched on.
7. Tap **Test the alarm now** to hear it. Tap **STOP** (or the STOP button on the lock screen) to silence it.

That's it. Leave the app installed and armed, and any Telegram message will set it off.

## Staying alive in the background

To be as reliable as Android allows, the app:

- Runs a quiet, permanent **"Telegram Alarm is armed"** notification. That's a *foreground service* — it keeps the app's process alive and high priority so the system is far less likely to kill the notification watcher. (That notification is normal and expected; it's your proof the alarm is still watching.)
- **Re-arms itself automatically after a reboot** or an app update.
- **Asks the system to reconnect** the notification watcher if it's ever dropped.
- Lets you turn off **battery optimisation** and allow **full-screen alarms** right from the main screen.

### If it doesn't fire while the screen is off / locked

This is almost always your phone **freezing the app to save battery** once the
screen turns off (Android calls it *Doze*). Fix it:

1. In the app, tap **Stop battery optimisation** and allow it — it must show the
   green ✅.
2. Tap **Allow full-screen alarm** and allow it — green ✅. (This lets the alarm
   wake the screen instead of showing a silent banner.)
3. **Samsung phones** have an extra, separate setting that overrides the above:
   Settings → **Battery → Background usage limits** → make sure Telegram Alarm is
   **not** in *Sleeping apps* or *Deep sleeping apps* (remove it if it's there).
   Also Settings → Apps → Telegram Alarm → Battery → **Unrestricted**.
4. **Xiaomi / Oppo / Realme / Huawei** have similar "Autostart" and
   "battery saver" toggles — enable Autostart for Telegram Alarm and set battery
   to no restrictions.
5. Keep the **"Telegram Alarm is armed"** notification showing — don't swipe it
   away or disable it; it's what keeps the app alive.

After changing these, reboot once and send yourself a test Telegram message with
the screen off.

### What it still can't do

If the phone is **fully powered off or the battery is dead**, nothing can run — no app can sound an alarm on a device that isn't on. As long as the phone is on (even locked, asleep, silent, or in Do Not Disturb), the alarm fires.

### If it doesn't fire or the screen doesn't pop up

- **Notification access got revoked.** Phones sometimes turn it off after an update or if the app is "optimized". Re-check step 2–4 above.
- **Battery optimization.** Settings → Apps → Telegram Alarm → Battery → set to **Unrestricted**, so Android doesn't kill the listener.
- **Full-screen alarm shows as a banner instead of taking over the screen.** On Android 14+, go to Settings → Apps → Telegram Alarm → and allow **Full-screen notifications** / **Full screen intent**. Even without it, the sound still plays and the notification carries a STOP button.
- **Which Telegram?** The app watches several Telegram clients by default. In the app, make sure the one you actually use is ticked (the official app is `org.telegram.messenger`).

---

## A note on Telegram's own "connecting…" notification

Telegram keeps a permanent, silent background notification alive. The app deliberately ignores "ongoing" notifications like that one, so the alarm only fires on real messages — not constantly.
