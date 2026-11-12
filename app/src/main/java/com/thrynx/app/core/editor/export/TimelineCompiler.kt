package com.thrynx.app.core.editor.export

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.model.TrackType
import java.io.File

data class ExportPlan(
    val composition: Composition,
    val totalDurationMs: Long,
    val clipCount: Int,
    val estimatedSizeBytes: Long
)

object TimelineCompiler {

    fun compileAndValidate(
        context: Context,
        project: ProjectModel,
        config: ExportConfiguration,
        outputFile: File
    ): ExportPlan {
        // 1. Check for empty project
        val mainVideoTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO }
        val videoClips = mainVideoTrack?.clips?.filter { it.mediaUri.isNotBlank() } ?: emptyList()
        val imageTrack = project.tracks.firstOrNull { it.type == TrackType.IMAGE }
        val imageClips = imageTrack?.clips?.filter { it.mediaUri.isNotBlank() } ?: emptyList()

        if (videoClips.isEmpty() && imageClips.isEmpty()) {
            throw ExportException.EmptyProjectException()
        }

        // 2. Validate media references exist and are accessible
        val allClipsWithMedia = project.tracks.flatMap { it.clips }.filter { it.mediaUri.isNotBlank() }
        for (clip in allClipsWithMedia) {
            val uri = Uri.parse(clip.mediaUri)
            val scheme = uri.scheme
            if (scheme == "file" || scheme == null) {
                val file = File(uri.path ?: clip.mediaUri)
                if (!file.exists()) {
                    throw ExportException.MissingMediaException(clip.name, clip.mediaUri)
                }
            } else if (scheme == "content") {
                try {
                    context.contentResolver.openInputStream(uri)?.close()
                        ?: throw ExportException.MissingMediaException(clip.name, clip.mediaUri)
                } catch (e: Exception) {
                    throw ExportException.MissingMediaException(clip.name, clip.mediaUri)
                }
            }
        }

        // 3. Storage Preflight Check
        val totalDurationMs = project.durationMs.coerceAtLeast(1000L)
        val estimatedSizeBytes = config.calculateEstimatedSizeBytes(totalDurationMs)
        val parentDir = outputFile.parentFile ?: context.cacheDir
        val freeSpaceBytes = parentDir.freeSpace
        val requiredSafetySpace = (estimatedSizeBytes * 1.5).toLong().coerceAtLeast(50 * 1024 * 1024L)

        if (freeSpaceBytes < requiredSafetySpace) {
            throw ExportException.InsufficientStorageException(requiredSafetySpace, freeSpaceBytes)
        }

        // 4. Build Sequences for Media3 Composition
        val sequences = mutableListOf<EditedMediaItemSequence>()

        // Video Sequence
        val editedVideoItems = mutableListOf<EditedMediaItem>()
        for (clip in videoClips) {
            val uri = Uri.parse(clip.mediaUri)
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs)
                        .setEndPositionMs(clip.sourceEndMs)
                        .build()
                )
                .build()

            val shouldMute = !config.isAudioEnabled || mainVideoTrack?.isMuted == true || clip.isMuted || clip.volume == 0f

            val editedItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(shouldMute)
                .build()

            editedVideoItems.add(editedItem)
        }

        if (editedVideoItems.isNotEmpty()) {
            sequences.add(EditedMediaItemSequence(editedVideoItems))
        }

        // Secondary Audio Sequences (BGM, Voice-Over, Extracted Audio)
        if (config.isAudioEnabled) {
            val audioTracks = project.tracks.filter { it.type == TrackType.AUDIO && !it.isMuted }
            for (aTrack in audioTracks) {
                val editedAudioItems = mutableListOf<EditedMediaItem>()
                for (aClip in aTrack.clips) {
                    if (aClip.mediaUri.isBlank() || aClip.isMuted || aClip.volume == 0f) continue
                    val aUri = Uri.parse(aClip.mediaUri)
                    val aMediaItem = MediaItem.Builder()
                        .setUri(aUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(aClip.sourceStartMs)
                                .setEndPositionMs(aClip.sourceEndMs)
                                .build()
                        )
                        .build()

                    val editedAItem = EditedMediaItem.Builder(aMediaItem)
                        .setRemoveAudio(false)
                        .build()

                    editedAudioItems.add(editedAItem)
                }

                if (editedAudioItems.isNotEmpty()) {
                    sequences.add(EditedMediaItemSequence(editedAudioItems))
                }
            }
        }

        val composition = Composition.Builder(sequences).build()

        return ExportPlan(
            composition = composition,
            totalDurationMs = totalDurationMs,
            clipCount = editedVideoItems.size,
            estimatedSizeBytes = estimatedSizeBytes
        )
    }
}
