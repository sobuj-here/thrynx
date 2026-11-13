package com.thrynx.app.core.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.thrynx.app.core.database.CustomFolderEntity
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    companion object {
        private val SUPPORTED_VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv", "m4v", "wmv"
        )

        fun hasStoragePermission(context: Context): Boolean {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    // Android 14+
                    val full = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    val partial = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                    full || partial
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    // Android 13
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                }
                else -> {
                    // Android 8-12
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
        }

        fun hasAudioPermission(context: Context): Boolean {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                }
                else -> {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
    }

    /**
     * Discovers all local video files on device via MediaStore and custom SAF folders.
     * Guaranteed safe column extraction without throwing on any Android OEM or version.
     */
    suspend fun loadVideos(
        customFolders: List<CustomFolderEntity> = emptyList(),
        hiddenFolderPaths: Set<String> = emptySet()
    ): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<MediaItemModel>()
        val seenUris = mutableSetOf<String>()

        // 1. Scan MediaStore Video Media
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED
        )

        // Add optional columns where supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            projectionList.add(MediaStore.Video.Media.WIDTH)
            projectionList.add(MediaStore.Video.Media.HEIGHT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        projectionList.add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projectionList.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else System.currentTimeMillis()
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else System.currentTimeMillis() / 1000
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0

                    val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                    // Determine folder name reliably
                    val folderName = when {
                        !bucketName.isNullOrBlank() -> bucketName
                        !relPath.isNullOrBlank() -> {
                            relPath.trim('/').split('/').lastOrNull()?.ifBlank { null } ?: "Videos"
                        }
                        path.isNotBlank() -> {
                            try { File(path).parentFile?.name ?: "Internal" } catch (e: Exception) { "Videos" }
                        }
                        else -> "Videos"
                    }

                    // Check if folder is excluded/hidden in vault
                    val parentPath = try { if (path.isNotBlank()) File(path).parent ?: "" else "" } catch (e: Exception) { "" }
                    if (hiddenFolderPaths.contains(parentPath) || hiddenFolderPaths.contains(folderName)) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val uriKey = contentUri.toString()

                    if (seenUris.add(uriKey)) {
                        videos.add(
                            MediaItemModel(
                                id = id,
                                title = name,
                                uri = contentUri,
                                path = path.ifBlank { uriKey },
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan Custom SAF Folders Persisted by User
        for (customFolder in customFolders) {
            try {
                val folderUri = Uri.parse(customFolder.folderUri)
                val safVideos = scanSafDirectory(folderUri, customFolder.folderName, hiddenFolderPaths)
                for (v in safVideos) {
                    if (seenUris.add(v.mediaUriString)) {
                        videos.add(v)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        videos
    }

    /**
     * Traverses a Storage Access Framework (SAF) tree directory to discover video files.
     */
    private fun scanSafDirectory(
        treeUri: Uri,
        folderName: String,
        hiddenFolderPaths: Set<String>
    ): List<MediaItemModel> {
        val results = mutableListOf<MediaItemModel>()
        val docFolder = DocumentFile.fromTreeUri(context, treeUri) ?: return results
        if (!docFolder.exists() || !docFolder.isDirectory) return results

        if (hiddenFolderPaths.contains(folderName) || hiddenFolderPaths.contains(treeUri.toString())) {
            return results
        }

        val files = docFolder.listFiles()
        for (file in files) {
            if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val isVideoExt = SUPPORTED_VIDEO_EXTENSIONS.contains(ext)
                val isVideoMime = file.type?.startsWith("video/") == true

                if (isVideoExt || isVideoMime) {
                    val size = file.length().coerceAtLeast(0L)
                    val lastModified = file.lastModified() / 1000

                    // Quick duration check via metadata retriever
                    var durationMs = 0L
                    var width = 0
                    var height = 0
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, file.uri)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    } catch (e: Exception) {
                        // Safe fallback
                    } finally {
                        try { retriever.release() } catch (ignored: Exception) {}
                    }

                    results.add(
                        MediaItemModel(
                            id = file.uri.hashCode().toLong(),
                            title = name,
                            uri = file.uri,
                            path = file.uri.toString(),
                            durationMs = durationMs,
                            sizeBytes = size,
                            mimeType = file.type ?: "video/$ext",
                            dateAdded = lastModified,
                            folderName = folderName,
                            isVideo = true,
                            width = width,
                            height = height
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Groups videos into organized folder models with video counts and representative media.
     */
    fun groupIntoFolders(videos: List<MediaItemModel>): List<FolderItemModel> {
        return videos
            .groupBy { it.folderName }
            .map { (folderName, items) ->
                val representativePath = items.firstOrNull()?.let {
                    try {
                        if (it.path.startsWith("content://")) {
                            it.folderName
                        } else {
                            File(it.path).parent ?: folderName
                        }
                    } catch (e: Exception) {
                        folderName
                    }
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
     * Resolves metadata from a single Storage Access Framework (SAF) Uri.
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
                folderName = "Imports",
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
            if (media.path.isNotBlank() && !media.path.startsWith("content://")) {
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
            if (media.path.isNotBlank() && !media.path.startsWith("content://")) {
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
     * Discovers all local audio/music files on device via MediaStore.
     */
    suspend fun loadAudios(): List<MediaItemModel> = withContext(Dispatchers.IO) {
        val audios = mutableListOf<MediaItemModel>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.DURATION} >= ?",
                arrayOf("5000"), // Filter out audio clips shorter than 5 seconds
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durationCol = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateCol = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = if (idCol >= 0) c.getLong(idCol) else 0L
                    val uri = ContentUris.withAppendedId(collection, id)
                    val rawName = if (nameCol >= 0) c.getString(nameCol) else ""
                    val title = if (titleCol >= 0) c.getString(titleCol) ?: rawName else rawName
                    val artist = if (artistCol >= 0) c.getString(artistCol) ?: "<unknown>" else "<unknown>"
                    val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    val duration = if (durationCol >= 0) c.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                    val dateAdded = if (dateCol >= 0) c.getLong(dateCol) * 1000L else System.currentTimeMillis()

                    val cleanTitle = if (title.isBlank()) {
                        if (rawName.isNotBlank()) rawName.substringBeforeLast(".") else "Audio $id"
                    } else title

                    audios.add(
                        MediaItemModel(
                            id = id,
                            title = cleanTitle,
                            uri = uri,
                            path = path,
                            durationMs = duration,
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            mimeType = "audio/*",
                            isVideo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audios
    }

    /**
     * Groups audio files into folder models.
     */
    fun loadAudioFolders(audios: List<MediaItemModel>): List<FolderItemModel> {
        val folderMap = mutableMapOf<String, MutableList<MediaItemModel>>()
        for (audio in audios) {
            val folderPath = if (audio.path.isNotBlank()) {
                File(audio.path).parent ?: "Internal Storage"
            } else {
                "Music"
            }
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(audio)
        }

        return folderMap.map { (path, items) ->
            val name = File(path).name.ifBlank { "Music" }
            FolderItemModel(
                name = name,
                path = path,
                videoCount = items.size,
                latestMedia = items.firstOrNull()
            )
        }.sortedByDescending { it.videoCount }
    }

    /**
     * Creates a secure sharing Intent for a video via FileProvider.
     */
    fun shareVideo(media: MediaItemModel): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType.ifBlank { "video/*" }
            val shareUri = try {
                if (media.path.isNotBlank() && !media.path.startsWith("content://") && File(media.path).exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(media.path))
                } else {
                    media.uri ?: if (media.mediaUriString.isNotBlank()) Uri.parse(media.mediaUriString) else null
                }
            } catch (e: Exception) {
                media.uri ?: if (media.mediaUriString.isNotBlank()) Uri.parse(media.mediaUriString) else null
            }
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TITLE, media.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share Video")
    }
}
