# THRYNX — Professional Video Player for Android

<p align="center">
  <img src="docs/screenshots/thrynx_hero_banner.svg" alt="THRYNX Hero Banner" width="100%">
</p>

<p align="center">
  <a href="https://github.com/sobuj-here/thrynx/releases/latest">
    <img src="https://img.shields.io/badge/Release-v1.0.0-2979FF?style=for-the-badge&logo=github&logoColor=white" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Engine-Media3%20ExoPlayer-00E5FF?style=for-the-badge" alt="Engine" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline--First-00E676?style=for-the-badge" alt="Privacy" />
  <img src="https://img.shields.io/badge/Languages-English%20%7C%20%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE-orange?style=for-the-badge" alt="Languages" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License" />
</p>

---

## 📥 Download & Installation

Pre-compiled production binaries for Android 8.0+ (API 26–34) are available on [GitHub Releases](https://github.com/sobuj-here/thrynx/releases/latest).

| Package | Target Architecture | File Size | Direct Download |
| :--- | :--- | :--- | :--- |
| **THRYNX v1.0.0 APK** | Universal (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | ~22.1 MB | [**Download APK (`app-debug.apk`)**](https://github.com/sobuj-here/thrynx/releases/download/v1.0.0/app-debug.apk) |

### Quick Installation Steps
1. Download `app-debug.apk` directly on your Android device or transfer from PC.
2. Open the downloaded APK and confirm installation (allow "Install Unknown Apps" in system settings if prompted).
3. Launch **THRYNX** (`PROFESSIONAL VIDEO PLAYER`).

---

## 🌟 Executive Overview
**THRYNX** is a dedicated, premium native Android **Video Player** inspired by the feature set, gesture responsiveness, and usability of **MX Player**. 

Built with modern **Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3 ExoPlayer**, THRYNX is engineered with a strict single-focus mission: delivering high-performance, rock-solid video playback and video library management without cloud bloat, intrusive telemetry, or unnecessary editing baggage.

### Core Architecture Pillars:
- **MX Player-Style Gestures**: Intuitive vertical swipes for brightness (left) and volume (right), horizontal scrubbing for timeline seek, double-tap seek, and long-press 2x speed.
- **Advanced Media3 Playback Engine**: Zero-lag hardware-accelerated playback with extension renderer support for 4K UHD, 1080p FHD, H.264/AVC, H.265/HEVC, VP9, and AV1.
- **Real Multi-Audio Track Switching**: Detects embedded multi-language audio streams (English, বাংলা, Hindi, Spanish, etc.) and switches audio tracks seamlessly without re-buffering or losing playback position.
- **Subtitle System & Synchronization**: Embedded track discovery, external `.srt` / `.vtt` / `.ass` subtitle loading, real-time sync delay offset (+/- 100ms, 500ms), and visual styling (font size, color, background opacity).
- **Private Vault & Hidden Folders**: Secure PIN-protected vault allowing users to hide sensitive videos from the standard library with local Room database persistence.
- **4 Clean Navigation Tabs**: Home, Videos, Folders, and Settings.
- **100% Offline-First Privacy**: Operates fully on-device. No analytics, no ads, no trackers, and zero external cloud dependencies.

---

## 🚀 Key Capabilities & Feature Matrix

### 🎬 1. Advanced Video Playback Engine
- **Media3 ExoPlayer Pipeline**: Low-latency rendering, adaptive buffering (15s minimum buffer, 60s maximum buffer, 1.5s fast playback start), and automatic recovery on network/playback interruptions.
- **Multiple Embedded Audio Tracks**: Automatically discovers real audio tracks within video containers and allows instant switching with language labels, channel count, and sample rate.
- **Aspect Ratio & Display Modes**:
  - `Fit to Screen` (Preserves video aspect ratio with letterboxing)
  - `Fill Screen / Crop` (Fills the entire display, eliminating black bars)
  - `Stretch to Window` (Stretches video frames to match display bounds)
  - `16:9 Widescreen` & `4:3 Standard` aspect frames
- **Playback Speed Controller**: 0.25x, 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x, 2.5x, and 3.0x.
- **Sleep Timer**: Configurable auto-pause timer (10m, 15m, 30m, 45m, 60m) with live remaining time display.
- **Screenshot Capture**: Lossless frame capture using hardware `PixelCopy`, saved directly into `Pictures/THRYNX_Screenshots`.
- **Picture-in-Picture (PiP)**: Smooth system PiP mode for seamless multitasking.
- **Orientation Control**: Instant portrait ⟷ landscape orientation toggle, plus auto-rotation sensor support.
- **Lock Controls**: One-tap control lock preventing accidental touch events during video playback.

---

### 👆 2. MX Player-Style Gesture Controls
| Gesture Action | Area of Screen | Result |
| :--- | :--- | :--- |
| **Vertical Swipe (Up/Down)** | Left Side | Smooth screen brightness adjustment (0% – 100%) with HUD badge |
| **Vertical Swipe (Up/Down)** | Right Side | Device media volume adjustment (0% – 100%) with HUD badge |
| **Horizontal Swipe (Left/Right)** | Anywhere | Precision timeline seek with time delta indicator (`+00:15 / -00:10`) |
| **Double Tap** | Left Side | Instant rewind (configurable: 5s, 10s, 15s, 30s) |
| **Double Tap** | Right Side | Instant forward seek (configurable: 5s, 10s, 15s, 30s) |
| **Double Tap** | Center | Play / Pause |
| **Long Press** | Anywhere | Temporary 2.0x fast-forward playback (returns to normal on release) |
| **Single Tap** | Anywhere | Toggle on-screen controls overlay (auto-hides after 4.5s of inactivity) |

---

### 💬 3. Subtitle System & Real-Time Synchronization
- **Embedded Subtitle Tracks**: Automatic detection of embedded subtitle streams with language tags.
- **External Subtitle File Loader**: File picker supporting external `.srt`, `.vtt`, and `.ass` subtitle files.
- **Real-Time Subtitle Delay Sync**: Adjust subtitle timing offset dynamically in real-time (`-500ms`, `-100ms`, `Reset`, `+100ms`, `+500ms`) to fix out-of-sync audio/subtitles.
- **Visual Appearance Customizer**:
  - Font Size: 12sp to 32sp slider
  - Text Color: White, Yellow, Cyan, Green
  - Background Opacity: Transparent to Solid Black

---

### 📂 4. Video Library & Organization
The application is organized into **4 dedicated navigation tabs**:

#### 1. Home (`Screen.Home`)
- **Continue Watching**: Quick-resume card displaying the last watched video with a progress bar and exact saved timestamp.
- **Recently Played**: Horizontal scroll carousel of recently watched videos.
- **Recently Added**: Carousel of newly discovered videos on device.
- **Favorite Videos**: Starred videos collection for instant access.
- **Video Folders**: Quick-access folder cards.
- **Open Network Stream**: Stream dialog supporting direct HLS (`.m3u8`), DASH (`.mpd`), and progressive MP4/MKV video links.

#### 2. Videos (`Screen.Videos`)
- Complete device video library with thumbnail caching.
- **Grid & List Views**: Instant toggle between single-column list and adaptive multi-column grid layouts.
- **Search & Duration Filter**: Real-time title search and duration chips (`All`, `< 2 mins`, `2–10 mins`, `> 10 mins`).
- **Sorting Options**: Date (Newest/Oldest), Name (A-Z/Z-A), Size (Largest/Smallest), and Duration (Longest/Shortest).
- **Video Action Menu**: Play, Play Next, Add to Favorites, Move to Private Vault, Video Details, Rename, Delete with confirmation, and Share via secure `FileProvider`.

#### 3. Folders (`Screen.Folders`)
- Organized directory view grouping videos by storage folder with video counts and latest thumbnail preview.
- **Folder Details View**: Drill down into any folder with dedicated search and video listing.
- **Add Custom Folder**: Add custom directories via Android Storage Access Framework (SAF).
- **Private Vault**: Secure hidden video storage protected by a 4-digit PIN. Hidden videos are removed from the public library and only accessible upon entering the correct PIN.

#### 4. Settings (`Screen.Settings`)
- **Player Controls**: Default playback speed, double-tap seek duration (5s–30s), resume playback toggle, auto-play next video toggle, background audio toggle.
- **Gestures & Display**: MX gesture toggle, default orientation, default aspect ratio, hardware acceleration mode (Auto, Hardware, Software).
- **Subtitles**: Appearance customizer (font size, text color, background opacity).
- **Security & Vault**: Setup or change 4-digit vault PIN.
- **Library & Storage**: Manual library rescan, clear application cache, clear playback history.
- **App & Language**: Color theme (Dark, Light, System), language toggle (English, বাংলা), and app info.

---

## 🔒 Security, Privacy & Android Compliance
- **100% Offline-First Architecture**: THRYNX requires zero cloud servers, zero analytics SDKs, and zero telemetry tracking.
- **Scoped Storage & FileProvider**: Media sharing is performed using `androidx.core.content.FileProvider` with temporary read permission grants (`FLAG_GRANT_READ_URI_PERMISSION`), preventing `FileUriExposedException`.
- **Backup Data Protection**: Configured with `data_extraction_rules.xml` and `backup_rules.xml` to prevent leaking private vault PINs or credentials during cloud device transfers.
- **Minimal Permissions**: Strictly requests `READ_MEDIA_VIDEO` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 8–12), with no microphone or extraneous permissions.

---

## 🌐 Dual-Language Parity (English & বাংলা)
THRYNX features 100% localization parity between **English** and **বাংলা (Bangla)**. All player HUD badges, settings descriptions, dialogs, folder names, and gesture hints adapt seamlessly according to the selected in-app language.

---

## 🛠️ Tech Stack & Dependencies

| Layer | Technologies |
| :--- | :--- |
| **Language & Concurrency** | Kotlin 1.9.22, Kotlin Coroutines, StateFlow |
| **UI Framework** | Jetpack Compose (BOM 2024.02.00), Material Design 3, Accompanist |
| **Media Playback** | AndroidX Media3 ExoPlayer 1.2.1, Media3 UI, Media3 Session, HLS, DASH |
| **Architecture** | MVVM / Clean Architecture, Single Activity Navigation |
| **Local Persistence** | Room SQLite Database v3 (`fallbackToDestructiveMigration`), Jetpack DataStore Preferences |
| **Image & Thumbnail Loading** | Coil Compose 2.5.0 |
| **Build System** | Android Gradle Plugin 8.2.2, Target SDK 34 (Android 14), Min SDK 26 (Android 8.0 Oreo) |

---

## 🧪 Testing & Verification

Unit tests cover the core player state machine, library sorting, and private vault security:
```bash
./gradlew testDebugUnitTest
```
- `VideoPlayerStateTest`: Validates audio/subtitle language resolution, queue navigation, repeat mode transitions, and resolution badge classification.
- `VideoLibraryRepositoryTest`: Validates sorting algorithms (Date, Name, Size, Duration), duration filtering, and folder grouping.
- `PrivateVaultSecurityTest`: Validates private video exclusion from public library queries and 4-digit PIN verification.

---

## 📄 License
```
Copyright 2026 THRYNX Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
