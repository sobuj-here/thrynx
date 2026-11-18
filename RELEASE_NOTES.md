# 🚀 THRYNX v1.2.0 — Local Video Player Overhaul & Media Management

Welcome to **THRYNX v1.2.0**! This release brings a massive overhaul to the Local Video and Music experience, packed with highly requested MX Player-style controls, seamless video resuming, on-screen battery and clock indicators, and in-app file deletion.

---

### ✨ What's New in v1.2.0

#### 🎬 Local Video Player Overhaul
- **Resume Playback from Where You Left Off:** The player now automatically remembers your exact watch progress. When reopening a video, it resumes smoothly with a quick on-screen position indicator.
- **Thumbnail Watch Progress Bar:** Video list thumbnails now feature a progress bar showing how much of each video you have already watched.
- **MX Player-Style Negative Remaining Time:** Tap on the timestamp in the player controls to switch between total duration (`01:23 / 04:50`) and remaining time (`01:23 / -03:27`).
- **Live Battery & Clock Display:** View your device's real-time battery percentage (with charging indicator) and current system clock directly in the top control bar without leaving full screen.
- **Portrait & Landscape Orientation Switcher:** Easily toggle between portrait and landscape modes with a single tap, featuring an MX Player-style animated icon.
- **Pinch-to-Zoom & Pan:** Fluid 1.0x to 4.5x pinch zooming and 2-finger panning across the video frame.

#### 🗑️ Media Management (In-App Deletion)
- **Local Videos Multi-Delete:** Long-press or use the select mode to choose multiple videos and delete them permanently from your storage directly from the app.
- **Local Music Deletion:** Select multiple offline or device songs and delete them directly from the song list or folder view.
- **Android 10 to Android 15+ Compatibility:** Fully compliant with Scoped Storage using `MediaStore.createDeleteRequest` for safe and native permission prompts.

#### 🎧 Listen Together Improvements
- Built-in connection to the default cloud sync server (`https://thrynx-listen-together.onrender.com`) for seamless real-time music sharing with friends.

---

### 📦 Which APK Should You Download?

| APK File | Best For | Description |
|---|---|---|
| **`app-prod-arm64-v8a-release.apk`** | 📱 **Modern Android Phones (Recommended)** | For 99% of Android smartphones (Samsung, Xiaomi, OnePlus, Vivo, Oppo, Realme, Pixel, etc.). Fast & optimized. |
| **`app-prod-x86_64-release.apk`** | 💻 **PC Emulators & ChromeOS** | For BlueStacks, LDPlayer, NoxPlayer, Windows Subsystem for Android (WSA), and x86_64 PCs. |
| **`app-prod-armeabi-v7a-release.apk`** | 📱 **Older 32-bit Android Devices** | For older budget or legacy smartphones. |
| **`app-prod-universal-release.apk`** | 🌐 **Universal (All Devices)** | Contains all CPU architectures. Guaranteed to install on any Android device, but has a larger file size (~191 MB). |

---

**Full Changelog**: https://github.com/sobuj-here/thrynx/compare/v1.1.0...v1.2.0
