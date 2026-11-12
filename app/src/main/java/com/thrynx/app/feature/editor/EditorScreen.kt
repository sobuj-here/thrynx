package com.thrynx.app.feature.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.editor.audio.AudioStudioEngine
import com.thrynx.app.core.editor.effects.EffectsEngine
import com.thrynx.app.core.editor.preview.PreviewEngine
import com.thrynx.app.core.editor.timeline.TimelineEngine
import com.thrynx.app.core.media.AudioExtractor
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.model.*
import com.thrynx.app.feature.home.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun EditorScreen(
    initialProject: ProjectModel,
    onSaveProject: (ProjectModel) -> Unit,
    onExportProject: (ProjectModel) -> Unit,
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

    LaunchedEffect(currentProject) {
        previewEngine.setProject(currentProject)
        onSaveProject(currentProject)
    }

    val isPlaying by previewEngine.isPlaying.collectAsState()
    val playheadMs by previewEngine.playheadMs.collectAsState()
    val snapshot by previewEngine.snapshot.collectAsState()

    var selectedTrackId by remember { mutableStateOf<String?>(null) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }

    // Dialog sheets
    var showFilterSheet by remember { mutableStateOf(false) }
    var showTransitionSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showVoiceRecordDialog by remember { mutableStateOf(false) }
    var showCanvasRatioDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var isExtractingAudio by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableStateOf(0) }

    val selectedClip = remember(currentProject, selectedTrackId, selectedClipId) {
        currentProject.tracks.firstOrNull { it.id == selectedTrackId }
            ?.clips?.firstOrNull { it.id == selectedClipId }
    }

    // Media Import Launcher (add video, audio, or image)
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

    // Video Audio Extraction Launcher
    val audioExtractPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isExtractingAudio = true
                extractProgress = 0
                val outputFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.m4a")
                val result = AudioExtractor.extractAudioFromVideo(
                    context = context,
                    videoUri = uri,
                    outputFile = outputFile,
                    onProgress = { extractProgress = it }
                )
                isExtractingAudio = false

                result.onSuccess { extractedFile ->
                    val resolved = scanner.resolveMetadataFromUri(Uri.fromFile(extractedFile))
                    val audioDuration = resolved?.durationMs ?: 5000L

                    val audioTrack = currentProject.tracks.firstOrNull { it.type == TrackType.AUDIO }
                        ?: TimelineTrackModel(
                            id = "track_audio_extracted",
                            type = TrackType.AUDIO,
                            index = currentProject.tracks.size
                        ).also { newTrack ->
                            currentProject = currentProject.copy(tracks = currentProject.tracks + newTrack)
                        }

                    val audioClip = TimelineClipModel(
                        id = UUID.randomUUID().toString(),
                        mediaUri = Uri.fromFile(extractedFile).toString(),
                        name = "Extracted Audio",
                        sourceStartMs = 0L,
                        sourceEndMs = audioDuration,
                        timelineStartMs = playheadMs,
                        durationMs = audioDuration
                    )
                    currentProject = timelineEngine.addClipToTrack(audioTrack.id, audioClip)
                    selectedTrackId = audioTrack.id
                    selectedClipId = audioClip.id
                }
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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                    Icons.Default.Undo,
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
                    Icons.Default.Redo,
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
                onClick = { onExportProject(currentProject) },
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

        // Preview Canvas Area with dynamic Aspect Ratio
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
                // Evaluated Visual Clip Rendering
                snapshot?.activeVideoClips?.firstOrNull()?.let { renderable ->
                    val transform = renderable.transform
                    val effects = renderable.clip.effects
                    val colorFilter = remember(effects) {
                        ColorFilter.colorMatrix(EffectsEngine.buildColorMatrix(effects))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = transform.translationX.dp, y = transform.translationY.dp)
                            .scale(transform.scaleX, transform.scaleY)
                            .rotate(transform.rotation)
                            .background(ThrynxCardDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                tint = ThrynxCyan.copy(alpha = transform.opacity),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = renderable.clip.name.ifBlank { "Video Clip" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = transform.opacity)
                            )
                            Text(
                                text = "Time: ${formatDuration(renderable.sourcePositionMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThrynxTextSecondaryDark
                            )
                        }
                    }
                } ?: run {
                    // Empty timeline canvas indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoCall, contentDescription = null, tint = ThrynxTextMutedDark, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add clips to preview composition",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThrynxTextMutedDark
                        )
                    }
                }

                // Render Active Text Layers
                snapshot?.activeTextClips?.forEach { textClip ->
                    val transform = textClip.transform
                    Box(
                        modifier = Modifier
                            .offset(x = transform.translationX.dp, y = transform.translationY.dp)
                            .scale(transform.scaleX, transform.scaleY)
                            .rotate(transform.rotation)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = textClip.clip.textContent,
                            color = Color.White,
                            fontSize = textClip.clip.textFontSizeSp.sp,
                            fontWeight = FontWeight.Bold
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
                                    imageVector = if (track.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
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
                                    val clipWidth = ((clip.durationMs / 40L).toInt()).coerceIn(60, 450).dp

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
                                                    TrackType.TEXT -> ThrynxEmerald
                                                    else -> ThrynxCoral
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
                title = "Add Media",
                enabled = true,
                onClick = { mediaImportLauncher.launch("*/*") }
            )

            // Extract Audio from Video
            EditorToolButton(
                icon = Icons.Default.AudioFile,
                title = "Extract Audio",
                enabled = true,
                onClick = { audioExtractPicker.launch("video/*") }
            )

            // Split Action
            EditorToolButton(
                icon = Icons.Default.CallSplit,
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

            // Keyframe Action
            EditorToolButton(
                icon = Icons.Default.Diamond,
                title = "Keyframe",
                enabled = selectedClip != null,
                onClick = {
                    selectedTrackId?.let { tId ->
                        selectedClipId?.let { cId ->
                            selectedClip?.let { clip ->
                                val kf = KeyframeModel(
                                    timestampMs = playheadMs,
                                    scaleX = clip.transform.scaleX,
                                    scaleY = clip.transform.scaleY,
                                    rotation = clip.transform.rotation,
                                    opacity = clip.transform.opacity
                                )
                                currentProject = timelineEngine.addKeyframeToClip(tId, cId, kf)
                            }
                        }
                    }
                }
            )

            // Speed Action
            EditorToolButton(
                icon = Icons.Default.Speed,
                title = stringResource(id = R.string.editor_speed),
                enabled = selectedClip != null,
                onClick = { showSpeedDialog = true }
            )

            // Volume Action
            EditorToolButton(
                icon = Icons.Default.VolumeUp,
                title = "Volume",
                enabled = selectedClip != null,
                onClick = { showVolumeDialog = true }
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

            // Text Layer
            EditorToolButton(
                icon = Icons.Default.TextFields,
                title = stringResource(id = R.string.editor_text_track),
                enabled = true,
                onClick = { showAddTextDialog = true }
            )

            // Voice-Over Recording
            EditorToolButton(
                icon = Icons.Default.Mic,
                title = stringResource(id = R.string.audio_voiceover),
                enabled = true,
                onClick = { showVoiceRecordDialog = true }
            )
        }

        // Voice Recording Dialog
        if (showVoiceRecordDialog) {
            var isRecordingActive by remember { mutableStateOf(false) }
            var recordDurationSeconds by remember { mutableStateOf(0) }
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

        // Volume Adjustment Dialog
        if (showVolumeDialog && selectedClip != null) {
            var currentVol by remember { mutableStateOf(selectedClip.volume) }
            AlertDialog(
                onDismissRequest = { showVolumeDialog = false },
                title = { Text("Clip Volume") },
                text = {
                    Column {
                        Text("Volume: ${(currentVol * 100).toInt()}%", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = currentVol,
                            onValueChange = { currentVol = it },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = ThrynxCyan,
                                activeTrackColor = ThrynxCyan
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        selectedTrackId?.let { tId ->
                            selectedClipId?.let { cId ->
                                currentProject = timelineEngine.updateClipVolume(tId, cId, currentVol)
                            }
                        }
                        showVolumeDialog = false
                    }) {
                        Text(stringResource(id = R.string.action_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVolumeDialog = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // Filter / Effects Dialog
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
                                                val updatedTracks = currentProject.tracks.map { track ->
                                                    if (track.id == tId) {
                                                        track.copy(clips = track.clips.map { clip ->
                                                            if (clip.id == cId) clip.copy(effects = listOf(updatedEffect))
                                                            else clip
                                                        })
                                                    } else track
                                                }
                                                currentProject = currentProject.copy(tracks = updatedTracks)
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

        // Speed Dialog
        if (showSpeedDialog) {
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
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

        // Transition Selection Dialog
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
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = ThrynxCyan)
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

        // Canvas Aspect Ratio Dialog
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

        // Add Text Dialog
        if (showAddTextDialog) {
            var textInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddTextDialog = false },
                title = { Text("Add Text / বাংলা সাবটাইটেল") },
                text = {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Enter text (English or বাংলা)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                val textClip = TimelineClipModel(
                                    name = textInput,
                                    textContent = textInput,
                                    durationMs = 4000L,
                                    timelineStartMs = playheadMs
                                )
                                val textTrack = currentProject.tracks.firstOrNull { it.type == TrackType.TEXT }
                                    ?: TimelineTrackModel(
                                        id = "track_text_${System.currentTimeMillis()}",
                                        type = TrackType.TEXT,
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

        // Audio Extraction Progress Dialog
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
                            progress = (extractProgress / 100f).coerceIn(0f, 1f),
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
