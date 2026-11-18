package com.music.thrynx.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class LocalVideo(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val path: String,
    val contentUri: Uri,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val folderName: String,
    val mimeType: String,
) {
    val formattedDuration: String
        get() = LocalVideoRepository.formatDuration(durationMs)

    val formattedSize: String
        get() = LocalVideoRepository.formatFileSize(sizeBytes)

    val resolutionLabel: String
        get() = LocalVideoRepository.formatResolution(width, height)
}

object LocalVideoRepository {

    fun hasVideoPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO,
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun queryVideos(context: Context): List<LocalVideo> = withContext(Dispatchers.IO) {
        if (!hasVideoPermission(context)) return@withContext emptyList()

        val videos = ArrayList<LocalVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = if (titleCol != -1) cursor.getString(titleCol) else null
                    val displayName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = rawTitle?.takeIf { it.isNotBlank() }
                        ?: displayName?.substringBeforeLast('.')
                        ?: "Video $id"

                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                    val path = if (dataCol != -1) cursor.getString(dataCol).orEmpty() else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol).orEmpty() else "video/*"

                    val folderName = try {
                        File(path).parentFile?.name ?: "Videos"
                    } catch (_: Exception) {
                        "Videos"
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    videos.add(
                        LocalVideo(
                            id = id,
                            title = title,
                            durationMs = duration,
                            path = path,
                            contentUri = uri,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            dateAdded = dateAdded,
                            folderName = folderName,
                            mimeType = mime,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            DebugLog.e("LocalVideoRepo", "Failed to query videos", e)
        }

        videos
    }

    suspend fun queryFolders(context: Context): Map<String, List<LocalVideo>> = withContext(Dispatchers.IO) {
        val all = queryVideos(context)
        all.groupBy { it.folderName }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = ms / 1000L
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return if (hr > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hr, min, sec)
        } else {
            String.format(Locale.US, "%d:%02d", min, sec)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatResolution(width: Int, height: Int): String {
        val h = maxOf(width, height)
        val v = minOf(width, height)
        return when {
            v >= 2160 || h >= 3840 -> "4K"
            v >= 1440 || h >= 2560 -> "2K"
            v >= 1080 || h >= 1920 -> "1080p"
            v >= 720 || h >= 1280 -> "720p"
            v >= 480 -> "480p"
            v > 0 -> "${v}p"
            else -> ""
        }
    }
}
