package com.thrynx.app.feature.editor

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.editor.audio.AudioStudioEngine
import com.thrynx.app.core.editor.effects.EffectsEngine
import com.thrynx.app.core.editor.export.ExportConfiguration
import com.thrynx.app.core.editor.preview.PreviewEngine
import com.thrynx.app.core.editor.timeline.TimelineEngine
import com.thrynx.app.core.media.AudioExtractor
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.model.*
import com.thrynx.app.feature.editor.export.ExportSettingsDialog
import com.thrynx.app.feature.home.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    initialProject: ProjectModel,
    onSaveProject: (ProjectModel) -> Unit,
    onExportProject: (ProjectModel, ExportConfiguration) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { MediaStoreScanner(context) }
    val audioStudio = remember { AudioStudioEngine(context) }

    val timelineEngine = remember { TimelineEngine(initialProject) }
    var currentProject by remember { mutableStateOf(timelineEngine.getProject()) }
    val previewEngine = remember { PreviewEngine() }

    // Dedicated Real Video Frame Decoded Player for Live Preview (Phase 2 requirement)
    val editorExoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            editorExoPlayer.release()
            previewEngine.release()
        }
    }

    LaunchedEffect(currentProject) {
        previewEngine.setProject(currentProject)
        onSaveProject(currentProject)
    }

    val isPlaying by previewEngine.isPlaying.collectAsState()
    val playheadMs by previewEngine.playheadMs.collectAsState()
    val snapshot by previewEngine.snapshot.collectAsState()

    var selectedTrackId by remember { mutableStateOf<String?>(null) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var timelineZoom by remember { mutableFloatStateOf(1.0f) }

    // Dialog Sheets & States
    var showFilterSheet by remember { mutableStateOf(false) }
    var showTransitionSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showEditTextDialog by remember { mutableStateOf(false) }
    var showVoiceRecordDialog by remember { mutableStateOf(false) }
    var showCanvasRatioDialog by remember { mutableStateOf(false) }
    var showAudioStudioSheet by remember { mutableStateOf(false) }
    var showTransformSheet by remember { mutableStateOf(false) }
    var showKeyframeSheet by remember { mutableStateOf(false) }
    var showExportSettingsDialog by remember { mutableStateOf(false) }
    var isExtractingAudio by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableIntStateOf(0) }

    val selectedTrack = remember(currentProject, selectedTrackId) {
        currentProject.tracks.firstOrNull { it.id == selectedTrackId }
    }

    val selectedClip = remember(currentProject, selectedTrackId, selectedClipId) {
        selectedTrack?.clips?.firstOrNull { it.id == selectedClipId }
    }

    // Synchronize Real ExoPlayer with Live Preview Engine (Actual video frames)
    val activeVideo = snapshot?.activeVideoClips?.firstOrNull()
    val isImageClip = activeVideo?.trackType == TrackType.IMAGE ||
            (activeVideo != null && (activeVideo.clip.mediaUri.endsWith(".jpg", true) ||
                    activeVideo.clip.mediaUri.endsWith(".jpeg", true) ||
                    activeVideo.clip.mediaUri.endsWith(".png", true) ||
                    activeVideo.clip.mediaUri.endsWith(".webp", true)))

    // Update media item when URI changes
    LaunchedEffect(activeVideo?.clip?.mediaUri, isImageClip) {
        if (!isImageClip) {
            val uriStr = activeVideo?.clip?.mediaUri
            if (!uriStr.isNullOrBlank()) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(uriStr))
                    .setMediaId(activeVideo.clip.id)
                    .build()
                editorExoPlayer.setMediaItem(mediaItem, activeVideo.sourcePositionMs)
                editorExoPlayer.prepare()
                if (isPlaying) editorExoPlayer.play() else editorExoPlayer.pause()
            } else {
                editorExoPlayer.stop()
                editorExoPlayer.clearMediaItems()
            }
        } else {
            editorExoPlayer.stop()
            editorExoPlayer.clearMediaItems()
        }
    }

    // Synchronize playback position & play/pause state
    LaunchedEffect(activeVideo?.sourcePositionMs, isPlaying, isImageClip) {
        if (activeVideo != null && !isImageClip) {
            if (isPlaying) {
                if (!editorExoPlayer.isPlaying) editorExoPlayer.play()
            } else {
                if (editorExoPlayer.isPlaying) editorExoPlayer.pause()
                if (Math.abs(editorExoPlayer.currentPosition - activeVideo.sourcePositionMs) > 60L) {
                    editorExoPlayer.seekTo(activeVideo.sourcePositionMs)
                }
            }
        }
    }

    // Synchronize playback speed
    LaunchedEffect(activeVideo?.clip?.speedFactor) {
        val spd = activeVideo?.clip?.speedFactor ?: 1.0f
        editorExoPlayer.setPlaybackSpeed(spd)
    }

    // Synchronize volume
    LaunchedEffect(activeVideo?.activeVolume) {
        val vol = activeVideo?.activeVolume ?: 1.0f
        editorExoPlayer.volume = vol
    }

    // Media Import Launcher (video, audio, or photo)
    val mediaImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val resolved = scanner.resolveMetadataFromUri(uri)
                if (resolved != null) {
                    val targetTrackType = if (resolved.isVideo) TrackType.VIDEO else TrackType.AUDIO
                    val track = currentProject.tracks.firstOrNull { it.type == targetTrackType }
                        ?: TimelineTrackModel(
                            id = UUID.randomUUID().toString(),
                            type = targetTrackType,
                            index = currentProject.tracks.size
                        ).also { newTrack ->
                            currentProject = currentProject.copy(tracks = currentProject.tracks + newTrack)
                        }

                    val newClip = TimelineClipModel(
                        id = UUID.randomUUID().toString(),
                        mediaUri = resolved.uri.toString(),
                        name = resolved.title,
                        sourceStartMs = 0L,
                        sourceEndMs = resolved.durationMs.coerceAtLeast(3000L),
                        timelineStartMs = playheadMs,
                        durationMs = resolved.durationMs.coerceAtLeast(3000L)
                    )
                    currentProject = timelineEngine.addClipToTrack(track.id, newClip)
                    selectedTrackId = track.id
                    selectedClipId = newClip.id
                }
            }
        }
    }

    // Image/Photo Import Launcher
    val photoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val imageTrack = currentProject.tracks.firstOrNull { it.type == TrackType.IMAGE }
                    ?: TimelineTrackModel(
                        id = "track_images",
                        type = TrackType.IMAGE,
                        index = currentProject.tracks.size
                    ).also { newTrack ->
                        currentProject = currentProject.copy(tracks = currentProject.tracks + newTrack)
                    }

                val imageClip = TimelineClipModel(
                    id = UUID.randomUUID().toString(),
                    mediaUri = uri.toString(),
                    name = "Photo_${System.currentTimeMillis() % 10000}",
                    sourceStartMs = 0L,
                    sourceEndMs = 4000L,
                    timelineStartMs = playheadMs,
                    durationMs = 4000L
                )
                currentProject = timelineEngine.addClipToTrack(imageTrack.id, imageClip)
                selectedTrackId = imageTrack.id
                selectedClipId = imageClip.id
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ThrynxBgDark)
    ) {
        // Top Studio Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = currentProject.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Undo
            IconButton(
                onClick = {
                    timelineEngine.undo()?.let { currentProject = it }
                },
                enabled = timelineEngine.canUndo()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (timelineEngine.canUndo()) Color.White else Color.Gray
                )
            }

            // Redo
            IconButton(
                onClick = {
                    timelineEngine.redo()?.let { currentProject = it }
                },
                enabled = timelineEngine.canRedo()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (timelineEngine.canRedo()) Color.White else Color.Gray
                )
            }

            // Canvas Aspect Ratio
            IconButton(onClick = { showCanvasRatioDialog = true }) {
                Icon(Icons.Default.AspectRatio, contentDescription = "Ratio", tint = ThrynxCyan)
            }

            // Export Button
            Button(
                onClick = { showExportSettingsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.editor_export_button),
                    color = ThrynxBgDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Preview Canvas Area: Real Video Frame Surface & Image Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratioFloat = currentProject.canvasRatio.widthRatio / currentProject.canvasRatio.heightRatio

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(ratioFloat, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThrynxSurfaceDark)
                    .border(1.dp, ThrynxCardStrokeDark, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (activeVideo != null && activeVideo.clip.mediaUri.isNotBlank()) {
                    val transform = activeVideo.transform
                    val transition = activeVideo.transitionState

                    // Combined visual transform with active keyframes and transitions
                    val liveScaleX = transform.scaleX * (transition?.incomingScale ?: 1f)
                    val liveScaleY = transform.scaleY * (transition?.incomingScale ?: 1f)
                    val liveAlpha = transform.opacity * (transition?.incomingAlpha ?: 1f)
                    val liveTransX = transform.translationX + ((transition?.incomingTranslationX ?: 0f) * 100f)
                    val liveTransY = transform.translationY

                    if (isImageClip) {
                        // Real Decoded Image Frame
                        val colorFilter = if (activeVideo.clip.effects.isNotEmpty()) {
                            ColorFilter.colorMatrix(EffectsEngine.buildColorMatrix(activeVideo.clip.effects))
                        } else null

                        AsyncImage(
                            model = activeVideo.clip.mediaUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = liveTransX
                                    translationY = liveTransY
                                    scaleX = liveScaleX
                                    scaleY = liveScaleY
                                    rotationZ = transform.rotation
                                    alpha = liveAlpha
                                }
                        )
                    } else {
                        // Real Decoded Video Frame Surface
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = liveTransX
                                    translationY = liveTransY
                                    scaleX = liveScaleX
                                    scaleY = liveScaleY
                                    rotationZ = transform.rotation
                                    alpha = liveAlpha
                                }
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = editorExoPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { view ->
                                    view.player = editorExoPlayer
                                }
                            )
                        }
                    }
                } else {
                    // Empty Timeline Indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = ThrynxTextMutedDark, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add clips to preview composition",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThrynxTextMutedDark
                        )
                    }
                }

                // Render Active Text & Subtitle Layers on top of video frames
                snapshot?.activeTextClips?.forEach { textClip ->
                    val transform = textClip.transform
                    val isSubtitle = textClip.clip.isSubtitle
                    val alignModifier = if (isSubtitle) Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp) else Modifier

                    Box(
                        modifier = alignModifier
                            .offset(x = transform.translationX.dp, y = transform.translationY.dp)
                            .graphicsLayer {
                                scaleX = transform.scaleX
                                scaleY = transform.scaleY
                                rotationZ = transform.rotation
                                alpha = transform.opacity
                            }
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = if (isSubtitle) 0.65f else 0.45f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = textClip.clip.textContent,
                            color = try {
                                Color(android.graphics.Color.parseColor(textClip.clip.textColorHex))
                            } catch (_: Exception) { Color.White },
                            fontSize = textClip.clip.textFontSizeSp.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Playhead Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatDuration(playheadMs)} / ${formatDuration(currentProject.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Zoom Out
                IconButton(
                    onClick = { timelineZoom = (timelineZoom - 0.25f).coerceAtLeast(0.5f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Toggle
                IconButton(
                    onClick = { previewEngine.togglePlayPause() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ThrynxCyan)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = ThrynxBgDark
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Zoom In
                IconButton(
                    onClick = { timelineZoom = (timelineZoom + 0.25f).coerceAtMost(3.0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Text(
                text = "${currentProject.canvasRatio.label.take(4)} • ${currentProject.fps} FPS",
                style = MaterialTheme.typography.labelSmall,
                color = ThrynxTextSecondaryDark
            )
        }

        // Multi-Track Timeline View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(ThrynxSurfaceDark)
                .border(1.dp, ThrynxCardStrokeDark)
        ) {
            val scrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(8.dp)
            ) {
                // Tracks Container
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentProject.tracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .background(ThrynxTimelineTrack, RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Track Mute & Lock controls
                            IconButton(
                                onClick = { currentProject = timelineEngine.toggleTrackMute(track.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = if (track.isMuted) ThrynxCoral else ThrynxTextSecondaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = track.type.name.take(3),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThrynxCyan,
                                modifier = Modifier.width(28.dp)
                            )

                            if (track.clips.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .fillMaxHeight()
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Empty", fontSize = 10.sp, color = ThrynxTextMutedDark)
                                }
                            } else {
                                track.clips.forEach { clip ->
                                    val isClipSelected = selectedClipId == clip.id
                                    val clipWidth = (((clip.durationMs / 40L) * timelineZoom).toInt()).coerceIn(60, 600).dp

                                    Box(
                                        modifier = Modifier
                                            .width(clipWidth)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isClipSelected) ThrynxCyan
                                                else when (track.type) {
                                                    TrackType.VIDEO -> ThrynxCobalt
                                                    TrackType.AUDIO -> ThrynxPurple
                                                    TrackType.IMAGE -> ThrynxCoral
                                                    TrackType.TEXT, TrackType.SUBTITLE -> ThrynxEmerald
                                                    else -> ThrynxSurfaceLight
                                                }
                                            )
                                            .clickable {
                                                selectedTrackId = track.id
                                                selectedClipId = clip.id
                                            }
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = clip.name.ifBlank { clip.textContent.ifBlank { "Clip" } },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isClipSelected) ThrynxBgDark else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            // Waveform Indicator on Audio Clips
                                            if (track.type == TrackType.AUDIO) {
                                                Icon(
                                                    Icons.Default.GraphicEq,
                                                    contentDescription = "Waveform",
                                                    tint = if (isClipSelected) ThrynxBgDark else Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }

                                            // Keyframe Indicator
                                            if (clip.keyframes.isNotEmpty()) {
                                                Icon(
                                                    Icons.Default.Diamond,
                                                    contentDescription = "Keyframe",
                                                    tint = if (isClipSelected) ThrynxBgDark else ThrynxCyan,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Playhead Needle Overlay
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .offset(x = 100.dp)
                    .background(ThrynxCyan)
            )
        }

        // Action Toolbar (CapCut-grade professional controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .background(ThrynxCardDark)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Add Media Clip
            EditorToolButton(
                icon = Icons.Default.AddPhotoAlternate,
                title = "Add Video",
                enabled = true,
                onClick = { mediaImportLauncher.launch("video/*") }
            )

            // Add Photo Clip
            EditorToolButton(
                icon = Icons.Default.PhotoLibrary,
                title = "Add Photo",
                enabled = true,
                onClick = { photoImportLauncher.launch("image/*") }
            )

            // Split Action
            EditorToolButton(
                icon = Icons.AutoMirrored.Filled.CallSplit,
                title = stringResource(id = R.string.editor_split),
                enabled = selectedClip != null,
                onClick = {
                    selectedTrackId?.let { tId ->
                        selectedClipId?.let { cId ->
                            currentProject = timelineEngine.splitClipAtPlayhead(tId, cId, playheadMs)
                        }
                    }
                }
            )

            // Extract Audio from Selected Video Clip
            EditorToolButton(
                icon = Icons.Default.AudioFile,
                title = "Extract Audio",
                enabled = selectedClip != null && selectedClip.mediaUri.isNotBlank(),
                onClick = {
                    selectedClip?.let { clip ->
                        coroutineScope.launch {
                            isExtractingAudio = true
                            extractProgress = 0
                            val outputFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.m4a")
                            val result = AudioExtractor.extractAudioFromVideo(
                                context = context,
                                videoUri = Uri.parse(clip.mediaUri),
                                outputFile = outputFile,
                                onProgress = { extractProgress = it }
                            )
                            isExtractingAudio = false

                            result.onSuccess { file ->
                                val targetAudioTrack = currentProject.tracks.firstOrNull { it.type == TrackType.AUDIO }
                                    ?: TimelineTrackModel(
                                        id = "track_audio_extracted",
                                        type = TrackType.AUDIO,
                                        index = currentProject.tracks.size
                                    ).also { newTrack ->
                                        currentProject = currentProject.copy(tracks = currentProject.tracks + newTrack)
                                    }

                                // Mute original video clip
                                val mutedClip = clip.copy(volume = 0f)
                                currentProject = currentProject.copy(
                                    tracks = currentProject.tracks.map { track ->
                                        if (track.id == selectedTrackId) {
                                            track.copy(clips = track.clips.map { if (it.id == clip.id) mutedClip else it })
                                        } else track
                                    }
                                )

                                // Insert extracted audio clip at the exact same timeline start time
                                val extractedAudioClip = TimelineClipModel(
                                    id = UUID.randomUUID().toString(),
                                    mediaUri = Uri.fromFile(file).toString(),
                                    name = "${clip.name} (Audio)",
                                    sourceStartMs = 0L,
                                    sourceEndMs = clip.durationMs,
                                    timelineStartMs = clip.timelineStartMs,
                                    durationMs = clip.durationMs
                                )
                                currentProject = timelineEngine.addClipToTrack(targetAudioTrack.id, extractedAudioClip)
                            }
                        }
                    }
                }
            )

            // Transform (Rotate, Flip, Scale, Opacity)
            EditorToolButton(
                icon = Icons.Default.CropRotate,
                title = "Transform",
                enabled = selectedClip != null,
                onClick = { showTransformSheet = true }
            )

            // Keyframe Action & Sheet
            EditorToolButton(
                icon = Icons.Default.Diamond,
                title = "Keyframes",
                enabled = selectedClip != null,
                onClick = { showKeyframeSheet = true }
            )

            // Speed Action
            EditorToolButton(
                icon = Icons.Default.Speed,
                title = stringResource(id = R.string.editor_speed),
                enabled = selectedClip != null,
                onClick = { showSpeedDialog = true }
            )

            // Audio Studio (Volume, Mute, Fade In/Out)
            EditorToolButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Audio Studio",
                enabled = selectedClip != null,
                onClick = { showAudioStudioSheet = true }
            )

            // Snap Clip to Playhead
            EditorToolButton(
                icon = Icons.Default.VerticalAlignBottom,
                title = "Move Here",
                enabled = selectedClip != null,
                onClick = {
                    selectedTrackId?.let { tId ->
                        selectedClipId?.let { cId ->
                            currentProject = timelineEngine.moveClip(tId, cId, playheadMs)
                        }
                    }
                }
            )

            // Duplicate Action
            EditorToolButton(
                icon = Icons.Default.ContentCopy,
                title = stringResource(id = R.string.editor_duplicate),
                enabled = selectedClip != null,
                onClick = {
                    selectedTrackId?.let { tId ->
                        selectedClipId?.let { cId ->
                            currentProject = timelineEngine.duplicateClip(tId, cId)
                        }
                    }
                }
            )

            // Delete Action
            EditorToolButton(
                icon = Icons.Default.Delete,
                title = stringResource(id = R.string.editor_delete),
                enabled = selectedClip != null,
                onClick = {
                    selectedTrackId?.let { tId ->
                        selectedClipId?.let { cId ->
                            currentProject = timelineEngine.deleteClip(tId, cId)
                            selectedClipId = null
                        }
                    }
                }
            )

            // Filters & Color Grading
            EditorToolButton(
                icon = Icons.Default.FilterVintage,
                title = stringResource(id = R.string.editor_filters),
                enabled = selectedClip != null,
                onClick = { showFilterSheet = true }
            )

            // Transitions
            EditorToolButton(
                icon = Icons.Default.Transform,
                title = stringResource(id = R.string.editor_transitions),
                enabled = selectedClip != null,
                onClick = { showTransitionSheet = true }
            )

            // Text & Subtitles
            EditorToolButton(
                icon = Icons.Default.TextFields,
                title = if (selectedClip?.textContent?.isNotBlank() == true) "Edit Text" else "Add Text",
                enabled = true,
                onClick = {
                    if (selectedClip?.textContent?.isNotBlank() == true) {
                        showEditTextDialog = true
                    } else {
                        showAddTextDialog = true
                    }
                }
            )

            // Voice-Over Recording
            EditorToolButton(
                icon = Icons.Default.Mic,
                title = stringResource(id = R.string.audio_voiceover),
                enabled = true,
                onClick = { showVoiceRecordDialog = true }
            )
        }

        // ==========================================
        // DIALOGS & ACTION SHEETS
        // ==========================================

        // 1. Transform Sheet (Rotate 90°, Flip H, Flip V, Opacity)
        if (showTransformSheet && selectedClip != null) {
            AlertDialog(
                onDismissRequest = { showTransformSheet = false },
                title = { Text("Transform Clip") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(onClick = {
                                selectedTrackId?.let { tId ->
                                    selectedClipId?.let { cId ->
                                        currentProject = timelineEngine.rotateClip90(tId, cId)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.RotateRight, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate 90°")
                            }

                            OutlinedButton(onClick = {
                                selectedTrackId?.let { tId ->
                                    selectedClipId?.let { cId ->
                                        currentProject = timelineEngine.flipClipHorizontal(tId, cId)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Flip, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flip H")
                            }

                            OutlinedButton(onClick = {
                                selectedTrackId?.let { tId ->
                                    selectedClipId?.let { cId ->
                                        currentProject = timelineEngine.flipClipVertical(tId, cId)
                                    }
                                }
                            }) {
                                Text("Flip V")
                            }
                        }

                        var opacityVal by remember { mutableFloatStateOf(selectedClip.transform.opacity) }
                        Text("Opacity: ${(opacityVal * 100).toInt()}%", fontWeight = FontWeight.Bold)
                        Slider(
                            value = opacityVal,
                            onValueChange = {
                                opacityVal = it
                                selectedTrackId?.let { tId ->
                                    selectedClipId?.let { cId ->
                                        currentProject = timelineEngine.updateClipOpacity(tId, cId, it)
                                    }
                                }
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showTransformSheet = false }) {
                        Text(stringResource(id = R.string.action_ok))
                    }
                }
            )
        }

        // 2. Audio Studio Sheet (Volume, Fade In/Out, Mute)
        if (showAudioStudioSheet && selectedClip != null) {
            var currentVol by remember { mutableFloatStateOf(selectedClip.volume) }
            var currentFadeIn by remember { mutableLongStateOf(selectedClip.fadeInMs) }
            var currentFadeOut by remember { mutableLongStateOf(selectedClip.fadeOutMs) }

            AlertDialog(
                onDismissRequest = { showAudioStudioSheet = false },
                title = { Text("Audio Studio") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Volume: ${(currentVol * 100).toInt()}%", fontWeight = FontWeight.Bold)
                        Slider(
                            value = currentVol,
                            onValueChange = { currentVol = it },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                        )

                        Text("Fade In: ${currentFadeIn / 1000f}s", fontWeight = FontWeight.Bold)
                        Slider(
                            value = currentFadeIn.toFloat(),
                            onValueChange = { currentFadeIn = it.toLong() },
                            valueRange = 0f..4000f,
                            colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                        )

                        Text("Fade Out: ${currentFadeOut / 1000f}s", fontWeight = FontWeight.Bold)
                        Slider(
                            value = currentFadeOut.toFloat(),
                            onValueChange = { currentFadeOut = it.toLong() },
                            valueRange = 0f..4000f,
                            colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        selectedTrackId?.let { tId ->
                            selectedClipId?.let { cId ->
                                currentProject = timelineEngine.updateClipVolume(tId, cId, currentVol)
                                currentProject = timelineEngine.updateAudioFade(tId, cId, currentFadeIn, currentFadeOut)
                            }
                        }
                        showAudioStudioSheet = false
                    }) {
                        Text(stringResource(id = R.string.action_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAudioStudioSheet = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 3. Keyframe Sheet (Add at playhead, list and delete)
        if (showKeyframeSheet && selectedClip != null) {
            AlertDialog(
                onDismissRequest = { showKeyframeSheet = false },
                title = { Text("Keyframe Animation Studio") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                selectedTrackId?.let { tId ->
                                    selectedClipId?.let { cId ->
                                        val kf = KeyframeModel(
                                            timestampMs = playheadMs,
                                            scaleX = selectedClip.transform.scaleX,
                                            scaleY = selectedClip.transform.scaleY,
                                            rotation = selectedClip.transform.rotation,
                                            opacity = selectedClip.transform.opacity
                                        )
                                        currentProject = timelineEngine.addKeyframeToClip(tId, cId, kf)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ThrynxBgDark)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Keyframe at Playhead", color = ThrynxBgDark, fontWeight = FontWeight.Bold)
                        }

                        if (selectedClip.keyframes.isEmpty()) {
                            Text("No keyframes on this clip yet.", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            Text("Existing Keyframes (${selectedClip.keyframes.size}):", fontWeight = FontWeight.Bold)
                            selectedClip.keyframes.forEach { kf ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${formatDuration(kf.timestampMs)} — Scale: ${kf.scaleX}x, Rot: ${kf.rotation}°", fontSize = 11.sp)
                                    IconButton(
                                        onClick = {
                                            selectedTrackId?.let { tId ->
                                                selectedClipId?.let { cId ->
                                                    currentProject = timelineEngine.removeKeyframeFromClip(tId, cId, kf.id)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ThrynxCoral, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showKeyframeSheet = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 4. Voice Recording Dialog
        if (showVoiceRecordDialog) {
            var isRecordingActive by remember { mutableStateOf(false) }
            var recordDurationSeconds by remember { mutableIntStateOf(0) }
            var recordedOutputFile by remember { mutableStateOf<File?>(null) }

            LaunchedEffect(isRecordingActive) {
                if (isRecordingActive) {
                    recordDurationSeconds = 0
                    while (isRecordingActive) {
                        delay(1000)
                        recordDurationSeconds++
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    if (isRecordingActive) {
                        audioStudio.stopVoiceRecording()
                    }
                    showVoiceRecordDialog = false
                },
                title = { Text(stringResource(id = R.string.audio_voiceover)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecordingActive) ThrynxCoral else ThrynxCyan
                        )

                        Text(
                            text = if (isRecordingActive) "Recording microphone audio…" else "Ready to record voice-over at playhead",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                if (!isRecordingActive) {
                                    val outFile = File(context.cacheDir, "voiceover_${System.currentTimeMillis()}.m4a")
                                    recordedOutputFile = outFile
                                    val started = audioStudio.startVoiceRecording(outFile)
                                    if (started) isRecordingActive = true
                                } else {
                                    val finishedFile = audioStudio.stopVoiceRecording()
                                    isRecordingActive = false
                                    recordedOutputFile = finishedFile
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecordingActive) ThrynxCoral else ThrynxCyan
                            )
                        ) {
                            Icon(
                                imageVector = if (isRecordingActive) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = ThrynxBgDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecordingActive) "Stop Recording" else "Start Recording",
                                color = ThrynxBgDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            recordedOutputFile?.let { file ->
                                val recDurationMs = (recordDurationSeconds * 1000L).coerceAtLeast(1000L)
                                val audioTrack = currentProject.tracks.firstOrNull { it.type == TrackType.AUDIO }
                                    ?: TimelineTrackModel(
                                        id = "track_audio_voiceover",
                                        type = TrackType.AUDIO,
                                        index = currentProject.tracks.size
                                    ).also { newTrack ->
                                        currentProject = currentProject.copy(tracks = currentProject.tracks + newTrack)
                                    }

                                val voiceClip = TimelineClipModel(
                                    id = UUID.randomUUID().toString(),
                                    mediaUri = Uri.fromFile(file).toString(),
                                    name = "Voice-over (${recordDurationSeconds}s)",
                                    sourceStartMs = 0L,
                                    sourceEndMs = recDurationMs,
                                    timelineStartMs = playheadMs,
                                    durationMs = recDurationMs
                                )
                                currentProject = timelineEngine.addClipToTrack(audioTrack.id, voiceClip)
                            }
                            showVoiceRecordDialog = false
                        },
                        enabled = recordedOutputFile != null && !isRecordingActive
                    ) {
                        Text("Add to Timeline")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (isRecordingActive) audioStudio.stopVoiceRecording()
                        showVoiceRecordDialog = false
                    }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 5. Filter / Effects Dialog
        if (showFilterSheet) {
            AlertDialog(
                onDismissRequest = { showFilterSheet = false },
                title = { Text(stringResource(id = R.string.editor_filters)) },
                text = {
                    Column {
                        EffectType.values().forEach { effectType ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTrackId?.let { tId ->
                                            selectedClipId?.let { cId ->
                                                val updatedEffect = EffectModel(type = effectType, intensity = 0.7f)
                                                currentProject = timelineEngine.applyEffectToClip(tId, cId, updatedEffect)
                                            }
                                        }
                                        showFilterSheet = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = ThrynxCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(effectType.displayName)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterSheet = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 6. Speed Dialog
        if (showSpeedDialog) {
            val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text(stringResource(id = R.string.editor_speed)) },
                text = {
                    Column {
                        speedOptions.forEach { spd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTrackId?.let { tId ->
                                            selectedClipId?.let { cId ->
                                                currentProject = timelineEngine.setClipSpeed(tId, cId, spd)
                                            }
                                        }
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedClip?.speedFactor == spd,
                                    onClick = {
                                        selectedTrackId?.let { tId ->
                                            selectedClipId?.let { cId ->
                                                currentProject = timelineEngine.setClipSpeed(tId, cId, spd)
                                            }
                                        }
                                        showSpeedDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${spd}x")
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

        // 7. Transition Selection Dialog
        if (showTransitionSheet) {
            AlertDialog(
                onDismissRequest = { showTransitionSheet = false },
                title = { Text(stringResource(id = R.string.editor_transitions)) },
                text = {
                    Column {
                        TransitionType.values().forEach { transType ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTrackId?.let { tId ->
                                            selectedClipId?.let { cId ->
                                                val trans = TransitionModel(type = transType, durationMs = 600L)
                                                val updatedTracks = currentProject.tracks.map { track ->
                                                    if (track.id == tId) {
                                                        track.copy(clips = track.clips.map { clip ->
                                                            if (clip.id == cId) clip.copy(transitionIn = trans)
                                                            else clip
                                                        })
                                                    } else track
                                                }
                                                currentProject = currentProject.copy(tracks = updatedTracks)
                                            }
                                        }
                                        showTransitionSheet = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Transform, contentDescription = null, tint = ThrynxCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(transType.displayName)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTransitionSheet = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 8. Canvas Aspect Ratio Dialog
        if (showCanvasRatioDialog) {
            AlertDialog(
                onDismissRequest = { showCanvasRatioDialog = false },
                title = { Text(stringResource(id = R.string.editor_canvas_ratio)) },
                text = {
                    Column {
                        CanvasAspectRatio.values().forEach { ratio ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentProject = currentProject.copy(canvasRatio = ratio)
                                        showCanvasRatioDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentProject.canvasRatio == ratio,
                                    onClick = {
                                        currentProject = currentProject.copy(canvasRatio = ratio)
                                        showCanvasRatioDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(ratio.label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCanvasRatioDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 9. Add Text Dialog
        if (showAddTextDialog) {
            var textInput by remember { mutableStateOf("") }
            var isSubtitleMode by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddTextDialog = false },
                title = { Text("Add Text / বাংলা সাবটাইটেল") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Enter text (English or বাংলা)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isSubtitleMode, onCheckedChange = { isSubtitleMode = it })
                            Text("Create as bottom subtitle overlay", fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                val textClip = TimelineClipModel(
                                    name = textInput.take(15),
                                    textContent = textInput,
                                    durationMs = 4000L,
                                    timelineStartMs = playheadMs,
                                    isSubtitle = isSubtitleMode
                                )
                                val textTrackType = if (isSubtitleMode) TrackType.SUBTITLE else TrackType.TEXT
                                val textTrack = currentProject.tracks.firstOrNull { it.type == textTrackType }
                                    ?: TimelineTrackModel(
                                        id = "track_text_${System.currentTimeMillis()}",
                                        type = textTrackType,
                                        index = currentProject.tracks.size
                                    ).also { newTrack ->
                                        currentProject = currentProject.copy(tracks = currentProject.tracks + newTrack)
                                    }
                                currentProject = timelineEngine.addClipToTrack(textTrack.id, textClip)
                            }
                            showAddTextDialog = false
                        }
                    ) {
                        Text(stringResource(id = R.string.action_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTextDialog = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 10. Edit Text Dialog
        if (showEditTextDialog && selectedClip != null) {
            var textInput by remember { mutableStateOf(selectedClip.textContent) }
            var fontSizeVal by remember { mutableFloatStateOf(selectedClip.textFontSizeSp) }
            val colorOptions = listOf("#FFFFFF", "#FFFF00", "#00F0FF", "#FF5370", "#00E676")

            AlertDialog(
                onDismissRequest = { showEditTextDialog = false },
                title = { Text("Edit Text & Styling") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Text Content") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Font Size: ${fontSizeVal.toInt()} sp", fontWeight = FontWeight.Bold)
                        Slider(
                            value = fontSizeVal,
                            onValueChange = { fontSizeVal = it },
                            valueRange = 12f..64f,
                            colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                        )

                        Text("Text Color:", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorOptions.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .clickable {
                                            selectedTrackId?.let { tId ->
                                                selectedClipId?.let { cId ->
                                                    currentProject = timelineEngine.updateTextProperties(
                                                        tId, cId, textInput, hex, "#00000000", fontSizeVal, selectedClip.isSubtitle
                                                    )
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        selectedTrackId?.let { tId ->
                            selectedClipId?.let { cId ->
                                currentProject = timelineEngine.updateTextProperties(
                                    tId, cId, textInput, selectedClip.textColorHex, selectedClip.textBackgroundColorHex, fontSizeVal, selectedClip.isSubtitle
                                )
                            }
                        }
                        showEditTextDialog = false
                    }) {
                        Text(stringResource(id = R.string.action_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditTextDialog = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 11. Audio Extraction Progress Dialog
        if (isExtractingAudio) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Extracting Audio") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { (extractProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = ThrynxCyan
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Extracting audio stream: $extractProgress%")
                    }
                },
                confirmButton = {}
            )
        }
        // 12. Export Studio Settings Dialog (Phase 3)
        if (showExportSettingsDialog) {
            ExportSettingsDialog(
                project = currentProject,
                onDismissRequest = { showExportSettingsDialog = false },
                onStartExport = { config ->
                    showExportSettingsDialog = false
                    onExportProject(currentProject, config)
                }
            )
        }
    }
}

@Composable
fun EditorToolButton(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (enabled) ThrynxCyan else ThrynxTextMutedDark,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color.White else ThrynxTextMutedDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
