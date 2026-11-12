package com.thrynx.app

import com.thrynx.app.core.editor.composition.CompositionEngine
import com.thrynx.app.core.editor.keyframe.KeyframeInterpolator
import com.thrynx.app.core.editor.transitions.TransitionEngine
import com.thrynx.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class KeyframeAndCompositionTest {

    @Test
    fun testKeyframeInterpolationLinear() {
        val kfs = listOf(
            KeyframeModel(id = "k1", timestampMs = 0L, scaleX = 1.0f, scaleY = 1.0f, opacity = 1.0f),
            KeyframeModel(id = "k2", timestampMs = 2_000L, scaleX = 2.0f, scaleY = 2.0f, opacity = 0.0f)
        )

        val defaultTransform = ClipTransform()

        // At midpoint 1,000ms: scale should be 1.5f, opacity 0.5f
        val midTransform = KeyframeInterpolator.interpolateTransform(kfs, defaultTransform, 1_000L)
        assertEquals(1.5f, midTransform.scaleX, 0.01f)
        assertEquals(1.5f, midTransform.scaleY, 0.01f)
        assertEquals(0.5f, midTransform.opacity, 0.01f)

        // Beyond end at 3,000ms: should clamp to last keyframe values
        val endTransform = KeyframeInterpolator.interpolateTransform(kfs, defaultTransform, 3_000L)
        assertEquals(2.0f, endTransform.scaleX, 0.01f)
        assertEquals(0.0f, endTransform.opacity, 0.01f)
    }

    @Test
    fun testVolumeKeyframeInterpolation() {
        val kfs = listOf(
            KeyframeModel(id = "v1", timestampMs = 0L, volume = 0.0f),
            KeyframeModel(id = "v2", timestampMs = 1_000L, volume = 1.0f)
        )
        val volAtHalf = KeyframeInterpolator.interpolateVolume(kfs, 1.0f, 500L)
        assertEquals(0.5f, volAtHalf, 0.01f)
    }

    @Test
    fun testTransitionsCalculation() {
        // Cross dissolve at 50% progress
        val dissolveState = TransitionEngine.calculateTransition(TransitionType.DISSOLVE, 0.5f)
        assertEquals(0.5f, dissolveState.outgoingAlpha, 0.01f)
        assertEquals(0.5f, dissolveState.incomingAlpha, 0.01f)

        // Slide left at 25% progress
        val slideState = TransitionEngine.calculateTransition(TransitionType.SLIDE_LEFT, 0.25f)
        assertEquals(-0.25f, slideState.outgoingTranslationX, 0.01f)
        assertEquals(0.75f, slideState.incomingTranslationX, 0.01f)
    }

    @Test
    fun testCompositionEngineMultiTrackEvaluation() {
        val videoClip = TimelineClipModel(
            id = "v_clip_1",
            timelineStartMs = 0L,
            durationMs = 5_000L,
            name = "Video 1"
        )
        val textClip = TimelineClipModel(
            id = "t_clip_1",
            timelineStartMs = 1_000L,
            durationMs = 3_000L,
            textContent = "বাংলা সাবটাইটেল"
        )

        val project = ProjectModel(
            durationMs = 5_000L,
            tracks = listOf(
                TimelineTrackModel(id = "t_video", type = TrackType.VIDEO, clips = listOf(videoClip)),
                TimelineTrackModel(id = "t_text", type = TrackType.TEXT, clips = listOf(textClip))
            )
        )

        // At 2,000ms: both video and text clip should be active
        val snapshotAt2s = CompositionEngine.evaluate(project, 2_000L)
        assertEquals(1, snapshotAt2s.activeVideoClips.size)
        assertEquals("Video 1", snapshotAt2s.activeVideoClips[0].clip.name)
        assertEquals(1, snapshotAt2s.activeTextClips.size)
        assertEquals("বাংলা সাবটাইটেল", snapshotAt2s.activeTextClips[0].clip.textContent)

        // At 4,500ms: video is active, text clip is not active (ended at 4,000ms)
        val snapshotAt4500 = CompositionEngine.evaluate(project, 4_500L)
        assertEquals(1, snapshotAt4500.activeVideoClips.size)
        assertEquals(0, snapshotAt4500.activeTextClips.size)
    }
}
