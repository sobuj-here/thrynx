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
}
