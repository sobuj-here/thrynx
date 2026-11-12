package com.thrynx.app.core.editor.export

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File

data class ExportVerificationResult(
    val isValid: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val hasAudio: Boolean,
    val fileSizeBytes: Long,
    val errorMessage: String? = null
)

object ExportVerifier {

    fun verifyExportedFile(
        file: File,
        expectedAudio: Boolean = true
    ): ExportVerificationResult {
        if (!file.exists()) {
            return ExportVerificationResult(
                isValid = false,
                durationMs = 0L,
                width = 0,
                height = 0,
                mimeType = "",
                hasAudio = false,
                fileSizeBytes = 0L,
                errorMessage = "Exported file does not exist on disk."
            )
        }

        val size = file.length()
        if (size <= 4096L) {
            return ExportVerificationResult(
                isValid = false,
                durationMs = 0L,
                width = 0,
                height = 0,
                mimeType = "",
                hasAudio = false,
                fileSizeBytes = size,
                errorMessage = "Exported file is empty or corrupted (size: $size bytes)."
            )
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val width = widthStr?.toIntOrNull() ?: 0

            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val height = heightStr?.toIntOrNull() ?: 0

            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"
            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasAudio = hasAudioStr?.equals("yes", ignoreCase = true) == true

            if (durationMs <= 0L) {
                return ExportVerificationResult(
                    isValid = false,
                    durationMs = 0L,
                    width = width,
                    height = height,
                    mimeType = mime,
                    hasAudio = hasAudio,
                    fileSizeBytes = size,
                    errorMessage = "Video stream duration is 0 or unreadable."
                )
            }

            if (width <= 0 || height <= 0) {
                return ExportVerificationResult(
                    isValid = false,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    mimeType = mime,
                    hasAudio = hasAudio,
                    fileSizeBytes = size,
                    errorMessage = "Video stream dimensions are invalid ($width x $height)."
                )
            }

            ExportVerificationResult(
                isValid = true,
                durationMs = durationMs,
                width = width,
                height = height,
                mimeType = mime,
                hasAudio = hasAudio,
                fileSizeBytes = size
            )
        } catch (e: Exception) {
            ExportVerificationResult(
                isValid = false,
                durationMs = 0L,
                width = 0,
                height = 0,
                mimeType = "",
                hasAudio = false,
                fileSizeBytes = size,
                errorMessage = "Failed to parse MP4 container: ${e.message}"
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
