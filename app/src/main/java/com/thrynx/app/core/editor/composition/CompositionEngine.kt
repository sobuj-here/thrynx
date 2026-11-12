package com.thrynx.app.core.editor.composition

import com.thrynx.app.core.editor.keyframe.KeyframeInterpolator
import com.thrynx.app.core.editor.transitions.TransitionEngine
import com.thrynx.app.core.editor.transitions.TransitionFrameState
import com.thrynx.app.core.model.*

data class RenderableClip(
    val clip: TimelineClipModel,
    val trackType: TrackType,
    val sourcePositionMs: Long,
    val transform: ClipTransform,
    val activeVolume: Float,
    val transitionState: TransitionFrameState? = null
)

data class CompositionSnapshot(
    val timestampMs: Long,
    val canvasRatio: CanvasAspectRatio,
    val activeVideoClips: List<RenderableClip>,
    val activeAudioClips: List<RenderableClip>,
    val activeTextClips: List<RenderableClip>,
    val activeOverlayClips: List<RenderableClip>
)

object CompositionEngine {

    fun evaluate(project: ProjectModel, timestampMs: Long): CompositionSnapshot {
        val activeVideos = mutableListOf<RenderableClip>()
        val activeAudios = mutableListOf<RenderableClip>()
        val activeTexts = mutableListOf<RenderableClip>()
        val activeOverlays = mutableListOf<RenderableClip>()

        for (track in project.tracks) {
            if (track.isHidden) continue

            for (clip in track.clips) {
                if (timestampMs >= clip.timelineStartMs && timestampMs < clip.timelineEndMs) {
                    val offsetInClipMs = timestampMs - clip.timelineStartMs
                    val sourcePositionMs = (clip.sourceStartMs + (offsetInClipMs * clip.speedFactor).toLong())
                        .coerceAtMost(clip.sourceEndMs)

                    // Compute keyframed transform & volume
                    val transform = KeyframeInterpolator.interpolateTransform(
                        clip.keyframes,
                        clip.transform,
                        offsetInClipMs
                    )
                    val baseVolume = if (track.isMuted || clip.isMuted) 0f else clip.volume
                    var volume = KeyframeInterpolator.interpolateVolume(
                        clip.keyframes,
                        baseVolume,
                        offsetInClipMs
                    )
                    // Apply Audio Fade In / Fade Out
                    if (clip.fadeInMs > 0 && offsetInClipMs < clip.fadeInMs) {
                        volume *= (offsetInClipMs.toFloat() / clip.fadeInMs.toFloat()).coerceIn(0f, 1f)
                    }
                    val remainMs = clip.durationMs - offsetInClipMs
                    if (clip.fadeOutMs > 0 && remainMs < clip.fadeOutMs) {
                        volume *= (remainMs.toFloat() / clip.fadeOutMs.toFloat()).coerceIn(0f, 1f)
                    }

                    // Check transition at clip edges
                    var transitionState: TransitionFrameState? = null
                    clip.transitionIn?.let { trans ->
                        if (offsetInClipMs < trans.durationMs && trans.durationMs > 0) {
                            val progress = offsetInClipMs.toFloat() / trans.durationMs.toFloat()
                            transitionState = TransitionEngine.calculateTransition(trans.type, progress)
                        }
                    }

                    val renderable = RenderableClip(
                        clip = clip,
                        trackType = track.type,
                        sourcePositionMs = sourcePositionMs,
                        transform = transform,
                        activeVolume = volume,
                        transitionState = transitionState
                    )

                    when (track.type) {
                        TrackType.VIDEO -> activeVideos.add(renderable)
                        TrackType.AUDIO -> activeAudios.add(renderable)
                        TrackType.TEXT -> activeTexts.add(renderable)
                        TrackType.SUBTITLE -> activeTexts.add(renderable)
                        TrackType.OVERLAY -> activeOverlays.add(renderable)
                        TrackType.IMAGE -> activeVideos.add(renderable)
                    }
                }
            }
        }

        return CompositionSnapshot(
            timestampMs = timestampMs,
            canvasRatio = project.canvasRatio,
            activeVideoClips = activeVideos,
            activeAudioClips = activeAudios,
            activeTextClips = activeTexts,
            activeOverlayClips = activeOverlays
        )
    }
}
