package com.thrynx.app.core.player

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Handles downloading YouTube Music audio streams to local storage for offline playback.
 */
object AudioDownloadManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds = _downloadingIds.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds = _downloadedIds.asStateFlow()

    suspend fun downloadTrack(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (_downloadingIds.value.contains(videoId)) return@withContext

        _downloadingIds.value = _downloadingIds.value + videoId

        try {
            val resolvedResult = YouTubeAudioResolver.resolveAudio(videoId)
            val stream = resolvedResult.getOrElse { err ->
                withContext(Dispatchers.Main) {
                    _downloadingIds.value = _downloadingIds.value - videoId
                    onError(err.message ?: "Failed to resolve stream")
                }
                return@withContext
            }

            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "audio_$videoId" }
            val requestBuilder = Request.Builder().url(stream.streamUrl)
            stream.headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    _downloadingIds.value = _downloadingIds.value - videoId
                    onError("Download failed with HTTP ${response.code}")
                }
                return@withContext
            }

            val body = response.body
            if (body == null) {
                withContext(Dispatchers.Main) {
                    _downloadingIds.value = _downloadingIds.value - videoId
                    onError("Empty response body")
                }
                return@withContext
            }

            // Write to public Music/THRYNX folder or app external files
            var targetPath: String? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle.m4a")
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/THRYNX")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    targetPath = uri.toString()
                }
            }

            if (targetPath == null) {
                // Direct file system fallback
                val musicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "THRYNX"
                )
                if (!musicDir.exists()) musicDir.mkdirs()

                val file = File(musicDir, "$safeTitle.m4a")
                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )
                targetPath = file.absolutePath
            }

            withContext(Dispatchers.Main) {
                _downloadingIds.value = _downloadingIds.value - videoId
                _downloadedIds.value = _downloadedIds.value + videoId
                onSuccess(title)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _downloadingIds.value = _downloadingIds.value - videoId
                onError(e.localizedMessage ?: "Download error")
            }
        }
    }
}
