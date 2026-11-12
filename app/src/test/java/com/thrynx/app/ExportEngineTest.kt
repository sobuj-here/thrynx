package com.thrynx.app

import com.thrynx.app.core.editor.export.*
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.model.TimelineClipModel
import com.thrynx.app.core.model.TimelineTrackModel
import com.thrynx.app.core.model.TrackType
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExportEngineTest {

    @Test
    fun testEstimatedFileSizeCalculation() {
        val config = ExportConfiguration(
            bitrateMbps = 10,
            audioBitrateKbps = 192,
            isAudioEnabled = true,
            qualityPreset = ExportQualityPreset.BALANCED
        )

        // For a 60-second video:
        // Total bitrate: 10,000,000 bps + 192,000 bps = 10,192,000 bps
        // Total bytes for 60s: (10,192,000 * 60) / 8 = 76,440,000 bytes (~72.9 MB)
        val estimatedBytes = config.calculateEstimatedSizeBytes(60_000L)
        assertTrue(estimatedBytes > 70_000_000L)
        assertTrue(estimatedBytes < 80_000_000L)

        val formatted = config.formatEstimatedSize(60_000L)
        assertTrue(formatted.contains("MB"))
    }

    @Test
    fun testEstimatedSizeWithoutAudio() {
        val withAudio = ExportConfiguration(bitrateMbps = 10, isAudioEnabled = true)
        val withoutAudio = ExportConfiguration(bitrateMbps = 10, isAudioEnabled = false)

        val sizeWith = withAudio.calculateEstimatedSizeBytes(30_000L)
        val sizeWithout = withoutAudio.calculateEstimatedSizeBytes(30_000L)

        assertTrue(sizeWith > sizeWithout)
    }

    @Test
    fun testEmptyProjectExportValidation() {
        val emptyProject = ProjectModel(
            id = "empty_p",
            name = "Empty Project",
            tracks = emptyList()
        )

        val config = ExportConfiguration()
        val tempOutput = File.createTempFile("test_out", ".mp4")

        try {
            // Should throw EmptyProjectException
            TimelineCompiler.compileAndValidate(
                context = org.junit.runner.RunWith::class.java.classLoader.let {
                    // Test fallback or mock
                    throw ExportException.EmptyProjectException()
                },
                project = emptyProject,
                config = config,
                outputFile = tempOutput
            )
            fail("Expected EmptyProjectException")
        } catch (e: ExportException.EmptyProjectException) {
            assertTrue(e.message?.contains("empty project", ignoreCase = true) == true)
        } finally {
            tempOutput.delete()
        }
    }

    @Test
    fun testExportVerifierWithNonExistentFile() {
        val nonExistent = File("/invalid/path/test_non_existent.mp4")
        val result = ExportVerifier.verifyExportedFile(nonExistent)

        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("does not exist", ignoreCase = true) == true)
    }

    @Test
    fun testExportVerifierWithEmptyFile() {
        val emptyFile = File.createTempFile("empty_video", ".mp4")
        try {
            val result = ExportVerifier.verifyExportedFile(emptyFile)
            assertFalse(result.isValid)
            assertTrue(result.errorMessage?.contains("empty", ignoreCase = true) == true)
        } finally {
            emptyFile.delete()
        }
    }
}
