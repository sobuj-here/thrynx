# THRYNX — High-Performance Native Video & Audio Media Studio for Android

<p align="center">
  <img src="docs/screenshots/thrynx_hero_banner.svg" alt="THRYNX Hero Banner" width="100%">
</p>

<p align="center">
  
  </a>
  &nbsp;&nbsp;
  <a href="https://github.com/sobuj-here/thrynx/releases/tag/v1.2.0">
    <img src="https://img.shields.io/badge/📦%20RELEASE%20PAGE-v1.2.0-2979FF?style=for-the-badge&logo=github&logoColor=white" height="42" alt="Release Page" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v1.2.0-00E5FF?style=flat-square&logo=android&logoColor=white" alt="Version" />
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green?style=flat-square&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Engine-Media3%20ExoPlayer-00E5FF?style=flat-square" alt="Engine" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Online%20Music-YouTube%20Music%20Discovery-FF0000?style=flat-square&logo=youtube" alt="Online Music" />
  <img src="https://img.shields.io/badge/Audio-10--Band%20Parametric%20EQ-00E676?style=flat-square" alt="Audio EQ" />
  <img src="https://img.shields.io/badge/Lyrics-Synced%20LRCLIB-FF4081?style=flat-square" alt="Lyrics" />
  <img src="https://img.shields.io/badge/Bluetooth-Full%20MediaSession-00B0FF?style=flat-square&logo=bluetooth" alt="Bluetooth" />
  <img src="https://img.shields.io/badge/Languages-English%20%7C%20%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE-orange?style=flat-square" alt="Languages" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square" alt="License" />
</p>

---

## 📥 Direct Download & Installation

Get the latest official APK build directly on your Android device:

<div align="center">

