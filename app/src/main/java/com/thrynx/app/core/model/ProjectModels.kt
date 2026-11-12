package com.thrynx.app.core.model

import java.util.UUID

enum class TrackType {
    VIDEO,
    AUDIO,
    IMAGE,
    TEXT,
    SUBTITLE,
    OVERLAY
}

enum class EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT
}

enum class EffectType(val displayName: String) {
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    TEMPERATURE("Temperature"),
    BLUR("Gaussian Blur"),
    VIGNETTE("Vignette"),
    GRAYSCALE("Black & White"),
    VINTAGE("Vintage Film"),
    CINEMATIC_TEAL("Cinematic Teal")
}

enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade to Black"),
    DISSOLVE("Cross Dissolve"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    ZOOM_IN("Zoom In"),
    WIPE_LEFT("Wipe Left"),
    BLUR_PUSH("Blur Push")
}

data class ClipTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f
)

data class KeyframeModel(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = 0L,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f,
    val easing: EasingType = EasingType.LINEAR
)

data class EffectModel(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType = EffectType.BRIGHTNESS,
    val intensity: Float = 0.5f // Range 0.0 to 1.0 (with 0.5 as neutral where applicable)
)

data class TransitionModel(
    val id: String = UUID.randomUUID().toString(),
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 500L
)

data class TimelineClipModel(
    val id: String = UUID.randomUUID().toString(),
    val mediaUri: String = "",
    val name: String = "",
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = 5000L,
    val timelineStartMs: Long = 0L,
    val durationMs: Long = 5000L,
    val speedFactor: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val transform: ClipTransform = ClipTransform(),
    val keyframes: List<KeyframeModel> = emptyList(),
    val effects: List<EffectModel> = emptyList(),
    val transitionIn: TransitionModel? = null,
    val transitionOut: TransitionModel? = null,
    // Text Layer Specific Properties
    val textContent: String = "",
    val textColorHex: String = "#FFFFFF",
    val textBackgroundColorHex: String = "#00000000",
    val textFontSizeSp: Float = 24f,
    val textFontName: String = "Default",
    val isSubtitle: Boolean = false
) {
    val timelineEndMs: Long
        get() = timelineStartMs + durationMs
}

data class TimelineTrackModel(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType = TrackType.VIDEO,
    val index: Int = 0,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
    val clips: List<TimelineClipModel> = emptyList()
)

data class ProjectModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailUri: String = "",
    val durationMs: Long = 0L,
    val canvasRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9,
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val fps: Int = 30,
    val bitrateMbps: Int = 10,
    val tracks: List<TimelineTrackModel> = listOf(
        TimelineTrackModel(id = "track_main_video", type = TrackType.VIDEO, index = 0),
        TimelineTrackModel(id = "track_bgm", type = TrackType.AUDIO, index = 1),
        TimelineTrackModel(id = "track_text", type = TrackType.TEXT, index = 2)
    )
)

enum class DownloadState {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadItemModel(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val sourceUrl: String = "",
    val destinationPath: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val state: DownloadState = DownloadState.PENDING,
    val mimeType: String = "video/mp4",
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
}
