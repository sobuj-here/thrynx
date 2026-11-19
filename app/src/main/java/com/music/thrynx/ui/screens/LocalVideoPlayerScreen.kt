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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.material.icons.rounded.ScreenRotation
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import com.music.thrynx.R
import com.music.thrynx.data.LocalVideo
import com.music.thrynx.data.LocalVideoRepository
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class VideoResizeMode(val labelRes: Int) {
    FIT(R.string.fit_screen),
    FILL(R.string.fill_screen),
    FIXED_16_9(R.string.zoom),
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
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
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (videoList.size - 1).coerceAtLeast(0))) }
    val currentVideo = videoList.getOrNull(currentIndex) ?: run {
        onBack()
        return
    }

    // Fullscreen state
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var isLandscape by remember {
        mutableStateOf(configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }



    fun toggleOrientation() {
        val act = activity ?: return
        val target = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        act.requestedOrientation = target
    }

    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Allow sensor orientation so device can auto-rotate smoothly
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        // Immersive full screen: hide status bar and navigation bar
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
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

    // Video aspect ratio tracking
    val initialAspect = remember(currentVideo.id) {
        if (currentVideo.width > 0 && currentVideo.height > 0) {
            currentVideo.width.toFloat() / currentVideo.height.toFloat()
        } else {
            16f / 9f
        }
    }
    var videoAspectRatio by remember(currentVideo.id) { mutableFloatStateOf(initialAspect) }

    // Zoom & pan
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(configuration.orientation) {
        isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // Reset zoom and pan on orientation changes to prevent unwanted cropping
        zoomScale = 1.0f
        offsetX = 0f
        offsetY = 0f
    }

    // HUD overlays
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var seekRippleSide by remember { mutableStateOf<Int?>(null) } // -1 left, 1 right

    // Track selections
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var availableTracks by remember { mutableStateOf<Tracks?>(null) }
    var showRemainingTime by remember { mutableStateOf(false) }

    // Battery & Clock tracking
    var currentTimeStr by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible) {
        while (controlsVisible) {
            val now = java.util.Date()
            currentTimeStr = android.text.format.DateFormat.getTimeFormat(context).format(now)
            delay(1000L)
        }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                if (intent?.action == android.content.Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL
                    if (level >= 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) {
            val level = sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val status = sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale
            }
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

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
        LocalVideoRepository.setLastPlayedVideoId(context, video.id)
        val resumePos = LocalVideoRepository.getResumePosition(context, video.id)
        if (resumePos > 3000L) {
            exoPlayer.seekTo(resumePos)
            playbackPosition = resumePos
            hudText = "Resumed: ${LocalVideoRepository.formatDuration(resumePos)}"
            hudIcon = Icons.Rounded.PlayCircle
            coroutineScope.launch {
                delay(2500L)
                if (hudText?.startsWith("Resumed") == true) {
                    hudText = null
                    hudIcon = null
                }
            }
        }
        exoPlayer.play()
        zoomScale = 1f
        offsetX = 0f
        offsetY = 0f
        resizeMode = VideoResizeMode.FIT
        if (video.width > 0 && video.height > 0) {
            videoAspectRatio = video.width.toFloat() / video.height.toFloat()
        }
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
                } else if (playbackState == Player.STATE_ENDED) {
                    LocalVideoRepository.saveResumePosition(context, currentVideo.id, 0L, videoDuration)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val isRotated = videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270
                    val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1.0f
                    val displayWidth = if (isRotated) videoSize.height.toFloat() else (videoSize.width * pixelRatio)
                    val displayHeight = if (isRotated) (videoSize.width * pixelRatio) else videoSize.height.toFloat()
                    if (displayWidth > 0f && displayHeight > 0f) {
                        videoAspectRatio = displayWidth / displayHeight
                    }
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
            LocalVideoRepository.saveResumePosition(context, currentVideo.id, exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }

    // Gesture drag state for horizontal seek and vertical gestures
    var dragMode by remember { mutableIntStateOf(0) } // 0: None, 1: Vertical, 2: Horizontal
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }

    // Periodic time tracking (paused during scrubbing) & periodic resume saving
    LaunchedEffect(isPlaying, isScrubbing, currentIndex) {
        var tickCount = 0
        while (isPlaying && !isScrubbing) {
            playbackPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0L) videoDuration = d
            tickCount++
            if (tickCount % 4 == 0 && playbackPosition > 0L) {
                LocalVideoRepository.saveResumePosition(context, currentVideo.id, playbackPosition, videoDuration)
            }
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

    val safeAspect = remember(videoAspectRatio) {
        if (videoAspectRatio.isFinite() && videoAspectRatio > 0.05f) videoAspectRatio else (16f / 9f)
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
                .clipToBounds()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = offsetX
                    translationY = offsetY
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
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val width = constraints.maxWidth
                        val height = if (constraints.hasBoundedHeight) {
                            constraints.maxHeight
                        } else {
                            (width / safeAspect).roundToInt()
                        }
                        val boundsAspect = width.toFloat() / height.coerceAtLeast(1)
                        val wider = boundsAspect > safeAspect

                        val (w, h) = when (resizeMode) {
                            VideoResizeMode.FIT -> {
                                // Fit-to-screen: letterbox / pillarbox, preserve exact aspect ratio without stretching or cropping
                                if (wider) {
                                    (height * safeAspect).roundToInt() to height
                                } else {
                                    width to (width / safeAspect).roundToInt()
                                }
                            }
                            VideoResizeMode.FILL -> {
                                // Proportional fill: crop overflow without distortion
                                if (wider) {
                                    width to (width / safeAspect).roundToInt()
                                } else {
                                    (height * safeAspect).roundToInt() to height
                                }
                            }
                            VideoResizeMode.FIXED_16_9 -> {
                                // 16:9 fit-to-screen
                                val targetRatio = 16f / 9f
                                val wider16_9 = boundsAspect > targetRatio
                                if (wider16_9) {
                                    (height * targetRatio).roundToInt() to height
                                } else {
                                    width to (width / targetRatio).roundToInt()
                                }
                            }
                        }

                        val placeable = measurable.measure(
                            Constraints.fixed(
                                w.coerceAtLeast(1),
                                h.coerceAtLeast(1),
                            ),
                        )
                        layout(width, height) {
                            placeable.place((width - w) / 2, (height - h) / 2)
                        }
                    },
            )
        }

        // Unified Touch & Gesture Interaction Surface (Multi-touch Zoom & Pan, Horizontal Seek, Vertical Brightness/Volume, Taps)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                        )
                    } else {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startTime = System.currentTimeMillis()
                            val startPos = down.position

                            var mode = 0 // 0: None, 1: Vertical, 2: Horizontal, 3: PinchZoom
                            var prevDist = 0f
                            var prevCentroid = startPos
                            var dragAccumulatorY = 0f
                            var currentSeekTargetMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                            val dragStartPosMs = currentSeekTargetMs
                            val isLeftSide = startPos.x < size.width * 0.5f

                            do {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }
                                val count = pressedChanges.size

                                if (count >= 2) {
                                    // Pinch to Zoom In / Zoom Out and Pan
                                    val p0 = pressedChanges[0].position
                                    val p1 = pressedChanges[1].position
                                    val dist = (p0 - p1).getDistance()
                                    val centroid = (p0 + p1) / 2f

                                    if (mode != 3) {
                                        mode = 3
                                        prevDist = dist
                                        prevCentroid = centroid
                                        hudText = null
                                        hudIcon = null
                                    } else {
                                        if (prevDist > 1f && dist > 1f) {
                                            val scaleFactor = dist / prevDist
                                            val newScale = (zoomScale * scaleFactor).coerceIn(1.0f, 4.5f)
                                            zoomScale = newScale
                                            if (newScale > 1.0f) {
                                                val pan = centroid - prevCentroid
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                            val pct = (newScale * 100).roundToInt()
                                            hudText = "Zoom $pct%"
                                            hudIcon = Icons.Rounded.FitScreen
                                        }
                                        prevDist = dist
                                        prevCentroid = centroid
                                    }
                                    pressedChanges.forEach { it.consume() }
                                } else if (count == 1 && mode != 3) {
                                    val change = pressedChanges[0]
                                    val currentPos = change.position
                                    val totalDelta = currentPos - startPos

                                    if (mode == 0) {
                                        val absX = kotlin.math.abs(totalDelta.x)
                                        val absY = kotlin.math.abs(totalDelta.y)
                                        val dist = kotlin.math.sqrt(absX * absX + absY * absY)
                                        if (dist > 18f) {
                                            if (absX > absY * 1.2f) {
                                                mode = 2 // Horizontal seek
                                            } else if (absY > absX * 1.2f) {
                                                mode = 1 // Vertical brightness / volume
                                            }
                                        }
                                    }

                                    if (mode == 2) {
                                        val screenWidth = size.width.coerceAtLeast(1)
                                        val seekRangeMs = (videoDuration.toFloat() * 0.25f).coerceIn(60_000f, 600_000f)
                                        val deltaMs = (totalDelta.x / screenWidth) * seekRangeMs
                                        val target = (dragStartPosMs + deltaMs.toLong()).coerceIn(0L, videoDuration.coerceAtLeast(1L))
                                        currentSeekTargetMs = target
                                        seekTargetMs = target
                                        val diffSec = (target - dragStartPosMs) / 1000L
                                        val sign = if (diffSec >= 0) "+" else ""
                                        hudText = "${LocalVideoRepository.formatDuration(target)} ($sign${diffSec}s)"
                                        hudIcon = if (diffSec >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind
                                        change.consume()
                                    } else if (mode == 1) {
                                        val stepDeltaY = -(change.position.y - change.previousPosition.y)
                                        dragAccumulatorY += stepDeltaY

                                        if (isLeftSide) {
                                            val window = activity?.window
                                            if (window != null) {
                                                val lp = window.attributes
                                                val currentBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                                val newBrightness = (currentBrightness + (stepDeltaY / 500f)).coerceIn(0.01f, 1.0f)
                                                lp.screenBrightness = newBrightness
                                                window.attributes = lp
                                                hudText = "${(newBrightness * 100).roundToInt()}%"
                                                hudIcon = Icons.Rounded.WbSunny
                                            }
                                        } else {
                                            if (audioManager != null) {
                                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                if (kotlin.math.abs(dragAccumulatorY) >= 20f) {
                                                    val steps = (dragAccumulatorY / 20f).toInt()
                                                    val newVol = (curVol + steps).coerceIn(0, maxVol)
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                    dragAccumulatorY -= steps * 20f
                                                    val pct = ((newVol.toFloat() / maxVol) * 100).roundToInt()
                                                    hudText = "$pct%"
                                                    hudIcon = if (newVol > 0) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown
                                                }
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            } while (pressedChanges.isNotEmpty())

                            // Gesture ended
                            if (mode == 3) {
                                if (zoomScale <= 1.05f) {
                                    zoomScale = 1.0f
                                    offsetX = 0f
                                    offsetY = 0f
                                    hudText = "Fit screen"
                                }
                                coroutineScope.launch {
                                    delay(1200L)
                                    if (hudText?.startsWith("Zoom") == true || hudText == "Fit screen") {
                                        hudText = null
                                        hudIcon = null
                                    }
                                }
                            } else if (mode == 2) {
                                exoPlayer.seekTo(currentSeekTargetMs)
                                playbackPosition = currentSeekTargetMs
                                hudText = null
                                hudIcon = null
                            } else if (mode == 1) {
                                hudText = null
                                hudIcon = null
                            } else {
                                // Tap handling
                                val now = System.currentTimeMillis()
                                val isDoubleTap = (now - lastTapTimestamp < 320L) &&
                                    ((startPos - lastTapPosition).getDistance() < 120f)

                                if (isDoubleTap) {
                                    singleTapJob?.cancel()
                                    val width = size.width
                                    if (startPos.x < width * 0.35f) {
                                        val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(newPos)
                                        playbackPosition = newPos
                                        seekRippleSide = -1
                                    } else if (startPos.x > width * 0.65f) {
                                        val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(videoDuration)
                                        exoPlayer.seekTo(newPos)
                                        playbackPosition = newPos
                                        seekRippleSide = 1
                                    } else {
                                        if (zoomScale > 1.05f) {
                                            zoomScale = 1.0f
                                            offsetX = 0f
                                            offsetY = 0f
                                            hudText = "Fit screen"
                                            hudIcon = Icons.Rounded.FitScreen
                                            coroutineScope.launch {
                                                delay(1000L)
                                                if (hudText == "Fit screen") {
                                                    hudText = null
                                                    hudIcon = null
                                                }
                                            }
                                        } else {
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                    }
                                    lastTapTimestamp = 0L
                                } else {
                                    lastTapTimestamp = now
                                    lastTapPosition = startPos
                                    singleTapJob?.cancel()
                                    singleTapJob = coroutineScope.launch {
                                        delay(300L)
                                        controlsVisible = !controlsVisible
                                        lastInteraction = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
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

                        // Battery & Clock Display (MX Player style)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            if (batteryLevel >= 0) {
                                Icon(
                                    imageVector = if (isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                                    contentDescription = null,
                                    tint = if (batteryLevel <= 15 && !isCharging) Color.Red else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "$batteryLevel%",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (currentTimeStr.isNotBlank()) {
                                Text(
                                    text = currentTimeStr,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        // Aspect Ratio Cycle Button (Fit / Fill / 16:9)
                        IconButton(
                            onClick = {
                                val nextMode = when (resizeMode) {
                                    VideoResizeMode.FIT -> VideoResizeMode.FILL
                                    VideoResizeMode.FILL -> VideoResizeMode.FIXED_16_9
                                    VideoResizeMode.FIXED_16_9 -> VideoResizeMode.FIT
                                }
                                resizeMode = nextMode
                                zoomScale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                hudText = context.getString(nextMode.labelRes)
                                hudIcon = when (nextMode) {
                                    VideoResizeMode.FIT -> Icons.Rounded.FitScreen
                                    VideoResizeMode.FILL -> Icons.Rounded.Crop
                                    VideoResizeMode.FIXED_16_9 -> Icons.Rounded.Fullscreen
                                }
                                coroutineScope.launch {
                                    delay(1200L)
                                    if (hudText == context.getString(nextMode.labelRes)) {
                                        hudText = null
                                        hudIcon = null
                                    }
                                }
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
                        val currentSliderValue = if (isScrubbing) scrubPosition else playbackPosition.toFloat()

                        Slider(
                            value = currentSliderValue.coerceIn(0f, videoDuration.toFloat().coerceAtLeast(1f)),
                            onValueChange = { newPosition ->
                                isScrubbing = true
                                scrubPosition = newPosition
                                lastInteraction = System.currentTimeMillis()
                            },
                            onValueChangeFinished = {
                                val targetPos = scrubPosition.toLong().coerceIn(0L, videoDuration.coerceAtLeast(1L))
                                playbackPosition = targetPos
                                exoPlayer.seekTo(targetPos)
                                isScrubbing = false
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
                            val displayTime = if (isScrubbing) scrubPosition.toLong() else playbackPosition
                            val currentStr = LocalVideoRepository.formatDuration(displayTime)
                            val durationOrRemaining = if (showRemainingTime) {
                                val rem = (videoDuration - displayTime).coerceAtLeast(0L)
                                "-${LocalVideoRepository.formatDuration(rem)}"
                            } else {
                                LocalVideoRepository.formatDuration(videoDuration)
                            }
                            Text(
                                text = "$currentStr / $durationOrRemaining",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showRemainingTime = !showRemainingTime
                                        lastInteraction = System.currentTimeMillis()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
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
                                            val rationalAspect = try {
                                                val safePipAspect = safeAspect.coerceIn(0.42f, 2.38f)
                                                val num = (safePipAspect * 1000).roundToInt()
                                                Rational(num, 1000)
                                            } catch (_: Exception) {
                                                Rational(16, 9)
                                            }
                                            val params = PictureInPictureParams.Builder()
                                                .setAspectRatio(rationalAspect)
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
                                        imageVector = Icons.Rounded.ScreenRotation,
                                        contentDescription = stringResource(R.string.screen_rotation),
                                        modifier = Modifier.graphicsLayer { rotationZ = if (isLandscape) 90f else 0f },
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