[![Download THRYNX APK](https://img.shields.io/badge/⚡_Download_THRYNX_APK-v1.2.0-00E5FF?style=for-the-badge&logo=android&logoColor=black)](https://github.com/sobuj-here/thrynx/releases/download/v1.2.0/THRYNX-v1.2.0.apk)

</div>

### Installation Steps:
1. Tap the **Download APK** button above to download `THRYNX-v1.2.0.apk`.
2. Open the downloaded file in your browser or file manager.
3. If prompted, grant **"Install Unknown Apps"** permission.
4. Launch **THRYNX Media Studio** to enjoy high-performance video, online music discovery, and audiophile-grade sound.

---

## 🌟 Overview

**THRYNX** is an advanced native multimedia studio and audio-video powerhouse built with **Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3 ExoPlayer**. 

Engineered for speed, privacy, and fluid aesthetics, THRYNX combines a 4K hardware-accelerated video player, an online YouTube Music explorer, an offline audio downloader, an Apple Music-style Now Playing sheet with ambient mesh gradients, a 10-band parametric equalizer, synchronized scrolling lyrics, call-smart audio focus management, and a physical private security vault.

---

## 🏗️ Project Architecture & Directory Structure

THRYNX follows modern Android architectural standards (Unidirectional Data Flow, MVVM, and clean separation of concerns):

```
THRYNX/
├── app/
│   ├── build.gradle.kts                   # Dependencies, R8 code/resource shrinking, ProGuard config
│   ├── proguard-rules.pro                 # Media3, Room, OkHttp, and Coroutines keep rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # MediaStore permissions, AudioBecomingNoisy receiver, PiP config
│       │   ├── java/com/thrynx/app/
│       │   │   ├── MainActivity.kt        # Single activity host, SAF permissions, Physical Vault, PiP
│       │   │   ├── core/
│       │   │   │   ├── database/          # Room database (v4), HistoryDao, VideoDao, AudioDao, FavoriteOnlineTrackDao
│       │   │   │   ├── datastore/         # PreferencesManager (DataStore key-value settings)
│       │   │   │   ├── designsystem/      # Theme, Color, MeshGradient, FadeCurve, Formatters
│       │   │   │   ├── lyrics/            # LrcLibClient & synchronized lyric timestamp parsers
│       │   │   │   ├── media/             # MediaStoreScanner (SAF resolver, Scoped Storage deletion)
│       │   │   │   ├── model/             # Domain models (MediaItemModel, FolderItemModel, PlaybackState)
│       │   │   │   ├── player/            # ThrynxPlayerManager, YouTubeAudioResolver, AudioDownloadManager, EqualizerProcessor
│       │   │   │   └── service/           # ThrynxMediaPlaybackService (Foreground playback service)
│       │   │   ├── feature/
│       │   │   │   ├── home/              # HomeScreen (Media Studio, Dual-action Network Stream, Recents, Vault)
│       │   │   │   ├── videos/            # VideosScreen (List/Grid view, blue active item, multi-select)
│       │   │   │   ├── audio/             # AudioScreen (Local Tracks & YouTube Music tabs, Liked Songs, Search)
│       │   │   │   │   ├── NowPlayingSheet.kt # Ambient mesh gradient, ThinSlider, lyrics, Up Next Queue
│       │   │   │   │   └── components/    # MeshGradientBackground, ThinSlider
│       │   │   │   ├── folders/           # FoldersScreen (Directory hierarchy, multi-select folder delete)
│       │   │   │   ├── player/            # VideoPlayerScreen (Gestures, zoom 1.0x-4.0x, PiP, lock HUD)
│       │   │   │   └── settings/          # SettingsScreen (6 Categorized Settings Hub with deep sub-pages)
│       │   │   └── ui/
│       │   │       ├── components/        # VideoThumbnail with LruCache memory pool
│       │   │       └── navigation/        # NavigationGraph (Bottom bar, route destination mapping)
│       │   └── res/
│       │       ├── values/strings.xml     # Natural English UI string definitions
│       │       ├── values-bn/strings.xml  # Natural Bengali (বাংলা) localization
│       │       └── xml/                   # Scoped storage & file provider rules
│       └── test/                          # Unit test suites (Player, Repository, Vault)
├── docs/                                  # Architectural specifications and diagrams
└── README.md
```

---

## 🚀 Key Features

### 🎬 1. Advanced Video Player Engine
- **Media3 ExoPlayer Core**: Hardware-accelerated decoding supporting 4K UHD, 1080p FHD, H.264/AVC, H.265/HEVC, VP9, and AV1.
- **Smart Dual-Action Network Streams**: Open online streams or YouTube links with instant choice between **Watch Video** (video player) or **Play Audio** (background audio player).
- **Universal Screen Orientation Button**: Readily accessible button at the bottom-left above the timeline in both Portrait and Landscape.
- **Two-Finger Pinch-to-Zoom**: Multi-touch zoom allows scaling video canvas from `1.0x` to `4.0x` with double-tap reset.
- **Clean Touch Lock HUD**: Video lock button auto-hides during playback; touches reveal it smoothly for 3.5 seconds.
- **Precision Gesture Controls**:
  - **Left Vertical Swipe**: Screen brightness adjustment.
  - **Right Vertical Swipe**: Media volume adjustment via Android AudioManager.
  - **Horizontal Scrubbing**: Video timeline seek with time delta indicator.
  - **Double-Tap Seek**: Rewind 10s (left), forward 10s (right), play/pause (center).
- **Seamless Picture-in-Picture (PiP)**: Activates automatically when switching apps (`onUserLeaveHint`).
- **Dual/Multi-Audio & Subtitles**: Instant track switching without reloading video.

---

### 🎵 2. YouTube Music Discovery & Audiophile Player
- **Online YouTube Music Explorer**: Live search suggestions and discovery inside the Audio Player.
- **Trending & Mood Categories**: Quick-access genre chips (*Top Hits, Lo-Fi, Pop, Rock, Hip Hop, Chill, EDM, Acoustic*).
- **Direct Offline Audio Downloader**: Download any online audio track directly to device storage (`Music/THRYNX`) with instant MediaStore indexing.
- **Favorites & Liked Songs**: Star or heart online songs to save them to a persistent Room database playlist for one-tap playback.
- **Up Next Queue Drawer**: View upcoming tracks, reorder playback, and manage current queue on the fly.
- **Apple Music-Style Now Playing Sheet**: Slide-up sheet with fluid dynamic mesh gradients extracted from album art.
- **10-Band Parametric Equalizer**: Pure Media3 `BaseAudioProcessor` with state-variable filters (SVF), tone controls (Bass/Treble tilt), and built-in peak limiter.
- **Real-Time Synced Scrolling Lyrics**: Word/line synchronized lyrics fetched via LRCLIB with auto-scrolling and tap-to-seek.
- **Hairline Scrubber (`ThinSlider`)**: Touch-reactive timeline capsule that expands smoothly under interaction without stuttering.
- **Persistent Docked Mini Player**: Audio control card above the bottom navigation bar for seamless browsing while listening.

---

### 🎧 3. Intelligent Audio Focus & Bluetooth Controls
- **Call Management**: Music automatically pauses when an incoming phone call is received and automatically resumes as soon as the call ends.
- **Bluetooth Disconnect Guard**: Automatically pauses playback when Bluetooth headphones or wired headsets disconnect (`ACTION_AUDIO_BECOMING_NOISY`).
- **Full MediaSession Hardware Controls**: Complete support for Play/Pause, Next, and Previous buttons on Bluetooth earphones, smartwatches, and car head units.

---

### 📁 4. Local Media Library & File Management
- **Hardware Thumbnails**: Hardware thumbnail generator with in-memory `LruCache` for zero-lag scrolling.
- **Last Played Item Highlight**: Visually indicates the most recently played video and audio with a cyan accent border and progress badge.
- **Scoped Storage File Deletion**: Full compatibility with Android 10+ and 11+ `MediaStore.createDeleteRequest` for secure, permanent deletion.
- **Folder Multi-Select**: Long-press any folder or file to enter multi-selection mode for batch operations.

---

### 🔒 5. Physical Private Vault
- **Storage Isolation**: Vaulted files are physically relocated to the app's secure private directory with a `.nomedia` marker, invisible to gallery apps.
- **PIN & Biometric Security**: Protected with 4-digit PIN authentication and biometric fingerprint login.

---

### ⚙️ 6. Categorized 6-Section Settings Hub
Organized into 6 clean, dedicated sub-pages matching the app's sleek dark theme:
1. **List Preferences**: Display format (List / Grid), thumbnail quality, file sorting, and folder filters.
2. **Player Controls**: Gestures, double-tap seek duration, screen orientation, and auto-resume.
3. **Audio Engine**: Audio quality, volume normalization, skip silence, crossfade, and 10-band equalizer.
4. **General**: Theme mode, interface language (**English** / **বাংলা**), cache cleanup, and telemetry.
5. **Development**: Developer options, log inspection, and player nerd statistics.
6. **Subtitle Preferences**: Font size, color, background contrast, text encoding, and synchronization offset.

---

## 🧪 Testing & Verification

```bash
# Run unit test suites
./gradlew testDebugUnitTest

# Build optimized release APK
./gradlew assembleRelease
```

---

## 📄 License

```
Copyright 2026 THRYNX Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
