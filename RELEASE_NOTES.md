# 🚀 THRYNX v1.2.1 — Video Player Aspect Ratio & Orientation Engine Fix

Welcome to **THRYNX v1.2.1**! This update delivers a major fix and precision calibration for the Local Video Player's aspect ratio calculation, screen orientation transitions, and display scaling.

---

### ✨ What's New in v1.2.1

#### 🎬 Aspect Ratio & Fit-to-Screen Perfection
- **Zero Stretching in Portrait Mode:** 16:9, 4:3, and ultrawide videos now strictly maintain their native aspect ratio with clean top and bottom letterboxing—no more vertical stretching or squishing.
- **No Excessive Zoom or Cropping in Landscape Mode:** Prevented unwanted cropping and oversized zoom when rotating to landscape orientation. Landscape videos fit the screen edge-to-edge naturally.
- **Portrait & Reel/Shorts Video Support:** Vertical (9:16) videos now display centered with proper pillarboxes in landscape, and fit edge-to-edge in portrait without distortion.
- **Real-Time Video Stream Dimension Tracking:** Accurately listens to ExoPlayer's `onVideoSizeChanged` events, taking into account stream rotation metadata (`unappliedRotationDegrees`) and pixel aspect ratio.
- **Orientation Auto-Reset:** Zoom scale and pan offsets automatically reset upon orientation change, preventing videos from jumping off-screen or being stuck cropped.
- **Aspect Ratio HUD Indicator:** Tapping the aspect ratio button cycles between **Fit to screen**, **Fill screen**, and **16:9** with an instant animated on-screen HUD badge.
- **Dynamic Picture-in-Picture (PiP):** PiP mode now dynamically matches the exact aspect ratio of the active video (portrait or landscape).

---

### 📦 Which APK Should You Download?

| APK File | Best For | Description |
|---|---|---|
| **`app-prod-arm64-v8a-release.apk`** | 📱 **Modern Android Phones (Recommended)** | For 99% of Android smartphones (Samsung, Xiaomi, OnePlus, Vivo, Oppo, Realme, Pixel, etc.). Fast & optimized. |
| **`app-prod-x86_64-release.apk`** | 💻 **PC Emulators & ChromeOS** | For BlueStacks, LDPlayer, NoxPlayer, Windows Subsystem for Android (WSA), and x86_64 PCs. |
| **`app-prod-armeabi-v7a-release.apk`** | 📱 **Older 32-bit Android Devices** | For older budget or legacy smartphones. |
| **`app-prod-universal-release.apk`** | 🌐 **Universal (All Devices)** | Contains all CPU architectures. Guaranteed to install on any Android device, but has a larger file size. |

---

**Full Changelog**: https://github.com/sobuj-here/thrynx/compare/v1.2.0...v1.2.1
