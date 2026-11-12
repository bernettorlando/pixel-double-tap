# Pixel Double Tap (for Google Pixel 11 Pro)

A lightweight, 100% offline, Material You Android utility built specifically for **Google Pixel 11 Pro** (Android 16 / Android 17) to add **Double-Tap to Turn Screen Off** on empty space of the Home Screen & Lock Screen — plus a 1-tap shortcut and live status detector for Pixel's hidden hardware **Tap-to-Wake** (*Tap to check phone*).

## Features

- **Double-Tap Empty Space to Lock (Home Screen & Lock Screen)**
  - Works seamlessly on stock **Pixel Launcher** (`com.google.android.apps.nexuslauncher`, including Quickstep gesture navigation) and the **Lock Screen**.
  - **Preserves Ultrasonic Fingerprint & Face Unlock** using Android's native `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN` (never triggers "Phone locked by admin" PIN prompts).
  - **Smart Empty-Space Filtering**: Automatically ignores app icons, folders, widgets, page swipes, notification shade, keyboard (`IME`), and the Lock Screen PIN/Pattern/Password bouncer.
  - **Zero Overlay Inside Apps**: The `1x1` outside-touch listener detaches completely whenever you open any app or keyboard.
- **Zero-Battery Hardware Screen ON Integration**
  - Detects whether Pixel 11 Pro's low-power display-controller **Tap to check phone** (`doze_tap_gesture`) is enabled and jumps directly to `Settings → Display & touch → Lock screen` in one tap.
- **Bonus Included Tools**
  - Optional **Status Bar Double-Tap** toggle (lock from inside any app while keeping single swipe-down notifications & Quick Settings working).
  - **Quick Settings "Lock Screen" Tile** & resizable **Invisible Double-Tap Zone Widget**.
  - **Interactive Double-Tap Test Pad**, speed slider (`220ms–500ms`), and crisp Pixel `EFFECT_TICK` haptic feedback.
- **Privacy by Architecture**
  - **Zero `INTERNET` permission** in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — mathematically offline.
  - Never subscribes to text input events (`TYPE_VIEW_TEXT_CHANGED`) and never stores or transmits any data.

---

## Install on Pixel 11 Pro (20-Second Setup)

**[⬇️ Download `PixelDoubleTap-v1.0.0.apk` (1.7 MB, Signed Release)](https://github.com/bernettorlando/pixel-double-tap/raw/main/releases/PixelDoubleTap-v1.0.0.apk)** *(or from [GitHub Releases](https://github.com/bernettorlando/pixel-double-tap/releases/tag/v1.0.0))*

1. Open the link above on your Pixel 11 Pro and tap to install **`PixelDoubleTap-v1.0.0.apk`**.
2. Open **Pixel Double Tap** and follow the built-in 2-step card (required once by Android 13–17 for sideloaded APKs):
   - **Step 1 — Allow Restricted Settings**: Tap **Open App Info** → tap the **⋮** menu in the top-right corner → tap **Allow restricted settings** and verify with your fingerprint.
   - **Step 2 — Enable Accessibility**: Tap **Open Accessibility** → tap **Pixel Double Tap** → switch **Use Pixel Double Tap** ON.
3. *(For turning screen ON)*: If **Tap to check phone** shows **Off**, tap **Enable "Tap to check phone" in Settings** and toggle **Tap to check phone** ON.

---

## Build from Source

Requires JDK 17 and Android SDK 35:

```bash
./gradlew test assembleRelease
```

The signed APK is output to `app/build/outputs/apk/release/app-release.apk`.
