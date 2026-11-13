# THRYNX — High-Performance Native Video & Audio Player for Android

<p align="center">
  <img src="docs/screenshots/thrynx_hero_banner.svg" alt="THRYNX Hero Banner" width="100%">
</p>

<p align="center">
  <a href="https://github.com/sobuj-here/thrynx/releases/download/v1.0.0/THRYNX-v1.0.0.apk">
    <img src="https://img.shields.io/badge/📥%20DOWNLOAD%20APK-v1.0.0%20(Official)-00E5FF?style=for-the-badge&logo=android&logoColor=0B0E14" height="42" alt="Download APK" />
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/sobuj-here/thrynx/releases/tag/v1.0.0">
    <img src="https://img.shields.io/badge/📦%20RELEASE%20PAGE-v1.0.0-2979FF?style=for-the-badge&logo=github&logoColor=white" height="42" alt="Release Page" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green?style=flat-square&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Engine-Media3%20ExoPlayer-00E5FF?style=flat-square" alt="Engine" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline--First-00E676?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/Languages-English%20%7C%20%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE-orange?style=flat-square" alt="Languages" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square" alt="License" />
</p>

---

## 📥 Direct Download & Installation

Get the latest official APK build directly on your Android device:

<div align="center">

[![Download THRYNX APK](https://img.shields.io/badge/⚡_Download_THRYNX_APK-v1.0.0-00E5FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/sobuj-here/thrynx/releases/download/v1.0.0/THRYNX-v1.0.0.apk)

**Direct Package**: [`THRYNX-v1.0.0.apk`](https://github.com/sobuj-here/thrynx/releases/download/v1.0.0/THRYNX-v1.0.0.apk) &bull; **Compatibility**: Android 8.0 (Oreo) to Android 14 (API 26–34) &bull; **Architecture**: Universal

</div>

### Installation Steps:
1. Tap the **Download APK** button above to download `THRYNX-v1.0.0.apk`.
2. Tap the downloaded file in your browser or file manager.
3. If prompted, permit **"Install Unknown Apps"** for the browser or file manager.
4. Open **THRYNX** to enjoy fast, hardware-accelerated playback.

---

## 🌟 Overview

**THRYNX** is a native, modern **Video and Audio Player** crafted in **Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3 ExoPlayer**. 

Engineered with an offline-first philosophy, THRYNX delivers an ultra-smooth, zero-latency playback experience with hardware-accelerated video decoding, multi-track audio switching, real-time subtitle synchronization, intuitive touch gestures, and true physical storage isolation for private media.

---

## 🏗️ Project Architecture & Directory Structure

THRYNX follows Android recommended architecture (Unidirectional Data Flow, MVVM, and clean separation of concerns):

```
THRYNX/
├── app/
│   ├── build.gradle.kts                   # Dependencies, R8 code/resource shrinking, ProGuard config
│   ├── proguard-rules.pro                 # Media3, Room, and Coroutines keep rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # MediaStore permissions, AudioBecomingNoisy receiver, PiP config
│       │   ├── java/com/thrynx/app/
│       │   │   ├── MainActivity.kt        # Single activity host, SAF permissions, Physical Vault isolation, PiP
│       │   │   ├── core/
│       │   │   │   ├── datastore/
│       │   │   │   │   └── PreferencesManager.kt  # User preferences (view modes, HUD, audio disconnect, PIN)
│       │   │   │   ├── db/
│       │   │   │   │   ├── AppDatabase.kt         # Room database with schema migration
│       │   │   │   │   ├── MediaItemDao.kt        # Playback history, favorites, and vault queries
│       │   │   │   │   └── MediaItemEntity.kt     # Media item persistence model
│       │   │   │   ├── media/
│       │   │   │   │   ├── MediaModels.kt         # Domain models (MediaItemModel, FolderItemModel)
│       │   │   │   │   └── MediaStoreScanner.kt   # System MediaStore video/audio resolver & SAF scanner
│       │   │   │   └── player/
│       │   │   │       ├── ThrynxPlayerManager.kt # Media3 ExoPlayer lifecycle, audio focus, stream controller
│       │   │   │       └── VideoPlayerState.kt    # Immutable UI state for playback screen
│       │   │   ├── feature/
│       │   │   │   ├── home/
│       │   │   │   │   └── HomeScreen.kt          # Continue Watching, Recents, Favorites, Vault shortcut, Stream
│       │   │   │   ├── videos/
│       │   │   │   │   └── VideosScreen.kt        # All Videos / Folders view, blue active item, multi-select, sort
│       │   │   │   ├── audio/
│       │   │   │   │   └── AudioScreen.kt         # Music player, audio folders, floating mini-player bar, batch actions
│       │   │   │   ├── player/
│       │   │   │   │   └── VideoPlayerScreen.kt   # ExoPlayer surface, gestures, zoom, HUD, bottom-left orientation
│       │   │   │   └── settings/
│       │   │   │       └── SettingsScreen.kt      # Professional 8-section organized configuration center
│       │   │   └── ui/
│       │   │       ├── components/
│       │   │       │   └── VideoThumbnail.kt      # Native hardware thumbnail generator with in-memory LruCache
│       │   │       ├── navigation/
│       │   │       │   └── NavigationGraph.kt     # Compose navigation destination graph (Home, Videos, Audio, Settings)
│       │   │       └── theme/
│       │   │           ├── Color.kt               # Obsidian dark palette & neon cyan accents
│       │   │           ├── Theme.kt               # Dynamic Material 3 theme configuration
│       │   │           └── Type.kt                # Typography scales
│       │   └── res/
│       │       ├── values/strings.xml             # Natural English UI string definitions
│       │       ├── values-bn/strings.xml          # Natural Bengali (বাংলা) localization
│       │       └── xml/                           # Scoped storage & data extraction security rules
│       └── test/java/com/thrynx/app/              # Unit test suites (Player, Repository, Vault)
├── docs/                                          # Architectural diagrams, specifications, and screenshots
└── README.md
```

---

## 🚀 Key Features

### 🎬 1. Advanced Playback Engine
- **Media3 ExoPlayer Core**: Hardware-accelerated decoding pipeline supporting 4K UHD, 1080p FHD, H.264/AVC, H.265/HEVC, VP9, and AV1 video codecs.
- **Universal Screen Orientation Button**: Readily accessible quick-toggle button positioned at the bottom-left above the timeline, accessible in both Portrait and Landscape orientations.
- **Play Time & Remaining Time HUD**: Tap the timeline duration to toggle between Total Duration (`05:30`) and Remaining Time (`-02:15`).
- **Two-Finger Pinch-to-Zoom**: Multi-touch pinch-to-zoom allows scaling the video canvas from `0.5x` to `4.0x` with live HUD percentage feedback.
- **Precision Gesture Controls**:
  - **Left Half Vertical Swipe**: Adjust screen brightness (0% – 100%).
  - **Right Half Vertical Swipe**: Adjust device media volume (0% – 100%).
  - **Horizontal Scrubbing**: Seek through video with delta indicator (`+00:15 / -00:10`).
  - **Double-Tap Seek**: Left 45% screen rewinds 10s; right 45% forwards 10s; center 10% toggles play/pause.
  - **Long Press**: Instant 2.0x playback speed acceleration while held.
- **Bluetooth & Headset Auto-Pause**: Automatically pauses playback with an on-screen notification when Bluetooth headphones disconnect or wired headsets are unplugged (`ACTION_AUDIO_BECOMING_NOISY`).
- **Seamless Picture-in-Picture (PiP)**: Smooth system PiP mode activates automatically when switching to other apps (`onUserLeaveHint`).
- **Dual/Multi-Audio Track Selection**: Switch between embedded audio streams without video reloads.
- **Sub-Frame Subtitle Synchronization**: Auto-detects embedded subtitles or load external `.srt`, `.vtt`, `.ass` files with millisecond offset tuning.

---

### 🎵 2. Dedicated Audio & Music Player
- **Complete Audio Library**: Automatically discovers local music files and albums across device storage.
- **Two Flexible Views**: Toggle between **All Songs** (flat track list) and **Audio Folders** (album/directory hierarchy).
- **Floating Mini-Player Bar**: Persistent bottom control bar with play/pause, seek track bar, previous/next skip, and track metadata.
- **Batch Multi-Select Mode**: Long-press any track to enter multi-select mode for bulk delete or batch vaulting.
- **Audio Sorting & Filtering**: Sort audio tracks by Title (A–Z), Date Added, File Size, and Track Duration.

---

### 📁 3. Local Media Library
- **Native Hardware Thumbnails**: Custom `VideoThumbnail` loader using Android's native `ContentResolver.loadThumbnail` (Android 10+) and `ThumbnailUtils` with memory `LruCache` for smooth 60fps scrolling without network overhead.
- **Active Video Highlight**: Currently active or recently played videos are highlighted with a cyan/blue accent border, title highlight, and progress indicator.
- **Played Time Badge**: Shows exact progress (e.g., `04:12 played`) on the video card (toggleable in Settings).
- **Smart Resume Prompt**: When opening a previously played video, a prompt asks to Resume or Start from Beginning. The Home screen continues instantly, while the Videos screen provides the prompt.
- **Folder / Flat List Toggle**: Switch between viewing videos grouped by folders or as a single unified list.
- **Multi-Select Batch Actions**: Long-press any item to select multiple videos for batch deletion or moving to Private Vault.

---

### 🔒 4. Physical Private Vault Isolation
- **True File-System Privacy**: Videos moved to the Private Vault are physically moved into the app's secure internal sandbox (`context.filesDir/vault/`) accompanied by a `.nomedia` marker file, and their records are removed from Android's system `MediaStore`.
- **Complete Invisibility**: Vaulted files are 100% invisible to Google Photos, device Gallery apps, and third-party file managers.
- **Biometric & PIN Protection**: Protected with a 4-digit PIN stored using cryptographic SHA-256 hashing.

---

### ⚙️ 5. Organized 8-Category Settings Center

Settings are structured into 8 distinct sections:

1. **List**: Toggle between All Videos and Folder View; Show/hide played time badges and progress bars on video cards.
2. **Player**: Resume playback preferences (Ask Every Time, Always Resume, Start from Beginning), default orientation, default speed, double-tap jump seek interval, clock & battery HUD toggle, auto-play next video.
3. **Decoder**: Hardware acceleration mode (Hardware / Auto / Software).
4. **Audio**: Pause playback on headset/Bluetooth disconnection, background audio playback when screen turns off.
5. **Subtitle**: Customize subtitle font size, text color, and background opacity.
6. **General**: Hidden folders manager, manual media library rescan, clear app cache, wipe playback history.
7. **Development & Security**: Set/change 4-digit Private Vault PIN, app architecture specifications, and build metadata.
8. **App Language**: Instant seamless switching between **English** and **বাংলা (Bengali)**.

---

## 🧪 Testing & Verification

THRYNX includes unit test coverage for media repository operations, player state transformations, and security models:

```bash
./gradlew testDebugUnitTest
```

- `VideoPlayerStateTest`: Validates audio track selection, subtitle stream extraction, and aspect ratio calculation.
- `VideoLibraryRepositoryTest`: Validates media sorting, folder aggregation, and duration filtering.
- `PrivateVaultSecurityTest`: Validates file physical isolation and PIN verification logic.

---

## 🌐 Dual-Language Parity (English & বাংলা)

THRYNX provides 100% natural localization parity between **English** and **বাংলা (Bangla)** without robotic phrasing or mixed languages.

---

## 📄 License

```
Copyright 2026 THRYNX Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
