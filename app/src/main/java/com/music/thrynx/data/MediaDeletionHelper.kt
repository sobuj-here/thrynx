package com.music.thrynx.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MediaDeletionHelper {

    sealed interface DeleteResult {
        data object Success : DeleteResult
        data class RequiresConsent(val intentSender: IntentSender) : DeleteResult
        data class Error(val message: String) : DeleteResult
    }

    fun deleteMediaUris(
        context: Context,
        uris: List<Uri>,
        paths: List<String> = emptyList(),
    ): DeleteResult {
        if (uris.isEmpty() && paths.isEmpty()) return DeleteResult.Success

        // For Android 11+ (API 30+), MediaStore.createDeleteRequest handles scoped storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            return try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                DeleteResult.RequiresConsent(pendingIntent.intentSender)
            } catch (_: Exception) {
                directDelete(context, uris, paths)
            }
        }

        // For Android 10 (API 29), direct delete or catch RecoverableSecurityException
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            for (uri in uris) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    if (e is android.app.RecoverableSecurityException) {
                        return DeleteResult.RequiresConsent(e.userAction.actionIntent.intentSender)
                    }
                }
            }
            directDelete(context, emptyList(), paths)
            return DeleteResult.Success
        }

        // Android 9 and below
        return directDelete(context, uris, paths)
    }

    fun directDelete(
        context: Context,
        uris: List<Uri>,
        paths: List<String> = emptyList(),
    ): DeleteResult {
        for (uri in uris) {
            try {
                context.contentResolver.delete(uri, null, null)
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).delete() }
                }
            } catch (_: Exception) {}
        }
        for (path in paths) {
            try {
                if (path.isNotBlank()) {
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            } catch (_: Exception) {}
        }
        return DeleteResult.Success
    }
}
