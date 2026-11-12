package com.thrynx.app.feature.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.AudioTrackInfo
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.model.SubtitleTrackInfo
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.feature.home.formatDuration
import com.thrynx.app.feature.settings.formatFileSize
import kotlinx.coroutines.delay
import kotlin.math.sin

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playerManager: ThrynxPlayerManager,
    onBack: () -> Unit,
    onEnterPiP: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val playbackState by playerManager.playbackState.collectAsState()
    val preferencesManager = remember { com.thrynx.app.ThrynxApplication.instance.preferencesManager }
    val defaultResizePref by preferencesManager.defaultResizeModeFlow.collectAsState(initial = "fit")
    val defaultOrientationPref by preferencesManager.defaultOrientationFlow.collectAsState(initial = "auto")
    val gesturesEnabledPref by preferencesManager.gesturesEnabledFlow.collectAsState(initial = true)

    var showControls by remember { mutableStateOf(true) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Fit vs Fill Mode (Fit = AspectRatioFrameLayout.RESIZE_MODE_FIT, Fill = AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    var resizeMode by remember(defaultResizePref) {
        mutableIntStateOf(
            if (defaultResizePref == "fill") AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else AspectRatioFrameLayout.RESIZE_MODE_FIT
        )
    }
    var isLandscape by remember { mutableStateOf(false) }

    LaunchedEffect(defaultOrientationPref) {
        when (defaultOrientationPref) {
            "landscape" -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                isLandscape = true
            }
            "portrait" -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                isLandscape = false
            }
            else -> {}
        }
    }

    // System Bar Fullscreen Controller
    fun setFullscreen(enable: Boolean) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (enable) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Clean up fullscreen & orientation on exit
    DisposableEffect(Unit) {
        onDispose {
            setFullscreen(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(isLandscape) {
        setFullscreen(isLandscape)
    }

    // Unified Back Navigation Handler
    val handleBackPress = {
        if (playbackState.isControlsLocked) {
            playerManager.setControlsLocked(false)
        } else if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isLandscape = false
            setFullscreen(false)
        } else {
            setFullscreen(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onBack()
        }
    }

    // System Back Gesture / Key Listener
    BackHandler(enabled = true) {
        handleBackPress()
    }

    // Gesture HUD overlay state
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }

    fun showHud(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        hudText = text
        hudIcon = icon
    }

    LaunchedEffect(hudText) {
        if (hudText != null) {
            delay(1200)
            hudText = null
            hudIcon = null
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, playbackState.isPlaying) {
        if (showControls && playbackState.isPlaying && !playbackState.isControlsLocked) {
            delay(4500)
            showControls = false
        }
    }

    // External Subtitle Picker
    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            playerManager.loadExternalSubtitle(uri)
            showSubtitleDialog = false
            showHud("External Subtitle Loaded", Icons.Default.Subtitles)
        }
    }

    val isAudioContent = playbackState.currentMedia?.isVideo == false || playbackState.isAudioOnly

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture handling: Tap for controls, Double tap for seek, Vertical drag for volume/brightness
            .pointerInput(playbackState.isControlsLocked, gesturesEnabledPref) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!playbackState.isControlsLocked && gesturesEnabledPref) {
                            if (offset.x < size.width / 2) {
                                playerManager.seekBackward(10_000L)
                                showHud("-10s", Icons.Default.Replay10)
                            } else {
                                playerManager.seekForward(10_000L)
                                showHud("+10s", Icons.Default.Forward10)
                            }
                        }
                    }
                )
            }
            .pointerInput(playbackState.isControlsLocked, gesturesEnabledPref) {
                if (!playbackState.isControlsLocked && gesturesEnabledPref) {
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY += dragAmount.y
                            val isLeftSide = change.position.x < size.width / 2

                            if (isLeftSide) {
                                // Brightness adjustment
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    var currentBrightness = lp.screenBrightness
                                    if (currentBrightness < 0f) currentBrightness = 0.5f
                                    val delta = -dragAmount.y / (size.height * 0.7f)
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.02f, 1.0f)
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                    showHud("Brightness: ${(newBrightness * 100).toInt()}%", Icons.Default.BrightnessMedium)
                                }
                            } else {
                                // Volume adjustment
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = -dragAmount.y / (size.height / maxVol)
                                if (Math.abs(delta) >= 1f) {
                                    val targetVol = (currentVol + delta.toInt()).coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    val pct = ((targetVol.toFloat() / maxVol) * 100).toInt()
                                    showHud(
                                        "Volume: $pct%",
                                        if (targetVol == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                                    )
                                }
                            }
                        }
                    )
                }
            }
    ) {
        if (!isAudioContent) {
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
        } else {
            // Integrated Audio Player Experience
            AudioPlayerVisualizerView(
                playbackState = playbackState,
                onTogglePlayPause = { playerManager.togglePlayPause() },
                onSeekBackward = { playerManager.seekBackward(10_000L) },
                onSeekForward = { playerManager.seekForward(10_000L) },
                onToggleShuffle = { playerManager.toggleShuffle() },
                onToggleRepeat = { playerManager.toggleRepeatMode() },
                onSwitchBackToVideo = if (playbackState.isAudioOnly) {
                    { playerManager.setAudioOnlyMode(false) }
                } else null
            )
        }

        // Gesture Feedback HUD Badge
        hudText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    hudIcon?.let { icon ->
                        Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay Controls (Top & Bottom)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = handleBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text(
                        text = playbackState.currentMedia?.title ?: stringResource(id = R.string.player_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // Audio Track Selector
                    IconButton(onClick = { showAudioTrackDialog = true }) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = "Audio Track",
                            tint = if (playbackState.audioTracks.size > 1) ThrynxCyan else Color.White
                        )
                    }

                    // Subtitle Selector
                    if (!isAudioContent) {
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = "Subtitles",
                                tint = if (playbackState.selectedSubtitleTrack != null) ThrynxCyan else Color.White
                            )
                        }
                    }

                    // Fit / Fill Quick Toggle (Part 3 requirement)
                    if (!isAudioContent) {
                        TextButton(
                            onClick = {
                                resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                val modeName = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Fill (Crop)" else "Fit (Aspect)"
                                showHud(modeName, Icons.Default.AspectRatio)
                            }
                        ) {
                            Text(
                                text = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Fill" else "Fit",
                                color = ThrynxCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Playback Speed
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                    }

                    // Aspect Ratio Dialog (Fit, Fill, 16:9, Stretch)
                    if (!isAudioContent) {
                        IconButton(onClick = { showAspectRatioDialog = true }) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                        }
                    }

                    // Media Information
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Information", tint = Color.White)
                    }

                    // Screen Rotation Toggle (Portrait ⟷ Landscape)
                    IconButton(onClick = {
                        activity?.let { act ->
                            isLandscape = !isLandscape
                            act.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.ScreenRotation else Icons.Default.ScreenLockPortrait,
                            contentDescription = "Rotate",
                            tint = Color.White
                        )
                    }

                    // PiP Button
                    IconButton(onClick = onEnterPiP) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                    }
                }

                // Center Play/Pause & Skip Controls
                if (!isAudioContent && !playbackState.isControlsLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(
                            onClick = {
                                playerManager.seekBackward(10_000L)
                                showHud("-10s", Icons.Default.Replay10)
                            },
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
                            onClick = {
                                playerManager.seekForward(10_000L)
                                showHud("+10s", Icons.Default.Forward10)
                            },
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
                            // Audio-Only Mode Toggle (for video content)
                            if (playbackState.currentMedia?.isVideo == true) {
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

        // Multiple Audio Track Selection Dialog (Part 2 requirement: Real tracks only, no fake options)
        if (showAudioTrackDialog) {
            AlertDialog(
                onDismissRequest = { showAudioTrackDialog = false },
                title = { Text(stringResource(id = R.string.player_audio_track)) },
                text = {
                    if (playbackState.audioTracks.isEmpty()) {
                        Text(stringResource(id = R.string.player_no_audio_tracks))
                    } else if (playbackState.audioTracks.size == 1) {
                        val track = playbackState.audioTracks.first()
                        Column {
                            Text(
                                text = "Single Audio Track (${track.displayLanguage})",
                                fontWeight = FontWeight.SemiBold,
                                color = ThrynxCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Format: ${track.mimeType} • ${track.channelCount} Channels • ${track.sampleRate} Hz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                    Column {
                                        Text(
                                            text = track.displayLanguage,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (track.isSelected) ThrynxCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${track.channelCount} Channels • ${track.sampleRate} Hz • ${track.mimeType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Load External Subtitle File Button
                        OutlinedButton(
                            onClick = { subtitlePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.player_load_external_subtitle))
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

        // Aspect Ratio Dialog (Fit, Fill/Crop, 16:9, Stretch)
        if (showAspectRatioDialog) {
            AlertDialog(
                onDismissRequest = { showAspectRatioDialog = false },
                title = { Text(stringResource(id = R.string.player_aspect_ratio)) },
                text = {
                    Column {
                        val ratios = listOf(
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit to Screen (Preserve Aspect)"),
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Fill Screen (Crop Black Bars)"),
                            Pair(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "16:9 Widescreen Frame"),
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

        // Video Information Dialog
        if (showInfoDialog) {
            val currentMedia = playbackState.currentMedia
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Playback Details") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Title: ${currentMedia?.title ?: "Unknown"}", fontWeight = FontWeight.Bold)
                        Text("Duration: ${formatDuration(playbackState.durationMs)}")
                        if (currentMedia != null && currentMedia.width > 0 && currentMedia.height > 0) {
                            Text("Resolution: ${currentMedia.width} x ${currentMedia.height}")
                        }
                        if (currentMedia != null && currentMedia.sizeBytes > 0) {
                            Text("File Size: ${formatFileSize(currentMedia.sizeBytes)}")
                        }
                        Text("Audio Tracks: ${playbackState.audioTracks.size}")
                        Text("Subtitle Tracks: ${playbackState.subtitleTracks.size}")
                        Text("Speed: ${playbackState.playbackSpeed}x")
                        Text("Viewing Mode: ${if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Fill (Crop)" else "Fit"}")
                        if (currentMedia?.path?.isNotBlank() == true) {
                            Text("Path: ${currentMedia.path}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }
    }
}

/**
 * Integrated Audio Visualizer and Disc view for audio files and audio-only video playback.
 */
@Composable
fun AudioPlayerVisualizerView(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSwitchBackToVideo: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_value"
    )

    val currentMedia = playbackState.currentMedia

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ThrynxPurple.copy(alpha = 0.35f),
                        ThrynxBgDark,
                        Color.Black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Rotating Vinyl Disc representation
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141923))
                    .rotate(if (playbackState.isPlaying) rotation else 0f),
                contentAlignment = Alignment.Center
            ) {
                // Outer Grooves
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2433)),
                    contentAlignment = Alignment.Center
                ) {
                    // Center Album Label
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(ThrynxCyan, ThrynxPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title & Artist
            Text(
                text = currentMedia?.title ?: "Audio Track",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentMedia?.artist?.ifBlank { "Unknown Artist" } ?: "THRYNX Audio",
                style = MaterialTheme.typography.bodyMedium,
                color = ThrynxTextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Equalizer Waveform Visualization
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val barCount = 24
                for (i in 0 until barCount) {
                    val phase = (i * 0.25f)
                    val baseHeight = if (playbackState.isPlaying) {
                        (sin((rotation * 0.05f) + phase) * 0.5f + 0.5f).coerceIn(0.15f, 1.0f)
                    } else {
                        0.15f
                    }
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight(baseHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(ThrynxCyan, ThrynxPurple)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Playback Mode Actions (Shuffle, Repeat, Switch to Video)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) ThrynxCyan else Color.Gray
                    )
                }

                if (onSwitchBackToVideo != null) {
                    OutlinedButton(
                        onClick = onSwitchBackToVideo,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show Video", fontSize = 12.sp)
                    }
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (playbackState.isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playbackState.isRepeatOne) ThrynxCyan else Color.Gray
                    )
                }
            }
        }
    }
}
