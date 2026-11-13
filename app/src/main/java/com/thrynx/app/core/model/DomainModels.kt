package com.thrynx.app.core.model

import android.net.Uri

/**
 * Universal media item model representing a local or imported video.
 */
data class MediaItemModel(
    val id: Long = 0L,
    val title: String = "",
    val uri: Uri? = null,
    val path: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "video/mp4",
    val dateAdded: Long = 0L,
    val folderName: String = "",
    val isFavorite: Boolean = false,
    val lastPositionMs: Long = 0L,
    val isVideo: Boolean = true,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Long = 0L
) {
    val mediaUriString: String
        get() = uri?.toString() ?: path

    val resolutionLabel: String
        get() = when {
            width >= 3840 || height >= 2160 -> "4K UHD"
            width >= 2560 || height >= 1440 -> "2K QHD"
            width >= 1920 || height >= 1080 -> "1080p FHD"
            width >= 1280 || height >= 720 -> "720p HD"
            width > 0 && height > 0 -> "${width}x${height}"
            else -> "SD"
        }
}

/**
 * Aggregated video folder model.
 */
data class FolderItemModel(
    val name: String,
    val path: String,
    val videoCount: Int,
    val latestMedia: MediaItemModel? = null,
    val isPrivate: Boolean = false
)

/**
 * Representation of an embedded audio track within a video container.
 */
data class AudioTrackInfo(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val mimeType: String,
    val channelCount: Int,
    val sampleRate: Int,
    val isSelected: Boolean = false
) {
    val displayLanguage: String
        get() = when (language.lowercase()) {
            "eng", "en" -> "English"
            "ben", "bn" -> "বাংলা (Bangla)"
            "hin", "hi" -> "Hindi"
            "spa", "es" -> "Spanish"
            "ara", "ar" -> "Arabic"
            "jpn", "ja" -> "Japanese"
            "fra", "fr" -> "French"
            "deu", "ger", "de" -> "German"
            "rus", "ru" -> "Russian"
            "und", "" -> if (label.isNotBlank()) label else "Track #${trackIndex + 1}"
            else -> language.uppercase()
        }
}

/**
 * Representation of embedded or external subtitle tracks.
 */
data class SubtitleTrackInfo(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String,
    val mimeType: String,
    val isSelected: Boolean = false,
    val isExternal: Boolean = false,
    val externalUri: Uri? = null
) {
    val displayLanguage: String
        get() = when (language.lowercase()) {
            "eng", "en" -> "English"
            "ben", "bn" -> "বাংলা (Bangla)"
            "hin", "hi" -> "Hindi"
            "spa", "es" -> "Spanish"
            "ara", "ar" -> "Arabic"
            "jpn", "ja" -> "Japanese"
            "fra", "fr" -> "French"
            "und", "" -> if (label.isNotBlank()) label else "Subtitle #${trackIndex + 1}"
            else -> language.uppercase()
        }
}

/**
 * Visual styling options for subtitle rendering.
 */
data class SubtitleStyle(
    val fontSizeSp: Float = 18f,
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#000000",
    val backgroundOpacity: Float = 0.5f,
    val outlineColorHex: String = "#000000",
    val delayMs: Long = 0L
)

/**
 * Repeat modes for video playback.
 */
enum class RepeatMode {
    OFF,
    ONE,
    ALL
}

/**
 * Video canvas aspect ratio scaling modes.
 */
enum class AspectRatioMode(val label: String) {
    FIT("Fit to Screen"),
    FILL_CROP("Fill Screen (Crop)"),
    STRETCH("Stretch to Window"),
    RATIO_16_9("16:9 Widescreen"),
    RATIO_4_3("4:3 Standard")
}

/**
 * Hardware vs Software video decoder configuration.
 */
enum class VideoDecoderMode(val label: String) {
    AUTO("Automatic (Recommended)"),
    HARDWARE("Hardware Accelerated"),
    SOFTWARE("Software Decoder")
}

/**
 * Library sorting options.
 */
enum class SortOption(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    DURATION_DESC("Duration (Longest)"),
    DURATION_ASC("Duration (Shortest)")
}

/**
 * Library duration filtering.
 */
enum class DurationFilter(val label: String) {
    ALL("All Videos"),
    SHORT("< 2 mins"),
    MEDIUM("2 - 10 mins"),
    LONG("> 10 mins")
}

/**
 * Complete video playback state snapshot observed across THRYNX.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val currentMedia: MediaItemModel? = null,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedAudioTrack: AudioTrackInfo? = null,
    val selectedSubtitleTrack: SubtitleTrackInfo? = null,
    val isControlsLocked: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,
    val queue: List<MediaItemModel> = emptyList(),
    val queueIndex: Int = 0,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val subtitleDelayMs: Long = 0L,
    val rotationDegrees: Int = 0,
    val zoomScale: Float = 1.0f,
    val isPipActive: Boolean = false
) {
    val hasNext: Boolean get() = queueIndex < queue.size - 1
    val hasPrevious: Boolean get() = queueIndex > 0
}
