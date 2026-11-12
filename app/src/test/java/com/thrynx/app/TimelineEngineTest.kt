package com.thrynx.app

import com.thrynx.app.core.editor.timeline.TimelineEngine
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.model.TimelineClipModel
import com.thrynx.app.core.model.TimelineTrackModel
import com.thrynx.app.core.model.TrackType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimelineEngineTest {

    private lateinit var engine: TimelineEngine
    private lateinit var project: ProjectModel

    @Before
    fun setup() {
        val initialClip = TimelineClipModel(
            id = "clip_1",
            mediaUri = "file:///sdcard/video1.mp4",
            name = "Test Clip",
            sourceStartMs = 0L,
            sourceEndMs = 10_000L,
            timelineStartMs = 0L,
            durationMs = 10_000L
        )

        project = ProjectModel(
            id = "proj_test",
            name = "Unit Test Project",
            tracks = listOf(
                TimelineTrackModel(
                    id = "track_v1",
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(initialClip)
                )
            )
        )

        engine = TimelineEngine(project)
    }

    @Test
    fun testAddClipToTrack() {
        val newClip = TimelineClipModel(
            id = "clip_2",
            name = "Second Clip",
            durationMs = 5_000L
        )
        val updated = engine.addClipToTrack("track_v1", newClip)
        val track = updated.tracks.first { it.id == "track_v1" }

        assertEquals(2, track.clips.size)
        // Second clip should start at 10,000 ms (end of first clip)
        assertEquals(10_000L, track.clips[1].timelineStartMs)
        assertEquals(15_000L, updated.durationMs)
    }

    @Test
    fun testSplitClipAtPlayhead() {
        // Split at 4,000 ms
        val updated = engine.splitClipAtPlayhead("track_v1", "clip_1", 4_000L)
        val track = updated.tracks.first { it.id == "track_v1" }

        assertEquals(2, track.clips.size)
        assertEquals(4_000L, track.clips[0].durationMs)
        assertEquals(6_000L, track.clips[1].durationMs)
        assertEquals(4_000L, track.clips[1].timelineStartMs)
        assertEquals(10_000L, updated.durationMs)
    }

    @Test
    fun testTrimClip() {
        // Trim source start to 2,000 ms and source end to 7,000 ms (new duration 5,000 ms)
        val updated = engine.trimClip("track_v1", "clip_1", 2_000L, 7_000L)
        val track = updated.tracks.first { it.id == "track_v1" }

        val trimmed = track.clips.first()
        assertEquals(2_000L, trimmed.sourceStartMs)
        assertEquals(7_000L, trimmed.sourceEndMs)
        assertEquals(5_000L, trimmed.durationMs)
        assertEquals(5_000L, updated.durationMs)
    }

    @Test
    fun testDuplicateClip() {
        val updated = engine.duplicateClip("track_v1", "clip_1")
        val track = updated.tracks.first { it.id == "track_v1" }

        assertEquals(2, track.clips.size)
        assertEquals(20_000L, updated.durationMs)
    }

    @Test
    fun testDeleteClip() {
        val updated = engine.deleteClip("track_v1", "clip_1")
        val track = updated.tracks.first { it.id == "track_v1" }

        assertTrue(track.clips.isEmpty())
        assertEquals(0L, updated.durationMs)
    }

    @Test
    fun testUndoRedoStack() {
        assertTrue(!engine.canUndo())
        engine.deleteClip("track_v1", "clip_1")
        assertTrue(engine.canUndo())

        val restored = engine.undo()
        assertNotNull(restored)
        assertEquals(1, restored!!.tracks.first().clips.size)

        val redone = engine.redo()
        assertNotNull(redone)
        assertEquals(0, redone!!.tracks.first().clips.size)
    }

    @Test
    fun testMoveClipAndSnapping() {
        // Move clip to 1,500ms
        val moved = engine.moveClip("track_v1", "clip_1", 1_500L)
        val clip = moved.tracks.first().clips.first()
        assertEquals(1_500L, clip.timelineStartMs)
        assertEquals(11_500L, moved.durationMs)

        // Snapping: moving close to 0ms (e.g. 50ms <= snapTolerance 150ms) should snap to 0
        val snapped = engine.moveClip("track_v1", "clip_1", 50L)
        assertEquals(0L, snapped.tracks.first().clips.first().timelineStartMs)
    }

    @Test
    fun testMoveClipBetweenTracks() {
        val projectWithTwoTracks = project.copy(
            tracks = project.tracks + TimelineTrackModel(id = "track_v2", type = TrackType.VIDEO, index = 1)
        )
        val eng = TimelineEngine(projectWithTwoTracks)
        val result = eng.moveClipBetweenTracks("track_v1", "track_v2", "clip_1", 2000L)

        val track1 = result.tracks.first { it.id == "track_v1" }
        val track2 = result.tracks.first { it.id == "track_v2" }

        assertTrue(track1.clips.isEmpty())
        assertEquals(1, track2.clips.size)
        assertEquals(2000L, track2.clips.first().timelineStartMs)
    }

    @Test
    fun testTransformOperations() {
        // Rotate 90
        val rotated = engine.rotateClip90("track_v1", "clip_1")
        assertEquals(90f, rotated.tracks.first().clips.first().transform.rotation, 0.01f)

        // Flip Horizontal
        val flippedH = engine.flipClipHorizontal("track_v1", "clip_1")
        assertEquals(-1f, flippedH.tracks.first().clips.first().transform.scaleX, 0.01f)

        // Flip Vertical
        val flippedV = engine.flipClipVertical("track_v1", "clip_1")
        assertEquals(-1f, flippedV.tracks.first().clips.first().transform.scaleY, 0.01f)

        // Opacity
        val withOpacity = engine.updateClipOpacity("track_v1", "clip_1", 0.65f)
        assertEquals(0.65f, withOpacity.tracks.first().clips.first().transform.opacity, 0.01f)
    }

    @Test
    fun testAudioFadeAndMute() {
        val withFade = engine.updateAudioFade("track_v1", "clip_1", 1000L, 2000L)
        val clip = withFade.tracks.first().clips.first()
        assertEquals(1000L, clip.fadeInMs)
        assertEquals(2000L, clip.fadeOutMs)

        val muted = engine.toggleClipMute("track_v1", "clip_1")
        assertTrue(muted.tracks.first().clips.first().isMuted)
    }

    @Test
    fun testTextPropertiesUpdate() {
        val updatedText = engine.updateTextProperties(
            trackId = "track_v1",
            clipId = "clip_1",
            text = "বাংলা সাবটাইটেল",
            colorHex = "#00F0FF",
            bgHex = "#000000",
            fontSizeSp = 28f,
            isSubtitle = true
        )
        val clip = updatedText.tracks.first().clips.first()
        assertEquals("বাংলা সাবটাইটেল", clip.textContent)
        assertEquals("#00F0FF", clip.textColorHex)
        assertEquals(28f, clip.textFontSizeSp, 0.01f)
        assertTrue(clip.isSubtitle)
    }
}
