package com.thrynx.app.core.media

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    /**
     * Discovers all local video files on device via MediaStore.
     * Audio and image files are completely excluded.
     */
    suspend fun loadVideos(hiddenFolderPaths: Set<String> = emptySet()): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<MediaItemModel>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video_$id"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "video/mp4"
                    val path = cursor.getString(dataColumn) ?: ""
                    val dateAdded = cursor.getLong(dateColumn)
                    val width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0
                    val height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    // Determine folder and check if hidden
                    val fileObj = File(path)
                    val folderName = try {
                        fileObj.parentFile?.name ?: "Internal"
                    } catch (e: Exception) {
                        "Videos"
                    }
                    val parentPath = fileObj.parent ?: ""

                    // If folder is in private/hidden set, skip from standard library
                    if (hiddenFolderPaths.contains(parentPath) || hiddenFolderPaths.contains(folderName)) {
                        continue
                    }

                    videos.add(
                        MediaItemModel(
                            id = id,
                            title = name,
                            uri = contentUri,
                            path = path,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            folderName = folderName,
                            isVideo = true,
                            width = width,
                            height = height
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videos
    }

    /**
     * Groups videos into organized folder models.
     */
    fun groupIntoFolders(videos: List<MediaItemModel>): List<FolderItemModel> {
        return videos
            .groupBy { it.folderName }
            .map { (folderName, items) ->
                val representativePath = items.firstOrNull()?.let {
                    try { File(it.path).parent ?: folderName } catch (e: Exception) { folderName }
                } ?: folderName
                FolderItemModel(
                    name = folderName,
                    path = representativePath,
                    videoCount = items.size,
                    latestMedia = items.firstOrNull(),
                    isPrivate = false
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Resolves metadata from a Storage Access Framework (SAF) Uri.
     */
    suspend fun resolveMetadataFromUri(uri: Uri): MediaItemModel? = withContext(Dispatchers.IO) {
        try {
            var displayName = "Imported Video"
            var sizeBytes = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }

            var duration = 0L
            var width = 0
            var height = 0
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                // Non-fatal metadata read error
            } finally {
                try { retriever.release() } catch (ignored: Exception) {}
            }

            MediaItemModel(
                id = System.currentTimeMillis(),
                title = displayName,
                uri = uri,
                path = uri.toString(),
                durationMs = duration,
                sizeBytes = sizeBytes,
                mimeType = context.contentResolver.getType(uri) ?: "video/mp4",
                dateAdded = System.currentTimeMillis() / 1000,
                folderName = "SAF Imports",
                isVideo = true,
                width = width,
                height = height
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a video file from disk or MediaStore.
     */
    suspend fun deleteVideo(media: MediaItemModel): Boolean = withContext(Dispatchers.IO) {
        try {
            if (media.path.isNotBlank()) {
                val file = File(media.path)
                if (file.exists() && file.delete()) {
                    return@withContext true
                }
            }
            val targetUri = media.uri ?: if (media.mediaUriString.isNotBlank()) Uri.parse(media.mediaUriString) else null
            if (targetUri != null) {
                val deleted = context.contentResolver.delete(targetUri, null, null)
                deleted > 0
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Renames a video file on disk.
     */
    suspend fun renameVideo(media: MediaItemModel, newName: String): MediaItemModel? = withContext(Dispatchers.IO) {
        try {
            if (media.path.isNotBlank()) {
                val oldFile = File(media.path)
                if (oldFile.exists()) {
                    val extension = oldFile.extension.ifBlank { "mp4" }
                    val sanitizedName = if (newName.endsWith(".$extension", ignoreCase = true)) newName else "$newName.$extension"
                    val newFile = File(oldFile.parentFile, sanitizedName)
                    if (oldFile.renameTo(newFile)) {
                        return@withContext media.copy(
                            title = sanitizedName,
                            path = newFile.absolutePath,
                            uri = Uri.fromFile(newFile)
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a secure sharing Intent for a video via FileProvider.
     */
    fun shareVideo(media: MediaItemModel): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType.ifBlank { "video/*" }
            val shareUri = try {
                if (media.path.isNotBlank() && File(media.path).exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(media.path))
                } else {
                    media.uri
                }
            } catch (e: Exception) {
                media.uri
            }
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TITLE, media.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Video")
    }
}
