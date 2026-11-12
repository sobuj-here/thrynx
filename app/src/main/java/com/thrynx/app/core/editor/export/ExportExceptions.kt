package com.thrynx.app.core.editor.export

sealed class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingMediaException(val clipName: String, val mediaUri: String) :
        ExportException("Source media file for '$clipName' cannot be found or accessed ($mediaUri).")

    class InsufficientStorageException(val requiredBytes: Long, val availableBytes: Long) :
        ExportException("Insufficient device storage. Required: ${requiredBytes / (1024 * 1024)} MB, Available: ${availableBytes / (1024 * 1024)} MB.")

    class EmptyProjectException :
        ExportException("Cannot export an empty project without playable video or media clips.")

    class ExportCancelledException :
        ExportException("Video export operation was cancelled by user.")

    class VerificationFailedException(val reason: String) :
        ExportException("Exported video verification failed: $reason")

    class CodecUnsupportedException(val codec: String, val resolution: String) :
        ExportException("The device does not support hardware encoding for $codec at $resolution.")
}
