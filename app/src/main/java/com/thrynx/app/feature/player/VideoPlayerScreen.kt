package com.thrynx.app.feature.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.AudioTrackInfo
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.model.SubtitleStyle
import com.thrynx.app.core.model.SubtitleTrackInfo
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.feature.home.formatDuration

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playerManager: ThrynxPlayerManager,
    onBack: () -> Unit,
    onEnterPiP: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by playerManager.playbackState.collectAsState()
    val subtitleStyle by playerManager.subtitleStyle.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }

    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Auto-hide controls
    LaunchedEffect(showControls, playbackState.isPlaying) {
        if (showControls && playbackState.isPlaying && !playbackState.isControlsLocked) {
            kotlinx.coroutines.delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(playbackState.isControlsLocked) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!playbackState.isControlsLocked) {
                            if (offset.x < size.width / 2) {
                                playerManager.seekBackward(10_000L)
                            } else {
                                playerManager.seekForward(10_000L)
                            }
                        }
                    }
                )
            }
    ) {
        // Video Surface via AndroidView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            }
        )

        // Subtitle Overlay (if custom styling applied)
        if (playbackState.selectedSubtitleTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showControls) 80.dp else 30.dp, start = 20.dp, end = 20.dp)
            ) {
                // Media3 player handles subtitle rendering internally on PlayerView, custom styling applied via subtitleStyle
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text(
                        text = playbackState.currentMedia?.title ?: stringResource(id = R.string.player_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // Audio Track Selector
                    IconButton(onClick = { showAudioTrackDialog = true }) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio Track", tint = ThrynxCyan)
                    }

                    // Subtitle Selector
                    IconButton(onClick = { showSubtitleDialog = true }) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                    }

                    // Playback Speed
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                    }

                    // Aspect Ratio
                    IconButton(onClick = { showAspectRatioDialog = true }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }

                    // PiP Button
                    IconButton(onClick = onEnterPiP) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                    }
                }

                // Center Play/Pause & Skip Controls
                if (!playbackState.isControlsLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(
                            onClick = { playerManager.seekBackward(10_000L) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = { playerManager.togglePlayPause() },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ThrynxCyan)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = ThrynxBgDark,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerManager.seekForward(10_000L) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                // Bottom Timeline & Scrubber Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Audio-Only Mode Toggle
                            TextButton(
                                onClick = { playerManager.setAudioOnlyMode(!playbackState.isAudioOnly) }
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isAudioOnly) Icons.Default.Headphones else Icons.Default.HeadsetOff,
                                    contentDescription = null,
                                    tint = if (playbackState.isAudioOnly) ThrynxCyan else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(id = R.string.player_audio_only),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (playbackState.isAudioOnly) ThrynxCyan else Color.White
                                )
                            }

                            // Lock Screen Controls
                            IconButton(onClick = { playerManager.setControlsLocked(!playbackState.isControlsLocked) }) {
                                Icon(
                                    imageVector = if (playbackState.isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Lock",
                                    tint = if (playbackState.isControlsLocked) ThrynxCyan else Color.White
                                )
                            }
                        }
                    }

                    if (!playbackState.isControlsLocked) {
                        Slider(
                            value = playbackState.currentPositionMs.toFloat(),
                            onValueChange = { playerManager.seekTo(it.toLong()) },
                            valueRange = 0f..(playbackState.durationMs.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = ThrynxCyan,
                                activeTrackColor = ThrynxCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }

        // Multiple Audio Track Selection Dialog
        if (showAudioTrackDialog) {
            AlertDialog(
                onDismissRequest = { showAudioTrackDialog = false },
                title = { Text(stringResource(id = R.string.player_audio_track)) },
                text = {
                    if (playbackState.audioTracks.isEmpty()) {
                        Text(stringResource(id = R.string.player_no_audio_tracks))
                    } else {
                        Column {
                            playbackState.audioTracks.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerManager.selectAudioTrack(track)
                                            showAudioTrackDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = track.isSelected || playbackState.selectedAudioTrack?.id == track.id,
                                        onClick = {
                                            playerManager.selectAudioTrack(track)
                                            showAudioTrackDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${track.displayLanguage} (${track.channelCount} ch)",
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (track.isSelected) ThrynxCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioTrackDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // Subtitle Language Selection Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text(stringResource(id = R.string.player_subtitle_track)) },
                text = {
                    Column {
                        // Option: Subtitles Off
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerManager.selectSubtitleTrack(null)
                                    showSubtitleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackState.selectedSubtitleTrack == null,
                                onClick = {
                                    playerManager.selectSubtitleTrack(null)
                                    showSubtitleDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.player_subtitles_off))
                        }

                        // Available Tracks
                        playbackState.subtitleTracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerManager.selectSubtitleTrack(track)
                                        showSubtitleDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = playbackState.selectedSubtitleTrack?.id == track.id,
                                    onClick = {
                                        playerManager.selectSubtitleTrack(track)
                                        showSubtitleDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(track.displayLanguage)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // Playback Speed Dialog
        if (showSpeedDialog) {
            val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text(stringResource(id = R.string.player_speed)) },
                text = {
                    Column {
                        speedOptions.forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerManager.setSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = playbackState.playbackSpeed == speed,
                                    onClick = {
                                        playerManager.setSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${speed}x")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // Aspect Ratio Dialog
        if (showAspectRatioDialog) {
            AlertDialog(
                onDismissRequest = { showAspectRatioDialog = false },
                title = { Text(stringResource(id = R.string.player_aspect_ratio)) },
                text = {
                    Column {
                        val ratios = listOf(
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, stringResource(id = R.string.player_ratio_fit)),
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, stringResource(id = R.string.player_ratio_fill)),
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "16:9 Widescreen"),
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch to Window")
                        )
                        ratios.forEach { (mode, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        resizeMode = mode
                                        showAspectRatioDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = resizeMode == mode,
                                    onClick = {
                                        resizeMode = mode
                                        showAspectRatioDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAspectRatioDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }
    }
}
