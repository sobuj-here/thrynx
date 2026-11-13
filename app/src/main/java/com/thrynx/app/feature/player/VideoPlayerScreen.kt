package com.thrynx.app.feature.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.thrynx.app.core.model.AspectRatioMode
import com.thrynx.app.core.model.AudioTrackInfo
import com.thrynx.app.core.model.RepeatMode
import com.thrynx.app.core.model.SubtitleTrackInfo
import com.thrynx.app.core.player.ThrynxPlayerManager
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

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
    val doubleTapSeekSec by preferencesManager.doubleTapSeekSecFlow.collectAsState(initial = 10)
    val showPlayerInfoHud by preferencesManager.showPlayerInfoHudFlow.collectAsState(initial = true)

    var showControls by remember { mutableStateOf(true) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSubtitleDelayDialog by remember { mutableStateOf(false) }

    var isLandscape by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Battery and Clock Status for MX-style Player Top Bar
    var batteryPercentage by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var currentTimeString by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                if (intent?.action == android.content.Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPercentage = (level * 100) / scale
                    }
                    isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        initialIntent?.let { intent ->
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0 && scale > 0) {
                batteryPercentage = (level * 100) / scale
            }
            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (ignored: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        while (true) {
            currentTimeString = timeFormat.format(java.util.Date())
            delay(10_000)
        }
    }

    // Resize Mode
    var currentResizeMode by remember(playbackState.aspectRatioMode, defaultResizePref) {
        mutableIntStateOf(
            when (playbackState.aspectRatioMode) {
                AspectRatioMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                else -> when (defaultResizePref) {
                    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        )
    }

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

    // Fullscreen controller
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

    DisposableEffect(Unit) {
        onDispose {
            setFullscreen(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(isLandscape) {
        setFullscreen(isLandscape)
    }

    // Gesture HUD overlay state
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }

    fun showHud(text: String, icon: ImageVector) {
        hudText = text
        hudIcon = icon
    }

    // Unified Back Navigation Handler
    val handleBackPress = {
        if (playbackState.isControlsLocked) {
            showHud(context.getString(R.string.player_lock_notification), Icons.Default.Lock)
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

    BackHandler(enabled = true) {
        handleBackPress()
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

    // Capture screenshot from player surface
    fun captureScreenshot() {
        val pv = playerViewRef ?: return
        val surfaceView = pv.videoSurfaceView as? SurfaceView
        if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(surfaceView, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    try {
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val thrynxDir = File(picturesDir, "THRYNX_Screenshots").also { it.mkdirs() }
                        val file = File(thrynxDir, "THRYNX_${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        activity?.runOnUiThread {
                            Toast.makeText(context, context.getString(R.string.player_screenshot_saved), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, surfaceView.handler ?: android.os.Handler(android.os.Looper.getMainLooper()))
        } else {
            Toast.makeText(context, "Screenshot captured", Toast.LENGTH_SHORT).show()
        }
    }

    val seekDeltaMs = doubleTapSeekSec * 1000L

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Taps & Double Taps
            .pointerInput(playbackState.isControlsLocked, gesturesEnabledPref) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        if (!playbackState.isControlsLocked && gesturesEnabledPref) {
                            val third = size.width / 3f
                            when {
                                offset.x < third -> {
                                    playerManager.seekBackward(seekDeltaMs)
                                    showHud("-$doubleTapSeekSec s", Icons.Default.Replay10)
                                }
                                offset.x > third * 2 -> {
                                    playerManager.seekForward(seekDeltaMs)
                                    showHud("+$doubleTapSeekSec s", Icons.Default.Forward10)
                                }
                                else -> {
                                    playerManager.togglePlayPause()
                                    showHud(if (playbackState.isPlaying) "Pause" else "Play", if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow)
                                }
                            }
                        }
                    },
                    onLongPress = {
                        if (!playbackState.isControlsLocked) {
                            playerManager.setSpeed(2.0f)
                            showHud("2.0x Fast Play", Icons.Default.FastForward)
                        }
                    }
                )
            }
            // Vertical Drag for Brightness (left) & Volume (right), Horizontal Drag for Seek
            .pointerInput(playbackState.isControlsLocked, gesturesEnabledPref) {
                if (!playbackState.isControlsLocked && gesturesEnabledPref) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var isDragHorizontal: Boolean? = null

                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                            isDragHorizontal = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (isDragHorizontal == null) {
                                if (Math.abs(totalDragX) > 20f || Math.abs(totalDragY) > 20f) {
                                    isDragHorizontal = Math.abs(totalDragX) > Math.abs(totalDragY)
                                }
                            }

                            if (isDragHorizontal == true) {
                                // Horizontal Seek Gesture
                                val deltaSec = (dragAmount.x / 10f).toInt()
                                if (deltaSec != 0) {
                                    val newPos = (playerManager.exoPlayer.currentPosition + (deltaSec * 1000L)).coerceIn(0L, playerManager.exoPlayer.duration)
                                    playerManager.seekTo(newPos)
                                    showHud("${formatDuration(newPos)} / ${formatDuration(playerManager.exoPlayer.duration)}", Icons.Default.SwapHoriz)
                                }
                            } else if (isDragHorizontal == false) {
                                // Vertical Swipe for Brightness or Volume
                                val isLeftSide = change.position.x < size.width / 2

                                if (isLeftSide) {
                                    // Brightness
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
                                    // Volume
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
                        },
                        onDragEnd = {
                            isDragHorizontal = null
                        }
                    )
                }
            }
    ) {
        // Video Surface via AndroidView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = playbackState.rotationDegrees.toFloat()
                    scaleX = playbackState.zoomScale
                    scaleY = playbackState.zoomScale
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.exoPlayer
                    useController = false
                    this.resizeMode = currentResizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { view ->
                view.resizeMode = currentResizeMode
            }
        )

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

        // Dedicated Floating Unlock Button when screen is locked
        if (playbackState.isControlsLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.TopStart
            ) {
                FloatingActionButton(
                    onClick = {
                        playerManager.setControlsLocked(false)
                        showHud(context.getString(R.string.player_unlock_controls), Icons.Default.LockOpen)
                    },
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = ThrynxCyan,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Unlock", modifier = Modifier.size(28.dp))
                }
            }
        }

        // Overlay Controls (Top, Center, Bottom) - Hidden completely when locked
        AnimatedVisibility(
            visible = showControls && !playbackState.isControlsLocked,
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

                    // MX-style Player Status: Clock & Battery Info Chip
                    if (showPlayerInfoHud && currentTimeString.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentTimeString,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                    contentDescription = null,
                                    tint = if (batteryPercentage <= 15) Color.Red else ThrynxCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$batteryPercentage%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                            contentDescription = stringResource(id = R.string.player_audio_track),
                            tint = if (playbackState.audioTracks.size > 1) ThrynxCyan else Color.White
                        )
                    }

                    // Subtitle Selector
                    IconButton(onClick = { showSubtitleDialog = true }) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = stringResource(id = R.string.player_subtitle_track),
                            tint = if (playbackState.selectedSubtitleTrack != null) ThrynxCyan else Color.White
                        )
                    }

                    // Video Canvas Rotation (0°, 90°, 180°, 270°)
                    IconButton(onClick = {
                        playerManager.rotateVideo()
                        showHud("${playbackState.rotationDegrees}° Video Rotation", Icons.Default.RotateRight)
                    }) {
                        Icon(
                            Icons.Default.RotateRight,
                            contentDescription = stringResource(id = R.string.player_rotate_video),
                            tint = if (playbackState.rotationDegrees != 0) ThrynxCyan else Color.White
                        )
                    }

                    // Video Zoom (1x, 1.25x, 1.5x, 2x)
                    IconButton(onClick = {
                        playerManager.cycleZoom()
                        val zoomText = when (playbackState.zoomScale) {
                            1.0f -> "Zoom: 100%"
                            1.25f -> "Zoom: 125%"
                            1.5f -> "Zoom: 150%"
                            2.0f -> "Zoom: 200%"
                            else -> "Zoom: ${(playbackState.zoomScale * 100).toInt()}%"
                        }
                        showHud(zoomText, Icons.Default.ZoomIn)
                    }) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = stringResource(id = R.string.player_zoom_video),
                            tint = if (playbackState.zoomScale > 1.05f) ThrynxCyan else Color.White
                        )
                    }

                    // Screen Orientation Toggle (Landscape ⟷ Portrait ⟷ Auto)
                    IconButton(onClick = {
                        activity?.let { act ->
                            val (nextOrient, label) = when (act.requestedOrientation) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> {
                                    isLandscape = false
                                    Pair(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, "Portrait")
                                }
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> {
                                    isLandscape = true
                                    Pair(ActivityInfo.SCREEN_ORIENTATION_SENSOR, "Auto-Rotate")
                                }
                                else -> {
                                    isLandscape = true
                                    Pair(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, "Landscape")
                                }
                            }
                            act.requestedOrientation = nextOrient
                            showHud(label, Icons.Default.ScreenRotation)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = stringResource(id = R.string.player_screen_orientation),
                            tint = if (isLandscape) ThrynxCyan else Color.White
                        )
                    }

                    // Playback Speed
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(Icons.Default.Speed, contentDescription = stringResource(id = R.string.player_speed), tint = Color.White)
                    }

                    // Aspect Ratio
                    IconButton(onClick = { showAspectRatioDialog = true }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = stringResource(id = R.string.player_aspect_ratio), tint = Color.White)
                    }

                    // Sleep Timer
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = stringResource(id = R.string.player_sleep_timer),
                            tint = if (playbackState.sleepTimerMinutes > 0) ThrynxCyan else Color.White
                        )
                    }

                    // Screenshot Capture
                    IconButton(onClick = { captureScreenshot() }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(id = R.string.player_screenshot), tint = Color.White)
                    }

                    // Media Information
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(id = R.string.player_info), tint = Color.White)
                    }

                    // PiP Button
                    IconButton(onClick = onEnterPiP) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = stringResource(id = R.string.player_pip), tint = Color.White)
                    }
                }

                // Center Play/Pause & Skip Controls
                if (!playbackState.isControlsLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Previous in Playlist
                        IconButton(
                            onClick = { playerManager.playPrevious() },
                            enabled = playbackState.hasPrevious || playerManager.exoPlayer.currentPosition > 3000L,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = if (playbackState.hasPrevious || playerManager.exoPlayer.currentPosition > 3000L) Color.White else Color.Gray
                            )
                        }

                        // Rewind
                        IconButton(
                            onClick = {
                                playerManager.seekBackward(seekDeltaMs)
                                showHud("-$doubleTapSeekSec s", Icons.Default.Replay10)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Play/Pause
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

                        // Forward
                        IconButton(
                            onClick = {
                                playerManager.seekForward(seekDeltaMs)
                                showHud("+$doubleTapSeekSec s", Icons.Default.Forward10)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Next in Playlist
                        IconButton(
                            onClick = { playerManager.playNext() },
                            enabled = playbackState.hasNext || playbackState.repeatMode == RepeatMode.ALL,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = if (playbackState.hasNext || playbackState.repeatMode == RepeatMode.ALL) Color.White else Color.Gray
                            )
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
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Repeat Mode Button
                            IconButton(onClick = { playerManager.toggleRepeatMode() }) {
                                Icon(
                                    imageVector = when (playbackState.repeatMode) {
                                        RepeatMode.ONE -> Icons.Default.RepeatOne
                                        RepeatMode.ALL -> Icons.Default.Repeat
                                        RepeatMode.OFF -> Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat",
                                    tint = if (playbackState.repeatMode != RepeatMode.OFF) ThrynxCyan else Color.White
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
                                        .padding(vertical = 10.dp),
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
                                .padding(vertical = 8.dp),
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
                                    .padding(vertical = 8.dp),
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Subtitle Delay Adjuster Button
                        OutlinedButton(
                            onClick = {
                                showSubtitleDialog = false
                                showSubtitleDelayDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Subtitle Sync Delay (${playbackState.subtitleDelayMs} ms)")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Load External Subtitle File Button
                        Button(
                            onClick = { subtitlePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.player_load_external_subtitle), fontWeight = FontWeight.Bold)
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

        // Subtitle Delay Sync Dialog
        if (showSubtitleDelayDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDelayDialog = false },
                title = { Text(stringResource(id = R.string.player_subtitle_delay)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${playbackState.subtitleDelayMs} ms",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThrynxCyan
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { playerManager.setSubtitleDelay(playbackState.subtitleDelayMs - 500L) }) {
                                Text("-500ms")
                            }
                            OutlinedButton(onClick = { playerManager.setSubtitleDelay(playbackState.subtitleDelayMs - 100L) }) {
                                Text("-100ms")
                            }
                            OutlinedButton(onClick = { playerManager.setSubtitleDelay(0L) }) {
                                Text("Reset")
                            }
                            OutlinedButton(onClick = { playerManager.setSubtitleDelay(playbackState.subtitleDelayMs + 100L) }) {
                                Text("+100ms")
                            }
                            OutlinedButton(onClick = { playerManager.setSubtitleDelay(playbackState.subtitleDelayMs + 500L) }) {
                                Text("+500ms")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDelayDialog = false }) {
                        Text(stringResource(id = R.string.action_done))
                    }
                }
            )
        }

        // Playback Speed Dialog
        if (showSpeedDialog) {
            val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
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
                                    .padding(vertical = 8.dp),
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

        // Aspect Ratio Dialog (Fit, Fill/Crop, Stretch, 16:9, 4:3)
        if (showAspectRatioDialog) {
            AlertDialog(
                onDismissRequest = { showAspectRatioDialog = false },
                title = { Text(stringResource(id = R.string.player_aspect_ratio)) },
                text = {
                    Column {
                        val modes = listOf(
                            Pair(AspectRatioMode.FIT, "Fit to Screen"),
                            Pair(AspectRatioMode.FILL_CROP, "Fill Screen (Crop)"),
                            Pair(AspectRatioMode.STRETCH, "Stretch to Window"),
                            Pair(AspectRatioMode.RATIO_16_9, "16:9 Widescreen"),
                            Pair(AspectRatioMode.RATIO_4_3, "4:3 Standard")
                        )
                        modes.forEach { (mode, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerManager.setAspectRatioMode(mode)
                                        currentResizeMode = when (mode) {
                                            AspectRatioMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                            AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                        showAspectRatioDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = playbackState.aspectRatioMode == mode,
                                    onClick = {
                                        playerManager.setAspectRatioMode(mode)
                                        currentResizeMode = when (mode) {
                                            AspectRatioMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                            AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
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

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            val timerOptions = listOf(0, 10, 15, 30, 45, 60)
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text(stringResource(id = R.string.player_sleep_timer)) },
                text = {
                    Column {
                        timerOptions.forEach { mins ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (mins == 0) playerManager.cancelSleepTimer()
                                        else playerManager.startSleepTimer(mins)
                                        showSleepTimerDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = playbackState.sleepTimerMinutes == mins,
                                    onClick = {
                                        if (mins == 0) playerManager.cancelSleepTimer()
                                        else playerManager.startSleepTimer(mins)
                                        showSleepTimerDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (mins == 0) stringResource(id = R.string.player_sleep_timer_off)
                                    else stringResource(id = R.string.player_sleep_timer_mins, mins)
                                )
                            }
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

        // Video Information Dialog
        if (showInfoDialog) {
            val currentMedia = playbackState.currentMedia
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text(stringResource(id = R.string.player_info)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Title: ${currentMedia?.title ?: "Unknown"}", fontWeight = FontWeight.Bold)
                        Text("Duration: ${formatDuration(playbackState.durationMs)}")
                        if (currentMedia != null && currentMedia.width > 0 && currentMedia.height > 0) {
                            Text("Resolution: ${currentMedia.width} x ${currentMedia.height} (${currentMedia.resolutionLabel})")
                        }
                        if (currentMedia != null && currentMedia.sizeBytes > 0) {
                            Text("File Size: ${formatFileSize(currentMedia.sizeBytes)}")
                        }
                        Text("Format: ${currentMedia?.mimeType ?: "video/mp4"}")
                        Text("Audio Tracks: ${playbackState.audioTracks.size}")
                        Text("Subtitle Tracks: ${playbackState.subtitleTracks.size}")
                        Text("Speed: ${playbackState.playbackSpeed}x")
                        Text("Display Mode: ${playbackState.aspectRatioMode.label}")
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
