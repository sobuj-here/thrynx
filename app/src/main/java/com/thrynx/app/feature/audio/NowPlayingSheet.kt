package com.thrynx.app.feature.audio

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.lyrics.LrcLibClient
import com.thrynx.app.core.lyrics.LyricLine
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.feature.audio.components.MeshGradientBackground
import com.thrynx.app.feature.audio.components.ThinSlider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    playerManager: ThrynxPlayerManager,
    onDismiss: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val playbackState by playerManager.playbackState.collectAsState()
    val currentMedia = playbackState.currentMedia

    var showLyrics by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val lyricsListState = rememberLazyListState()

    // Fetch synced lyrics from LRCLIB when track changes
    LaunchedEffect(currentMedia?.id) {
        if (currentMedia != null) {
            isLoadingLyrics = true
            lyrics = null
            lyrics = LrcLibClient.fetchLyrics(
                title = currentMedia.title,
                artist = currentMedia.folderName.ifEmpty { "Audio Track" },
                durationMs = currentMedia.durationMs
            )
            isLoadingLyrics = false
        }
    }

    // Auto-scroll lyrics to keep current singing line centered
    LaunchedEffect(playbackState.currentPositionMs, showLyrics) {
        if (showLyrics && lyrics != null) {
            val currentIndex = lyrics!!.indexOfLast { it.startMs <= playbackState.currentPositionMs }
            if (currentIndex >= 0) {
                lyricsListState.animateScrollToItem(
                    index = (currentIndex - 2).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF070B10),
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxHeight(0.96f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Apple Music Ambient Mesh Backdrop
            MeshGradientBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Handle & Dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                            .clickable { onDismiss() }
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Mode Toggle: Artwork vs Synced Lyrics
                    IconButton(
                        onClick = { showLyrics = !showLyrics },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Default.MusicNote else Icons.Default.Subtitles,
                            contentDescription = "Toggle Lyrics",
                            tint = if (showLyrics) ThrynxCyan else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Center Content: Album Artwork or Synced Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!showLyrics) {
                        // Artwork View
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = ThrynxCyan.copy(alpha = 0.4f))
                                .clip(RoundedCornerShape(24.dp))
                                .background(ThrynxSurfaceLight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentMedia?.uri != null) {
                                AsyncImage(
                                    model = currentMedia.uri,
                                    contentDescription = currentMedia.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = ThrynxCyan.copy(alpha = 0.6f),
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                        }
                    } else {
                        // Word/Line Synced Lyrics View
                        if (isLoadingLyrics) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ThrynxCyan, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Loading lyrics...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = ThrynxTextMutedDark)
                                )
                            }
                        } else if (lyrics.isNullOrEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = ThrynxTextMutedDark,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No synchronized lyrics available",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = ThrynxTextMutedDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        } else {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 120.dp)
                            ) {
                                itemsIndexed(lyrics!!) { index, line ->
                                    val isCurrentLine = playbackState.currentPositionMs in line.startMs..line.endMs
                                    val isPastLine = playbackState.currentPositionMs > line.endMs

                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = if (isCurrentLine) 24.sp else 19.sp,
                                            lineHeight = 32.sp
                                        ),
                                        color = when {
                                            isCurrentLine -> Color.White
                                            isPastLine -> Color.White.copy(alpha = 0.45f)
                                            else -> Color.White.copy(alpha = 0.25f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp)
                                            .clickable {
                                                playerManager.seekTo(line.startMs)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title, Artist and Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentMedia?.title ?: "No Track Playing",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = currentMedia?.folderName?.ifEmpty { "Audio Track" } ?: "Audio Track",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.65f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Equalizer Button
                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ThrynxCyan.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = ThrynxCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Apple Music Hairline Thin Scrubber
                val currentMs = playbackState.currentPositionMs.toFloat()
                val durationMs = (playbackState.durationMs.takeIf { it > 0 } ?: 1L).toFloat()
                val progressFraction = (currentMs / durationMs).coerceIn(0f, 1f)

                ThinSlider(
                    value = progressFraction,
                    onValueChange = { frac ->
                        val targetMs = (frac * durationMs).toLong()
                        playerManager.seekTo(targetMs)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Time indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(playbackState.currentPositionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = formatDuration(playbackState.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Transport Controls: Prev, Rewind 10s, Large Play/Pause FAB, Forward 10s, Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous
                    IconButton(
                        onClick = { playerManager.playPrevious() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = { playerManager.seekTo(playbackState.currentPositionMs - 10_000L) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Large Play / Pause Button
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .shadow(16.dp, CircleShape, spotColor = ThrynxCyan.copy(alpha = 0.6f))
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                playerManager.togglePlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { playerManager.seekTo(playbackState.currentPositionMs + 10_000L) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = { playerManager.playNext() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
