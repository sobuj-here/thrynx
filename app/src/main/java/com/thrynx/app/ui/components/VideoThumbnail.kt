package com.thrynx.app.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thrynx.app.core.designsystem.ThrynxCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of available memory

    val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }
}

@Composable
fun VideoThumbnail(
    uri: Uri?,
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val cacheKey = uri?.toString() ?: path ?: ""

    var bitmap by remember(cacheKey) {
        mutableStateOf(ThumbnailCache.memoryCache.get(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        if (bitmap == null && cacheKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                var loaded: Bitmap? = null

                // 1. Try Android Q+ contentResolver.loadThumbnail
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                    try {
                        loaded = context.contentResolver.loadThumbnail(uri, Size(320, 240), null)
                    } catch (ignored: Exception) {}
                }

                // 2. Try ThumbnailUtils for local file path
                if (loaded == null && !path.isNullOrBlank()) {
                    try {
                        @Suppress("DEPRECATION")
                        loaded = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
                    } catch (ignored: Exception) {}
                }

                // 3. Fallback to MediaMetadataRetriever
                if (loaded == null && uri != null) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        loaded = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(0)
                        retriever.release()
                    } catch (ignored: Exception) {}
                }

                if (loaded != null) {
                    ThumbnailCache.memoryCache.put(cacheKey, loaded)
                    bitmap = loaded
                }
            }
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF141C2B)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = ThrynxCyan.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
