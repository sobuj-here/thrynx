# THRYNX — All-in-One Multimedia Player, Video Editor & Creative Studio

<p align="center">
  <img src="docs/screenshots/thrynx_hero_banner.svg" alt="THRYNX Hero Banner" width="100%">
</p>

<p align="center">
  <a href="https://github.com/sobuj-here/thrynx/releases/latest">
    <img src="https://img.shields.io/badge/⚡%20Download-THRYNX%20v1.0.0%20APK-00E5FF?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
  </a>
  <a href="https://github.com/sobuj-here/thrynx/releases">
    <img src="https://img.shields.io/github/v/release/sobuj-here/thrynx?style=for-the-badge&color=2979FF&label=Release" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Languages-English%20%7C%20%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE-orange?style=for-the-badge" alt="Languages" />
</p>

---

## 🌟 Executive Overview
**THRYNX** is an independent, professional native Android multimedia power-station and creative studio developed with modern **Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3**. 

Designed as a single unified application, THRYNX combines:
1. **Advanced Video Player**: Multi-track audio switching (English, বাংলা, Hindi, etc.), subtitle customization, gesture controls, PiP, and audio-only background mode.
2. **Professional Video Editor**: CapCut-grade non-destructive multi-track timeline, keyframe animation, split/trim/speed (0.25x–4x), transitions, and OpenGL ES color grading.
3. **Music Player Studio**: Local library indexing, live animated waveform visualizer, queue manager, sleep timer, and background playback via `MediaSessionService`.
4. **Photo-to-Video Slideshow Maker**: Cinematic Ken Burns zoom effect, transition engine, soundtrack synchronization, and multi-ratio exports.
5. **Project Management System**: Room SQLite database persistence, auto-saving drafts, non-destructive editing, and project recovery.
6. **100% Offline-First Privacy**: No telemetry, no ads, and no cloud uploads. All media and projects stay strictly on your local device.

---

## 📸 Feature Showcase

<p align="center">
  <img src="docs/screenshots/feature_showcase.svg" alt="THRYNX Core Capabilities Showcase" width="100%">
</p>

---

## 🚀 Key Features

### 🎬 1. Advanced Video Player
- **Multiple Embedded Audio Tracks**: Automatically detects multiple audio streams (English, বাংলা, Hindi, Original) and switches instantly via `TrackSelectionOverride` without resetting the playback timestamp or re-buffering.
- **Subtitle Styling & External Loaders**: Detects embedded subtitles and loads external `.srt` / `.vtt` files. Fully customizable font size, text color, background opacity, and time-offset delay.
- **Intuitive Gesture Controls**:
  - Vertical swipe on left side: Screen brightness control
  - Vertical swipe on right side: Media volume control
  - Double-tap left / right: Fast ±10-second seek
  - Pinch-to-zoom & pan
- **Audio-Only Background Mode**: Turns off video decoding to listen to video audio in the background or with the screen turned off.
- **Picture-in-Picture (PiP)**: Smooth system PiP mode for multitasking.
- **Playback Position Memory**: Automatic resume prompt restoring the exact position where you left off.

### ✂️ 2. CapCut-Grade Custom Video Editing Engine
- **Independent Modular Architecture**: Built from the ground up without relying on fragile wrappers:
  - `TimelineEngine`: Manages multi-track layout, frame-accurate clip splitting, in/out trimming, clip duplication, deletion, and speed factor (0.25x to 4.0x).
  - `CompositionEngine`: Real-time multi-layer compositor calculating active clips across Video, Audio, Text, and Overlay tracks.
  - `KeyframeInterpolator`: 6-degree property curves (Translation X/Y, Scale X/Y, Rotation, Opacity, Volume) along Linear, Ease-In, Ease-Out, and Bezier interpolations.
  - `EffectsEngine`: Real-time color grading (Brightness, Contrast, Saturation, Temperature, Vignette, Grayscale, Vintage Film, and Cinematic Teal) with OpenGL ES fragment shaders.
  - `TransitionEngine`: Cross Dissolve, Fade to Black, Slide Left/Right, Zoom In, Wipe, and Blur Push.
  - `Undo/Redo History Stack`: Full state recovery for every editing action.
  - **Bangla & English Typography**: Native text layers with full support for Bengali unicode characters without text clipping.

