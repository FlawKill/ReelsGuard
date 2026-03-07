# 📵 ReelsGuard

> **Take back control of your time.** ReelsGuard monitors your Instagram Reels usage and enforces mandatory breaks when you exceed your daily limit — automatically, even in the background.

---

## 🧠 How It Works

ReelsGuard runs silently in the background using Android's **Accessibility Service API** to detect when you navigate to the Reels tab inside Instagram. A persistent **Foreground Service** tracks your total watch time for the day. The moment you hit your self-set daily limit, a full-screen blocker appears and Instagram is pushed to the background. You **cannot** skip the mandatory break.

All data is stored locally on your device. No accounts, no cloud, no tracking.

---

## ✨ Features

- 🔍 **Reels-only detection** — only tracks time spent on the Reels tab, not regular Instagram browsing
- ⏱️ **Configurable daily limit** — set anywhere from 5 to 120 minutes
- 🛑 **Mandatory break screen** — full-screen blocker with a live countdown timer
- 💾 **Persistent state** — break and usage data survive app restarts, service kills, and phone reboots
- 🔄 **Auto-recovery** — monitoring restarts automatically if the system kills the service; no manual re-toggling needed
- 🌙 **Daily reset** — usage counter resets automatically at midnight
- 🔔 **Live notification** — always shows current usage and remaining time in the status bar
- 🚀 **Boot-aware** — monitoring resumes automatically after a phone reboot

---

## 📸 Screens

| Main Screen | Blocker Screen |
|---|---|
| Permission setup, live usage tracker, limit sliders | Full-screen dark overlay with countdown and break tips |

---

## 🏗️ Architecture

```
ReelsGuard/
├── ReelsAccessibilityService.kt   # Watches Instagram view hierarchy for Reels tab
├── TimerService.kt                # Foreground service — ticks every second, persists state
├── BlockerActivity.kt             # Full-screen break enforcer with countdown
├── MainActivity.kt                # Setup UI — permissions, sliders, live usage display
├── AppPreferences.kt              # SharedPreferences wrapper — all persistent state
├── TimerServiceState.kt           # In-process singleton — tracks if service is alive
└── BootReceiver.kt                # Restarts TimerService after phone reboot
```

**Tech stack:** Kotlin · Android Accessibility Service · Foreground Service · SharedPreferences · BroadcastReceiver · ViewBinding

---

## 🔐 Permissions Required

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE` | Keeps the timer running in the background |
| `BIND_ACCESSIBILITY_SERVICE` | Reads Instagram's UI to detect the Reels tab |
| `SYSTEM_ALERT_WINDOW` | Displays the blocker screen over other apps |
| `PACKAGE_USAGE_STATS` | Verifies Instagram is in the foreground |
| `RECEIVE_BOOT_COMPLETED` | Restarts monitoring after a reboot |

> All permissions are used solely on-device. No data ever leaves your phone.

---

## 🚀 Getting Started

### Option A — Download APK (GitHub Actions)

1. Go to the **Actions** tab in this repo
2. Click the latest successful **Build Debug APK** run
3. Scroll to **Artifacts** → download **ReelsGuard-debug**
4. Transfer the `.apk` to your Android phone and install it

### Option B — Build locally

**Requirements:** Android Studio Hedgehog+, Android SDK, JDK 17, device running Android 8.0+ (API 26+)

```bash
git clone https://github.com/YOUR_USERNAME/ReelsGuard.git
cd ReelsGuard
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ First-Time Setup

After installing, open ReelsGuard and grant the three permissions **in order**:

1. **Usage Access** → tap Grant → find ReelsGuard → toggle ON
2. **Accessibility Service** → tap Grant → find ReelsGuard → toggle ON
3. **Display Over Other Apps** → tap Grant → toggle ON

Then:
- Set your **Daily Reels Limit** (default: 30 min)
- Set your **Mandatory Break** duration (default: 10 min)
- Flip **Enable Monitoring** → ON

Open Instagram and tap the Reels tab — you'll see the timer tick in the notification bar immediately.

---

## 🐛 Known Behaviour

- **Instagram updates** may change internal view IDs. If Reels detection stops working after an Instagram update, check `REELS_VIEW_IDS` in `ReelsAccessibilityService.kt` and update the IDs.
- On some Android skins (MIUI, OneUI), aggressive battery optimisation may delay service restart. Add ReelsGuard to your battery whitelist for best results.
- The blocker screen shows over the lock screen — if you lock your phone during a break, the break screen will be visible when you unlock.

---

## 🛠️ Built With

- [Kotlin](https://kotlinlang.org/)
- [Android Jetpack](https://developer.android.com/jetpack) — AppCompat, ConstraintLayout, Lifecycle
- [Material Components for Android](https://github.com/material-components/material-components-android)
- [GitHub Actions](https://github.com/features/actions) — automated APK builds

---

## 📄 License

MIT License — feel free to fork, modify, and use for personal projects.

---

## 🙋 Contributing

Pull requests are welcome. For major changes, open an issue first to discuss what you'd like to change.

If Instagram releases an update that breaks Reels detection, please open an issue with your Instagram version number and the new view IDs (found via Android Studio's Layout Inspector).

---

<p align="center">Built to fix a personal problem — spending too long on Reels 😅</p>
