package com.thrynx.app.core.editor.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.model.TrackType
import kotlinx.coroutines.*
import java.io.File

data class ExportConfiguration(
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val fps: Int = 30,
    val bitrateMbps: Int = 10,
    val videoCodec: String = MimeTypes.VIDEO_H264,
    val audioCodec: String = MimeTypes.AUDIO_AAC
)

class VideoExportEngine(private val context: Context) {

    private var currentTransformer: Transformer? = null
    private var isCancelled = false

    suspend fun exportProject(
        project: ProjectModel,
        config: ExportConfiguration,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        isCancelled = false
        val mainTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO }
        val clips = mainTrack?.clips ?: emptyList()

        if (clips.isEmpty()) {
            return@withContext Result.failure(Exception("Cannot export an empty project without video clips."))
        }

        try {
            val editedMediaItems = mutableListOf<EditedMediaItem>()

            for (clip in clips) {
                if (clip.mediaUri.isBlank()) continue
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

                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(mainTrack?.isMuted == true)
                    .build()

                editedMediaItems.add(editedItem)
            }

            if (editedMediaItems.isEmpty()) {
                return@withContext Result.failure(Exception("No valid media clips found for export."))
            }

            val composition = Composition.Builder(
                EditedMediaItemSequence(editedMediaItems)
            ).build()

            val progressHolder = ProgressHolder()
            val completer = CompletableDeferred<File>()

            withContext(Dispatchers.Main) {
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(config.videoCodec)
                    .setAudioMimeType(config.audioCodec)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            completer.complete(outputFile)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            completer.completeExceptionally(exportException)
                        }
                    })
                    .build()

                currentTransformer = transformer
                transformer.start(composition, outputFile.absolutePath)
            }

            // Polling progress loop
            val progressJob = launch {
                while (!completer.isCompleted && !isCancelled) {
                    delay(250)
                    withContext(Dispatchers.Main) {
                        currentTransformer?.let { trans ->
                            val progressState = trans.getProgress(progressHolder)
                            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(progressHolder.progress)
                            }
                        }
                    }
                }
            }

            val resultFile = completer.await()
            progressJob.cancel()
            onProgress(100)
            Result.success(resultFile)
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            Result.failure(e)
        }
    }

    fun cancelExport() {
        isCancelled = true
        Handler(Looper.getMainLooper()).post {
            currentTransformer?.cancel()
            currentTransformer = null
        }
    }
}
