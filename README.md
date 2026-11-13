# THRYNX — High-Performance Native Video Player for Android

<p align="center">
  <img src="docs/screenshots/thrynx_hero_banner.svg" alt="THRYNX Hero Banner" width="100%">
</p>

<p align="center">
  <a href="https://github.com/sobuj-here/thrynx/releases/download/v1.0.0/app-debug.apk">
    <img src="https://img.shields.io/badge/⚡%20Download%20APK-v1.0.0%20(25.9%20MB)-00E5FF?style=for-the-badge&logo=android&logoColor=0B0E14" alt="Download APK" />
  </a>
  <a href="https://github.com/sobuj-here/thrynx/releases/tag/v1.0.0">
    <img src="https://img.shields.io/badge/📦%20Release%20Notes-v1.0.0-2979FF?style=for-the-badge&logo=github&logoColor=white" alt="Release Notes" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green?style=flat-square&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Engine-Media3%20ExoPlayer-00E5FF?style=flat-square" alt="Engine" />
  <img src="https://img.shields.io/badge/Architecture-Kotlin%20%7C%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Architecture" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline--First-00E676?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/Languages-English%20%7C%20%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE-orange?style=flat-square" alt="Languages" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square" alt="License" />
</p>

---

## 📥 Production Release & Installation

