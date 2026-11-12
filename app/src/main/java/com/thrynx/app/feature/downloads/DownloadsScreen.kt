package com.thrynx.app.feature.downloads

import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thrynx.app.R
import com.thrynx.app.core.database.DownloadEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.DownloadState
import com.thrynx.app.core.model.MediaItemModel
import java.io.File

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onPlayCompletedMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val stat = remember {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val usedBytes = totalBytes - freeBytes
        Pair(formatBytes(usedBytes), formatBytes(freeBytes))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(title = stringResource(id = R.string.downloads_title))

        // Storage Usage Card
        ThrynxCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ThrynxCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.downloads_storage_free, stat.second),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.downloads_storage_used, stat.first),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Downloads List
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.downloads_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { onPauseDownload(item.id) },
                        onResume = { onResumeDownload(item.id) },
                        onCancel = { onCancelDownload(item.id) },
                        onPlay = {
                            val file = File(item.destinationPath)
                            onPlayCompletedMedia(
                                MediaItemModel(
                                    title = item.title,
                                    uri = Uri.fromFile(file),
                                    path = item.destinationPath,
                                    isVideo = true
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onPlay: () -> Unit
) {
    ThrynxCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (item.state) {
                                DownloadState.COMPLETED -> ThrynxEmerald.copy(alpha = 0.2f)
                                DownloadState.RUNNING -> ThrynxCyan.copy(alpha = 0.2f)
                                DownloadState.PAUSED -> ThrynxAmber.copy(alpha = 0.2f)
                                else -> ThrynxCoral.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.state) {
                            DownloadState.COMPLETED -> Icons.Default.Check
                            DownloadState.RUNNING -> Icons.Default.Downloading
                            DownloadState.PAUSED -> Icons.Default.Pause
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        tint = when (item.state) {
                            DownloadState.COMPLETED -> ThrynxEmerald
                            DownloadState.RUNNING -> ThrynxCyan
                            DownloadState.PAUSED -> ThrynxAmber
                            else -> ThrynxCoral
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)} • ${item.state.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Control Action Buttons
                when (item.state) {
                    DownloadState.RUNNING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = ThrynxCyan)
                        }
                    }
                    DownloadState.PAUSED, DownloadState.FAILED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = ThrynxCyan)
                        }
                    }
                    DownloadState.COMPLETED -> {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = ThrynxEmerald)
                        }
                    }
                    else -> {}
                }

                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = ThrynxCoral)
                }
            }

            if (item.state == DownloadState.RUNNING) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = if (item.totalBytes > 0) {
                    item.bytesDownloaded.toFloat() / item.totalBytes.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = ThrynxCyan
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        String.format("%.1f GB", gb)
    } else {
        String.format("%.1f MB", mb)
    }
}
