package com.thrynx.app.feature.music

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.feature.home.formatDuration

@Composable
fun MusicPlayerScreen(
    playerManager: ThrynxPlayerManager,
    songs: List<MediaItemModel>,
    onToggleFavorite: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by playerManager.playbackState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(id = R.string.music_songs),
        stringResource(id = R.string.music_artists),
        stringResource(id = R.string.music_albums),
        stringResource(id = R.string.music_favorites)
    )

    var showSleepTimerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Music Studio Header
        ThrynxHeader(
            title = stringResource(id = R.string.music_title),
            actions = {
                IconButton(onClick = { showSleepTimerDialog = true }) {
                    Icon(Icons.Default.Bedtime, contentDescription = "Sleep Timer", tint = ThrynxCyan)
                }
            }
        )

        // Category Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ThrynxCyan
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Song List or Empty State
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.music_no_songs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(songs) { song ->
                    val isCurrent = playbackState.currentMedia?.id == song.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerManager.playQueue(songs, songs.indexOf(song))
                            }
                            .background(
                                if (isCurrent) ThrynxCyan.copy(alpha = 0.1f) else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) ThrynxCyan else ThrynxCardDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCurrent && playbackState.isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) ThrynxBgDark else ThrynxCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) ThrynxCyan else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.artist} • ${formatDuration(song.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        IconButton(onClick = { onToggleFavorite(song) }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (song.isFavorite) ThrynxCoral else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Active Player Bar at Bottom (if media loaded)
        playbackState.currentMedia?.let { current ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Micro Visualizer Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        repeat(24) { i ->
                            val heightFrac = if (playbackState.isPlaying) {
                                (Math.sin((i + System.currentTimeMillis() / 150.0)).toFloat().coerceIn(0.2f, 1f))
                            } else 0.2f
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight(heightFrac)
                                    .clip(CircleShape)
                                    .background(ThrynxCyan)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { playerManager.toggleShuffle() }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.isShuffle) ThrynxCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { playerManager.togglePlayPause() }) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play/Pause",
                                tint = ThrynxCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(onClick = { playerManager.toggleRepeatMode() }) {
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = if (playbackState.isRepeatOne) ThrynxCoral else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text(stringResource(id = R.string.music_sleep_timer)) },
                text = {
                    Column {
                        val timerMinutes = listOf(15, 30, 45, 60)
                        timerMinutes.forEach { min ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Sleep timer trigger
                                        showSleepTimerDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = ThrynxCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("$min minutes")
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.TimerOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(id = R.string.music_sleep_timer_off))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }
    }
}
