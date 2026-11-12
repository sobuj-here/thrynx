package com.thrynx.app.core.editor.export

import androidx.media3.common.MimeTypes
import com.thrynx.app.core.model.CanvasAspectRatio
import java.io.File
import java.util.Locale

enum class ExportResolutionPreset(
    val label: String,
    val width: Int,
    val height: Int
) {
    RES_480P("480p (SD)", 854, 480),
    RES_720P("720p (HD)", 1280, 720),
    RES_1080P("1080p (Full HD)", 1920, 1080),
    RES_1440P("1440p (2K)", 2560, 1440),
    RES_2160P("4K (Ultra HD)", 3840, 2160)
}

enum class ExportQualityPreset(
    val label: String,
    val bitrateMultiplier: Float
) {
    LOW("Low", 0.6f),
    BALANCED("Balanced", 1.0f),
    HIGH("High Quality", 1.5f),
    ULTRA("Ultra (Studio)", 2.2f)
}

enum class ExportPerformanceProfile {
    COMPATIBILITY, // Prioritizes maximum compatibility (H.264 Baseline/Main)
    BALANCED,      // Balanced throughput and compression
    HIGH_QUALITY   // Maximum bitrate and encoding quality
}

data class ExportConfiguration(
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val fps: Int = 30,
    val bitrateMbps: Int = 10,
    val videoCodec: String = MimeTypes.VIDEO_H264,
    val audioCodec: String = MimeTypes.AUDIO_AAC,
    val audioBitrateKbps: Int = 192,
    val audioSampleRateHz: Int = 44100,
    val isAudioEnabled: Boolean = true,
    val qualityPreset: ExportQualityPreset = ExportQualityPreset.BALANCED,
    val performanceProfile: ExportPerformanceProfile = ExportPerformanceProfile.BALANCED,
    val aspectRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9
) {
    /**
     * Calculates the estimated output file size in bytes based on duration, video bitrate, and audio bitrate.
     * Formula: (Video Bitrate + Audio Bitrate) in bps * (durationMs / 1000) / 8
     */
    fun calculateEstimatedSizeBytes(durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val effectiveVideoBitrateBps = (bitrateMbps * 1_000_000L * qualityPreset.bitrateMultiplier).toLong()
        val effectiveAudioBitrateBps = if (isAudioEnabled) audioBitrateKbps * 1_000L else 0L
        val totalBitrateBps = effectiveVideoBitrateBps + effectiveAudioBitrateBps
        val durationSeconds = durationMs / 1000.0
        return ((totalBitrateBps * durationSeconds) / 8.0).toLong()
    }

    fun formatEstimatedSize(durationMs: Long): String {
        val bytes = calculateEstimatedSizeBytes(durationMs)
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
