package com.thrynx.app

import com.thrynx.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class VideoPlayerStateTest {

    @Test
    fun testAudioTrackLanguageDisplay() {
        val engTrack = AudioTrackInfo(
            id = "audio_1",
            groupIndex = 0,
            trackIndex = 0,
            language = "eng",
            label = "Surround 5.1",
            mimeType = "audio/ac4",
            channelCount = 6,
            sampleRate = 48000
        )
        assertEquals("English", engTrack.displayLanguage)

        val bngTrack = AudioTrackInfo(
            id = "audio_2",
            groupIndex = 0,
            trackIndex = 1,
            language = "ben",
            label = "Stereo",
            mimeType = "audio/mp4a-latm",
            channelCount = 2,
            sampleRate = 44100
        )
        assertEquals("বাংলা (Bangla)", bngTrack.displayLanguage)

        val undTrack = AudioTrackInfo(
            id = "audio_3",
            groupIndex = 0,
            trackIndex = 2,
            language = "und",
            label = "Director Commentary",
            mimeType = "audio/mp4a-latm",
            channelCount = 2,
            sampleRate = 44100
        )
        assertEquals("Director Commentary", undTrack.displayLanguage)
    }

    @Test
    fun testSubtitleTrackLanguageDisplay() {
        val subTrack = SubtitleTrackInfo(
            id = "sub_1",
            groupIndex = 1,
            trackIndex = 0,
            language = "spa",
            label = "Spanish SDH",
            mimeType = "application/x-subrip"
        )
        assertEquals("Spanish", subTrack.displayLanguage)
    }

    @Test
    fun testPlaybackStateQueueNavigation() {
        val v1 = MediaItemModel(id = 1L, title = "Episode 1")
        val v2 = MediaItemModel(id = 2L, title = "Episode 2")
        val v3 = MediaItemModel(id = 3L, title = "Episode 3")
        val queue = listOf(v1, v2, v3)

        val stateStart = PlaybackState(queue = queue, queueIndex = 0, currentMedia = v1)
        assertFalse(stateStart.hasPrevious)
        assertTrue(stateStart.hasNext)

        val stateMiddle = PlaybackState(queue = queue, queueIndex = 1, currentMedia = v2)
        assertTrue(stateMiddle.hasPrevious)
        assertTrue(stateMiddle.hasNext)

        val stateEnd = PlaybackState(queue = queue, queueIndex = 2, currentMedia = v3)
        assertTrue(stateEnd.hasPrevious)
        assertFalse(stateEnd.hasNext)
    }

    @Test
    fun testResolutionBadgeDetermination() {
        val uhdVideo = MediaItemModel(width = 3840, height = 2160)
        assertEquals("4K UHD", uhdVideo.resolutionLabel)

        val fhdVideo = MediaItemModel(width = 1920, height = 1080)
        assertEquals("1080p FHD", fhdVideo.resolutionLabel)

        val hdVideo = MediaItemModel(width = 1280, height = 720)
        assertEquals("720p HD", hdVideo.resolutionLabel)

        val sdVideo = MediaItemModel(width = 640, height = 480)
        assertEquals("640x480", sdVideo.resolutionLabel)
    }

    @Test
    fun testRepeatModeTransitions() {
        var mode = RepeatMode.OFF
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ALL, mode)

        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.ONE, mode)

        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test
    fun testZoomScaleClamping() {
        val minScale = 0.5f.coerceIn(1.0f, 4.0f)
        assertEquals(1.0f, minScale, 0.001f)

        val maxScale = 5.5f.coerceIn(1.0f, 4.0f)
        assertEquals(4.0f, maxScale, 0.001f)

        val validScale = 2.5f.coerceIn(1.0f, 4.0f)
        assertEquals(2.5f, validScale, 0.001f)
    }

    @Test
    fun testPlaybackStateBufferingAndError() {
        val normalState = PlaybackState()
        assertFalse(normalState.isBuffering)
        assertNull(normalState.errorMessage)

        val bufferingState = PlaybackState(isBuffering = true)
        assertTrue(bufferingState.isBuffering)

        val errorState = PlaybackState(errorMessage = "Network connection failed")
        assertEquals("Network connection failed", errorState.errorMessage)
    }

    @Test
    fun testAudioVsVideoMediaItemDistinction() {
        val video = MediaItemModel(id = 100L, title = "Movie.mp4", isVideo = true, durationMs = 120_000L)
        val audio = MediaItemModel(id = 200L, title = "Song.mp3", isVideo = false, durationMs = 180_000L)

        assertTrue(video.isVideo)
        assertFalse(audio.isVideo)
        assertNotEquals(video.id, audio.id)
    }

    @Test
    fun testAutoResumePositionThreshold() {
        val freshMedia = MediaItemModel(lastPositionMs = 500L)
        val shouldResumeFresh = freshMedia.lastPositionMs > 2000L
        assertFalse(shouldResumeFresh)

        val playedMedia = MediaItemModel(lastPositionMs = 45_000L)
        val shouldResumePlayed = playedMedia.lastPositionMs > 2000L
        assertTrue(shouldResumePlayed)
    }
}
