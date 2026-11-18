package com.music.thrynx.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import com.music.thrynx.R
import com.music.thrynx.data.LocalVideo
import com.music.thrynx.data.LocalVideoRepository
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class VideoResizeMode(val labelRes: Int) {
    FIT(R.string.fit_screen),
    FILL(R.string.fill_screen),
    FIXED_16_9(R.string.zoom),
}

@OptIn(UnstableApi::class)
@Composable
fun LocalVideoPlayerScreen(
    videoList: List<LocalVideo>,
    initialIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (videoList.size - 1).coerceAtLeast(0))) }
    val currentVideo = videoList.getOrNull(currentIndex) ?: run {
        onBack()
        return
    }

    // Fullscreen state
    var isLandscape by remember { mutableStateOf(false) }

    fun toggleOrientation() {
        val target = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        activity?.requestedOrientation = target
        isLandscape = !isLandscape
    }

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Keep screen awake while player is open
    LaunchedEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .build().apply {
                playWhenReady = true
            }
    }

    var textureView by remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(textureView, exoPlayer) {
        val tv = textureView
        if (tv != null) {
            exoPlayer.setVideoTextureView(tv)
        }
        onDispose {
            if (tv != null) {
                exoPlayer.clearVideoTextureView(tv)
            }
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var bufferPosition by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Zoom & pan
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // HUD overlays
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var seekRippleSide by remember { mutableStateOf<Int?>(null) } // -1 left, 1 right

    // Track selections
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var availableTracks by remember { mutableStateOf<Tracks?>(null) }

    // Load video on index change
    LaunchedEffect(currentIndex) {
        val video = videoList.getOrNull(currentIndex) ?: return@LaunchedEffect
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            video.contentUri
        } else {
            val f = java.io.File(video.path)
            if (f.canRead()) android.net.Uri.fromFile(f) else video.contentUri
        }
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        zoomScale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Listener for state updates
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    videoDuration = if (d > 0L) d else currentVideo.durationMs
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val f = java.io.File(currentVideo.path)
                if (f.canRead()) {
                    val fileUri = android.net.Uri.fromFile(f)
                    if (exoPlayer.currentMediaItem?.localConfiguration?.uri != fileUri) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(fileUri))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                availableTracks = tracks
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic time tracking
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            playbackPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            videoDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(250L)
        }
    }

    // Auto-hide controls
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(controlsVisible, isPlaying, lastInteraction, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(3500L)
            controlsVisible = false
        }
    }

    BackHandler {
        if (isLocked) {
            isLocked = false
        } else if (isLandscape) {
            toggleOrientation()
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Main Video View with Pinch to Zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1.0f, 4.0f)
                            if (zoomScale > 1.0f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        textureView = this
                    }
                },
                update = { },
                onRelease = {
                    if (textureView === it) textureView = null
                },
                modifier = when (resizeMode) {
                    VideoResizeMode.FIT -> Modifier.fillMaxSize()
                    VideoResizeMode.FILL -> Modifier.fillMaxSize()
                    VideoResizeMode.FIXED_16_9 -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                },
            )
        }

        // Touch & Gesture Interaction Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                        )
                    } else {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                                lastInteraction = System.currentTimeMillis()
                            },
                            onDoubleTap = { offset ->
                                val width = size.width
                                if (offset.x < width * 0.4f) {
                                    // Seek backward 10s
                                    val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    playbackPosition = newPos
                                    seekRippleSide = -1
                                } else if (offset.x > width * 0.6f) {
                                    // Seek forward 10s
                                    val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(videoDuration)
                                    exoPlayer.seekTo(newPos)
                                    playbackPosition = newPos
                                    seekRippleSide = 1
                                } else {
                                    // Toggle play/pause
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                                lastInteraction = System.currentTimeMillis()
                            },
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectVerticalDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                hudText = null
                                hudIcon = null
                            },
                            onDragCancel = {
                                hudText = null
                                hudIcon = null
                            },
                            onVerticalDrag = { change, dragAmount ->
                                val pos = change.position
                                val deltaY = -dragAmount
                                if (kotlin.math.abs(deltaY) > 0.5f) {
                                    val isLeft = pos.x < size.width * 0.5f
                                    if (isLeft) {
                                        val window = activity?.window
                                        if (window != null) {
                                            val lp = window.attributes
                                            val currentBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                            val newBrightness = (currentBrightness + (deltaY / 600f)).coerceIn(0.01f, 1.0f)
                                            lp.screenBrightness = newBrightness
                                            window.attributes = lp
                                            hudText = "${(newBrightness * 100).roundToInt()}%"
                                            hudIcon = Icons.Rounded.WbSunny
                                        }
                                    } else {
                                        if (audioManager != null) {
                                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val step = if (deltaY > 0) 1 else -1
                                            val newVol = (curVol + step).coerceIn(0, maxVol)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            val pct = ((newVol.toFloat() / maxVol) * 100).roundToInt()
                                            hudText = "$pct%"
                                            hudIcon = if (newVol > 0) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown
                                        }
                                    }
                                    change.consume()
                                }
                            }
                        )
                    }
                },
        )

        // Double-Tap Ripple Overlay (-10s / +10s)
        LaunchedEffect(seekRippleSide) {
            if (seekRippleSide != null) {
                delay(650L)
                seekRippleSide = null
            }
        }
        if (seekRippleSide != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (seekRippleSide == -1) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.size(80.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (seekRippleSide == -1) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = if (seekRippleSide == -1) "-10s" else "+10s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Center HUD for Volume / Brightness
        if (hudText != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        hudIcon?.let { icon ->
                            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
                        }
                        Text(
                            text = hudText.orEmpty(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Locked mode indicator (floating unlock button)
        if (isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        controlsVisible = true
                        lastInteraction = System.currentTimeMillis()
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.unlocked),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Video Controls Overlay
        if (!isLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.75f),
                                0.25f to Color.Transparent,
                                0.75f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = currentVideo.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (currentVideo.resolutionLabel.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(
                                            text = currentVideo.resolutionLabel,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = currentVideo.folderName,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        // Aspect Ratio Cycle Button (Fit / Fill / 16:9)
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    VideoResizeMode.FIT -> VideoResizeMode.FILL
                                    VideoResizeMode.FILL -> VideoResizeMode.FIXED_16_9
                                    VideoResizeMode.FIXED_16_9 -> VideoResizeMode.FIT
                                }
                                zoomScale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                lastInteraction = System.currentTimeMillis()
                            },
                        ) {
                            Icon(
                                imageVector = when (resizeMode) {
                                    VideoResizeMode.FIT -> Icons.Rounded.FitScreen
                                    VideoResizeMode.FILL -> Icons.Rounded.Crop
                                    VideoResizeMode.FIXED_16_9 -> Icons.Rounded.Fullscreen
                                },
                                contentDescription = stringResource(resizeMode.labelRes),
                                tint = Color.White,
                            )
                        }

                        // Playback Speed Button
                        IconButton(onClick = { showSpeedDialog = true }) {
                            Icon(Icons.Rounded.Speed, contentDescription = stringResource(R.string.playback_speed), tint = Color.White)
                        }

                        // Subtitles & Audio Tracks Button
                        IconButton(onClick = { showTrackDialog = true }) {
                            Icon(Icons.Rounded.Subtitles, contentDescription = stringResource(R.string.subtitles), tint = Color.White)
                        }

                        // Lock Button
                        IconButton(
                            onClick = {
                                isLocked = true
                                lastInteraction = System.currentTimeMillis()
                            },
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = stringResource(R.string.locked), tint = Color.White)
                        }
                    }

                    // Center Transport Controls (Prev, Rewind, Play/Pause, Forward, Next)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Previous Video
                        IconButton(
                            onClick = {
                                if (currentIndex > 0) currentIndex--
                                lastInteraction = System.currentTimeMillis()
                            },
                            enabled = currentIndex > 0,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = null,
                                tint = if (currentIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        // Rewind 10s
                        IconButton(
                            onClick = {
                                val pos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(pos)
                                playbackPosition = pos
                                lastInteraction = System.currentTimeMillis()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.FastRewind,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        // Big Play / Pause
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    lastInteraction = System.currentTimeMillis()
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                        }

                        // Fast Forward 10s
                        IconButton(
                            onClick = {
                                val pos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(videoDuration)
                                exoPlayer.seekTo(pos)
                                playbackPosition = pos
                                lastInteraction = System.currentTimeMillis()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        // Next Video
                        IconButton(
                            onClick = {
                                if (currentIndex < videoList.size - 1) currentIndex++
                                lastInteraction = System.currentTimeMillis()
                            },
                            enabled = currentIndex < videoList.size - 1,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = if (currentIndex < videoList.size - 1) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    // Bottom Bar (Scrubber, Timestamps, PiP, Orientation)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Slider(
                            value = playbackPosition.toFloat(),
                            onValueChange = { newPosition ->
                                playbackPosition = newPosition.toLong()
                                lastInteraction = System.currentTimeMillis()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(playbackPosition)
                                lastInteraction = System.currentTimeMillis()
                            },
                            valueRange = 0f..videoDuration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${LocalVideoRepository.formatDuration(playbackPosition)} / ${LocalVideoRepository.formatDuration(videoDuration)}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // PiP Button (Android 8.0+)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    IconButton(
                                        onClick = {
                                            controlsVisible = false
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(Rational(16, 9))
                                                .build()
                                            activity?.enterPictureInPictureMode(params)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Rounded.PictureInPicture,
                                            contentDescription = stringResource(R.string.pip_mode),
                                            tint = Color.White,
                                        )
                                    }
                                }

                                // Fullscreen / Orientation Toggle
                                IconButton(onClick = { toggleOrientation() }) {
                                    Icon(
                                        imageVector = if (isLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                        contentDescription = null,
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed Selection Dialog
        if (showSpeedDialog) {
            Dialog(onDismissRequest = { showSpeedDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.widthIn(min = 280.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.playback_speed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        speeds.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isSelected) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitles and Audio Tracks Dialog
        if (showTrackDialog) {
            Dialog(onDismissRequest = { showTrackDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.widthIn(min = 300.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.subtitles) + " & " + stringResource(R.string.audio_tracks),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(14.dp))

                        val tracks = availableTracks
                        if (tracks == null || tracks.groups.isEmpty()) {
                            Text(
                                text = "No extra tracks found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

                            if (audioGroups.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.audio_tracks),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                audioGroups.forEachIndexed { groupIndex, group ->
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val isSelected = group.isTrackSelected(i)
                                        val label = format.label ?: format.language ?: "Track ${i + 1}"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                        .build()
                                                    showTrackDialog = false
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(text = label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            if (textGroups.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.subtitles),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                // Option to turn off subtitles
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            showTrackDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text("Off")
                                }
                                textGroups.forEachIndexed { groupIndex, group ->
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val isSelected = group.isTrackSelected(i)
                                        val label = format.label ?: format.language ?: "Subtitle ${i + 1}"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                        .build()
                                                    showTrackDialog = false
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(text = label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showTrackDialog = false }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }
        }
    }
}
