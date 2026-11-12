package com.thrynx.app.core.editor.export

import android.content.Context
import androidx.media3.transformer.*
import com.thrynx.app.core.database.ExportHistoryEntity
import com.thrynx.app.core.database.ThrynxDatabase
import com.thrynx.app.core.model.ProjectModel
import kotlinx.coroutines.*
import java.io.File

enum class ExportStage(val displayTitle: String) {
    PREPARING("Preparing project…"),
    VALIDATING("Validating media references & storage…"),
    COMPOSITING("Compiling multi-track composition…"),
    ENCODING("Encoding video frames & mixing audio…"),
    FINALIZING("Finalizing MP4 container…"),
    VERIFYING("Verifying file integrity & streams…"),
    COMPLETED("Export completed successfully!")
}

class VideoExportEngine(private val context: Context) {

    private var currentTransformer: Transformer? = null
    private var activeJob: Job? = null
    private var isCancelled = false
    private var currentOutputFile: File? = null

    suspend fun exportProject(
        project: ProjectModel,
        config: ExportConfiguration,
        outputFile: File,
        onStageChanged: (ExportStage) -> Unit = {},
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        isCancelled = false
        currentOutputFile = outputFile

        try {
            // Stage 1 & 2: Validating & Compiling
            onStageChanged(ExportStage.VALIDATING)
            onProgress(0)

            val plan = TimelineCompiler.compileAndValidate(
                context = context,
                project = project,
                config = config,
                outputFile = outputFile
            )

            if (isCancelled) throw ExportException.ExportCancelledException()

            onStageChanged(ExportStage.COMPOSITING)
            onProgress(5)

            // Stage 3: Encoding with Media3 Transformer
            onStageChanged(ExportStage.ENCODING)
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
                            exportException: androidx.media3.transformer.ExportException
                        ) {
                            completer.completeExceptionally(exportException)
                        }
                    })
                    .build()

                currentTransformer = transformer
                transformer.start(plan.composition, outputFile.absolutePath)
            }

            // Real progress polling loop
            val progressJob = launch {
                while (!completer.isCompleted && !isCancelled) {
                    delay(200)
                    withContext(Dispatchers.Main) {
                        currentTransformer?.let { trans ->
                            val progressState = trans.getProgress(progressHolder)
                            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                                // Scale encoding progress from 5% to 90%
                                val rawProgress = progressHolder.progress
                                val scaled = 5 + ((rawProgress / 100f) * 85f).toInt()
                                onProgress(scaled.coerceIn(5, 90))
                            }
                        }
                    }
                }
            }

            val resultFile = completer.await()
            progressJob.cancel()

            if (isCancelled) {
                outputFile.delete()
                throw ExportException.ExportCancelledException()
            }

            // Stage 4: Finalizing Output
            onStageChanged(ExportStage.FINALIZING)
            onProgress(92)
            delay(150)

            // Stage 5: Verifying Output
            onStageChanged(ExportStage.VERIFYING)
            onProgress(95)

            val verification = ExportVerifier.verifyExportedFile(
                file = resultFile,
                expectedAudio = config.isAudioEnabled
            )

            if (!verification.isValid) {
                resultFile.delete()
                throw ExportException.VerificationFailedException(verification.errorMessage ?: "Unknown error")
            }

            onStageChanged(ExportStage.COMPLETED)
            onProgress(100)

            // Save to Export History in Room
            try {
                val historyEntity = ExportHistoryEntity(
                    projectName = project.name,
                    filePath = resultFile.absolutePath,
                    resolution = "${verification.width}x${verification.height}",
                    fps = config.fps,
                    durationMs = verification.durationMs,
                    fileSizeBytes = verification.fileSizeBytes,
                    status = "COMPLETED"
                )
                ThrynxDatabase.getInstance(context).exportDao().insertExport(historyEntity)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success(resultFile)
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            Result.failure(e)
        } finally {
            currentTransformer = null
            currentOutputFile = null
        }
    }

    fun cancelExport() {
        isCancelled = true
        try {
            currentTransformer?.cancel()
            currentOutputFile?.let { file ->
                if (file.exists()) file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentTransformer = null
        activeJob?.cancel()
    }
}