The official release binary for Android 8.0+ (API 26–34) is hosted on [GitHub Releases](https://github.com/sobuj-here/thrynx/releases/tag/v1.0.0).

| Package | Version | Binary Size | Target Architecture | Direct Download |
| :--- | :--- | :--- | :--- | :--- |
| **THRYNX Universal APK** | `v1.0.0` | **25.88 MB** | Universal (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | [**Download APK (`app-debug.apk`)**](https://github.com/sobuj-here/thrynx/releases/download/v1.0.0/app-debug.apk) |

### Installation Instructions:
1. Download `app-debug.apk` directly to your Android device.
2. Open the downloaded package. If prompted, grant system permission to "Install Unknown Apps" for your browser or file manager.
3. Launch **THRYNX** and enjoy seamless, hardware-accelerated video playback.

---

## 🌟 Product Overview

**THRYNX** is a native, modern **Video Player** engineered for Android. Built entirely in **Kotlin** with **Jetpack Compose (Material 3)** and **AndroidX Media3 ExoPlayer**, THRYNX delivers an ultra-responsive, zero-latency playback experience backed by a clean local media library.

Designed with a strict offline-first philosophy, THRYNX contains zero telemetry, zero analytics trackers, and zero background cloud sync — ensuring your personal media stays strictly private on your device.

### Core Architectural Highlights:
- **Precision Dual-Axis Gesture HUD**: Intuitive touch controls for brightness (left screen axis), volume (right screen axis), precise timeline scrubbing, double-tap jump seek, and instant 2x fast-forward on hold.
- **Hardware-Accelerated Decoding**: High-efficiency decoding pipeline with extension renderer prefer mode supporting 4K UHD, 1080p FHD, H.264/AVC, H.265/HEVC, VP9, and AV1 video codecs.
- **Dynamic Multi-Audio Track Switching**: In-stream audio track selection (English, বাংলা, Hindi, Spanish, Original) without video reload or buffer stalls.
- **Sub-Frame Subtitle Synchronization**: Auto-detects embedded subtitle streams, supports external `.srt`, `.vtt`, and `.ass` file loading, and provides millisecond sync offset tuning (+/- 100ms, 500ms).
- **Hardware-Backed Private Vault**: Isolated, 4-digit PIN-protected hidden storage using SHA-256 hashing. Private media is completely excluded from public library queries and MediaStore indexers.
- **Dedicated 4-Tab Navigation**: Clean, uncluttered UI featuring Home, Videos, Folders, and Settings.
- **100% Offline-First Privacy**: Operates fully on-device with local Room SQLite storage and DataStore preferences.

---

## 🚀 Key Features & Capabilities

### 🎬 1. Advanced Playback Engine
- **Media3 ExoPlayer Core**: Adaptive buffering pipeline (15s minimum buffer, 60s maximum buffer, 1.5s fast startup buffer) with automatic network recovery.
- **Multi-Track Audio Engine**: Inspects embedded audio streams with language tags, channel configurations, and sample rates for instant switching.
- **Flexible Display & Aspect Ratios**:
  - `Fit to Screen`: Maintains native aspect ratio with letterboxing.
  - `Fill Screen / Crop`: Expands frame to eliminate black bars.
  - `Stretch to Window`: Fills entire screen dimensions.
  - `16:9 Widescreen` & `4:3 Standard` fixed aspect ratios.
- **Speed Controller**: Fine-grained playback speed adjustment from `0.25x` to `3.0x`.
- **Sleep Timer**: Configurable auto-pause timer (`10m`, `15m`, `30m`, `45m`, `60m`) with live countdown display.
- **Lossless Screenshot**: One-tap hardware frame capture via Android `PixelCopy` saved to `Pictures/THRYNX_Screenshots`.
- **Picture-in-Picture (PiP)**: Smooth system PiP mode for effortless multitasking.
- **Screen Touch Lock**: Single-tap control lock to prevent accidental touches during media playback.

---

### 👆 2. Precision Touch Gesture Controls

| Gesture Action | Screen Zone | Function & HUD Feedback |
| :--- | :--- | :--- |
| **Vertical Swipe (Up / Down)** | Left Side | Smooth display brightness adjustment (0% – 100%) with live HUD card |
| **Vertical Swipe (Up / Down)** | Right Side | Device media volume adjustment (0% – 100%) with live HUD card |
| **Horizontal Swipe (Left / Right)** | Center Area | High-precision timeline scrub seeking with time delta indicator (`+00:15 / -00:10`) |
| **Double Tap** | Left Zone | Instant rewind jump (configurable: 5s, 10s, 15s, 30s) |
| **Double Tap** | Right Zone | Instant forward jump (configurable: 5s, 10s, 15s, 30s) |
| **Double Tap** | Center Zone | Instant Play / Pause toggle |
| **Long Press & Hold** | Anywhere | Temporary 2.0x fast-forward playback (reverts to normal on finger lift) |
| **Single Tap** | Anywhere | Toggle on-screen control overlays (auto-hides after 4.5 seconds of inactivity) |

---

### 💬 3. Subtitle System & Real-Time Sync

- **Embedded Subtitle Detection**: Scans and parses embedded subtitle tracks with language and format identification.
- **External Subtitle File Loader**: Storage Access Framework (SAF) integration supporting `.srt`, `.vtt`, and `.ass` files.
- **Real-Time Timing Offset**: Adjust subtitle synchronization on-the-fly (`-500ms`, `-100ms`, `Reset`, `+100ms`, `+500ms`) to correct desynced dialog.
- **Customizable Subtitle Styling**:
  - Font Size: Adjustable from `12sp` to `32sp`.
  - Color Palette: High-contrast White, Yellow, Cyan, Green.
  - Background Opacity: Fully transparent to solid black container.

---

### 📂 4. Media Library Organization

The application is structured into **4 focused navigation tabs**:

#### 1. Home (`Screen.Home`)
- **Continue Watching Hero Card**: Instant resume card for the last played video with live progress bar and exact millisecond playhead recovery.
- **Recently Played**: Carousel of recently accessed videos with progress badges.
- **Recently Added**: Carousel of newly discovered media files on the device.
- **Favorite Videos**: Dedicated carousel for starred media.
- **Video Folders**: Quick-access shortcuts to frequently opened directories.
- **Network Stream Player**: Built-in network player for direct HTTP, HTTPS, HLS (`.m3u8`), and DASH (`.mpd`) video streams.

#### 2. Videos (`Screen.Videos`)
- Complete local video catalog with fast thumbnail caching.
- **Adaptive Layouts**: Toggle between single-column List view and responsive multi-column Grid view.
- **Search & Duration Filters**: Instant search by filename and duration filter chips (`All`, `< 5 mins`, `5–20 mins`, `> 20 mins`).
- **Multi-Factor Sorting**: Sort by Date (Newest/Oldest), Name (A-Z/Z-A), Size (Largest/Smallest), and Duration (Longest/Shortest).
- **Contextual Action Sheet**: Play, Play Next, Favorite, Move to Private Vault, File Details & Codec Info, Rename, Delete with confirmation, and Share via `FileProvider`.

#### 3. Folders (`Screen.Folders`)
- Automatic directory grouping with video count badges and latest thumbnail previews.
- **Folder Drill-Down**: Dedicated folder detail view with search and custom sorting.
- **Custom Directory Import (SAF)**: Add custom directories or external SD card locations via Android's Storage Access Framework.
- **Private Vault**: Isolated, PIN-protected hidden storage with SHA-256 verification. Media moved into the vault is removed from public gallery queries.

#### 4. Settings (`Screen.Settings`)
- **Playback Controls**: Default playback speed, jump interval (5s–30s), resume playback preference, auto-play next video toggle, background playback toggle.
- **Display & Gestures**: Gesture controls toggle, default orientation, default aspect ratio, hardware acceleration mode (Auto, Hardware, Software).
- **Subtitles**: Styling preferences (font size, font color, background opacity).
- **Security & Vault**: Setup or modify 4-digit Private Vault PIN.
- **Storage & Maintenance**: Manual library rescan, clear app cache, wipe playback history.
- **Language & Theme**: Obsidian Dark theme, system theme, and instant **English ⟷ বাংলা (Bangla)** language toggle.

---

## 🔒 Security, Privacy & Android Compliance

- **100% Offline-First Architecture**: THRYNX operates completely on-device. No cloud servers, no analytics SDKs, and no telemetry tracking.
- **Secure File Sharing**: Media sharing utilizes `androidx.core.content.FileProvider` with temporary read permission grants (`FLAG_GRANT_READ_URI_PERMISSION`).
- **Data Protection Rules**: Configured with `data_extraction_rules.xml` and `backup_rules.xml` to prevent leaking private vault PINs during cloud backups or device transfers.
- **Targeted Permissions**: Strictly requests `READ_MEDIA_VIDEO` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 8–12), with zero microphone or background location access.

---

## 🌐 Dual-Language Parity (English & বাংলা)

THRYNX provides 100% localization parity between **English** and **বাংলা (Bangla)**. All UI strings, HUD alerts, settings descriptions, dialog messages, and gesture tooltips dynamically adapt to the user's selected language.

---

## 🛠️ Technology Stack

| Layer | Component | Version / Specification |
| :--- | :--- | :--- |
| **Language & Concurrency** | Kotlin, Coroutines, StateFlow | 1.9.22 |
| **UI Framework** | Jetpack Compose (Material Design 3) | BOM 2024.02.00 |
| **Media Playback Engine** | AndroidX Media3 ExoPlayer | 1.2.1 (Core, UI, Session, HLS, DASH) |
| **Architecture** | MVVM / Clean Architecture | Single-Activity Architecture |
| **Local Persistence** | Room SQLite Database | Version 3 (`fallbackToDestructiveMigration`) |
| **Preferences** | Jetpack DataStore Preferences | Proto/Key-Value Datastore |
| **Image Loading** | Coil Compose | 2.5.0 |
| **Build Configuration** | AGP 8.2.2 | Target SDK 34 (Android 14) \| Min SDK 26 (Android 8.0) |

---

## 🧪 Testing & Verification

Comprehensive unit test suites validate the core player state machine, library sorting, and private vault security:

```bash
./gradlew testDebugUnitTest
```

- `VideoPlayerStateTest`: Validates audio/subtitle stream resolution, queue navigation, repeat mode transitions, and resolution badge classification.
- `VideoLibraryRepositoryTest`: Validates sorting algorithms (Date, Name, Size, Duration), duration filtering, and folder grouping.
- `PrivateVaultSecurityTest`: Validates private video exclusion from public library queries and 4-digit SHA-256 PIN validation.

---

## 📄 License

```
Copyright 2026 THRYNX Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