### 🎵 3. Music Studio & Audio Visualizer
- **Complete Local Media Library**: Browse by Songs, Albums, Artists, Folders, Playlists, and Favorites.
- **Live Amplitude Visualizer**: Real-time animated waveform bars responsive to playing audio.
- **Sleep Timer**: Auto-pause playback after 15, 30, 45, or 60 minutes.
- **MediaSessionService**: Notification playback controls, headphone disconnect auto-pause (`AUDIO_BECOMING_NOISY`), and lockscreen artwork.

### 🎙️ 4. Audio Studio & Voice-Over Recording
- **Microphone Voice-Over**: Record voice narration directly onto the editing timeline using Android's native `MediaRecorder` in AAC/M4A format.
- **Extract Audio from Video**: Built-in hardware `MediaExtractor` + `MediaMuxer` tool to strip video frames and extract lossless audio tracks.
- **Volume Envelope & Mixing**: Fade-in and fade-out audio transitions with multi-track balance.

### 🖼️ 5. Photo-to-Video Slideshow Creator
- Multi-photo picker with slide duration sliders (1.0s to 8.0s per photo).
- **Cinematic Ken Burns Effect**: Generates dynamic camera pan-and-zoom keyframe motions.
- Canvas Aspect Ratios: **16:9 Landscape**, **9:16 Shorts/Reels**, **1:1 Square**, and **4:5 Instagram Feed**.
- One-click export or conversion into a full multi-track project.

### 🌐 6. Dual-Language Localization (English & বাংলা)
- Complete language parity across all menus, buttons, dialogs, error messages, and player controls.
- Dynamic runtime language switching from Settings without restarting playback or losing project progress.

---

## 🛠️ Technology Stack & Architecture

```
com.thrynx.app
├── core/
│   ├── model/         # Domain entities, Timeline clips, Keyframes, Effects
│   ├── database/      # SQLite Room database (Projects, History, Downloads)
│   ├── datastore/     # Jetpack DataStore Preferences
│   ├── localization/  # Dynamic Locale Context Wrapper (LocaleHelper)
│   ├── player/        # Media3 ExoPlayer Engine & Track Selector
│   ├── service/       # MediaSessionService, Notifications & Lockscreen
│   ├── editor/        # Custom Editing Engine (Timeline, Shaders, Keyframes)
│   ├── media/         # MediaStore Scanner & AudioExtractor
│   ├── download/      # Background Download Manager Service
│   └── designsystem/  # Modern Obsidian/Cyan Theme & Glassmorphism UI
├── feature/           # 10 Screen modules (Home, Player, Music, Editor, etc.)
└── ui/navigation/     # Jetpack Compose Navigation Graph & Mini-Player
```

- **Kotlin**: 1.9.22
- **Jetpack Compose**: Compose BOM 2024.02.00, Material 3
- **Media Engine**: AndroidX Media3 ExoPlayer & Transformer 1.2.1
- **Persistence**: Room SQLite 2.6.1 & DataStore Preferences 1.0.0
- **Rendering**: OpenGL ES 2.0 Shaders & Hardware MediaCodec H.264/AAC

---

## 📦 Installation & Build Instructions

### Download Pre-built APK
Download the latest verified APK directly from [Releases](https://github.com/sobuj-here/thrynx/releases/latest):
- **File**: `app-debug.apk` (~21.7 MB)
- **Min Android Version**: Android 8.0 Oreo (API 26)

### Build From Source
Clone the repository and compile using the included Gradle wrapper:

```bash
# Clone repository
git clone https://github.com/sobuj-here/thrynx.git
cd thrynx

# Run Automated Unit Tests (10/10 Tests Passed)
.\gradlew.bat testDebugUnitTest

# Assemble Debug APK
.\gradlew.bat assembleDebug

# Output APK is located at:
# app/build/outputs/apk/debug/app-debug.apk

# Install to connected device / emulator
.\gradlew.bat installDebug
```

---

## 🧪 Automated Test Verification
THRYNX includes verified JUnit test suites testing core editing mathematics:
- `TimelineEngineTest`: Verifies clip trimming, splitting at playhead, duplication, deletion, and undo/redo stacks.
- `KeyframeAndCompositionTest`: Verifies linear and bezier keyframe interpolations, volume curves, transition states, and multi-track compositing.

---

## 🔒 Privacy & Local-First Commitment
- **Zero Cloud Uploads**: All video rendering, audio mixing, and project storage happen 100% locally on your device.
- **No Trackers**: Zero telemetry, analytics SDKs, or third-party ad networks.

---

## 📄 License & Credits
Developed by **Sobuj** • © 2026 THRYNX Studio. All rights reserved.
Licensed under the [Apache License 2.0](LICENSE).
