package com.thrynx.app.core.model

import android.net.Uri

/**
 * Universal media item model representing a local or loaded video/audio track.
 */
data class MediaItemModel(
    val id: Long = 0L,
    val title: String = "",
    val uri: Uri = Uri.EMPTY,
    val path: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: Uri? = null,
    val dateAdded: Long = 0L,
    val folderName: String = "",
    val isFavorite: Boolean = false,
    val lastPositionMs: Long = 0L,
    val isVideo: Boolean = true
)

/**
 * Representation of an embedded or external audio track within a media container.
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
    val isSelected: Boolean = false,
    val isExternal: Boolean = false
) {
    val displayLanguage: String
        get() = when (language.lowercase()) {
            "eng", "en" -> "English"
            "ben", "bn" -> "বাংলা (Bangla)"
            "hin", "hi" -> "Hindi"
            "spa", "es" -> "Spanish"
            "ara", "ar" -> "Arabic"
            "und", "" -> if (label.isNotBlank()) label else "Track #${trackIndex + 1}"
            else -> language.uppercase()
        }
}

/**
 * Representation of embedded or external subtitles.
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
 * Live playback state snapshot observed by UI and services.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isAudioOnly: Boolean = false,
    val currentMedia: MediaItemModel? = null,
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedAudioTrack: AudioTrackInfo? = null,
    val selectedSubtitleTrack: SubtitleTrackInfo? = null,
    val isControlsLocked: Boolean = false,
    val isRepeatOne: Boolean = false,
    val isShuffle: Boolean = false
)

/**
 * Aspect ratios supported in player canvas and video editing studio.
 */
enum class CanvasAspectRatio(val label: String, val widthRatio: Float, val heightRatio: Float) {
    RATIO_16_9("16:9 Landscape", 16f, 9f),
    RATIO_9_16("9:16 Portrait / Shorts", 9f, 16f),
    RATIO_1_1("1:1 Square", 1f, 1f),
    RATIO_4_5("4:5 Instagram Feed", 4f, 5f),
    RATIO_21_9("21:9 Cinema UltraWide", 21f, 9f),
    RATIO_FIT("Fit to Screen", 0f, 0f)
}
