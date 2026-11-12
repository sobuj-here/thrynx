package com.thrynx.app.feature.editor

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.editor.effects.EffectsEngine
import com.thrynx.app.core.editor.preview.PreviewEngine
import com.thrynx.app.core.editor.timeline.TimelineEngine
import com.thrynx.app.core.model.*
import com.thrynx.app.feature.home.formatDuration

@Composable
fun EditorScreen(
    initialProject: ProjectModel,
    onSaveProject: (ProjectModel) -> Unit,
    onExportProject: (ProjectModel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    val selectedClip = remember(currentProject, selectedTrackId, selectedClipId) {
        currentProject.tracks.firstOrNull { it.id == selectedTrackId }
            ?.clips?.firstOrNull { it.id == selectedClipId }
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
                maxLines = 1
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

        // Preview Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ThrynxSurfaceDark)
                .border(1.dp, ThrynxCardStrokeDark, RoundedCornerShape(16.dp)),
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
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                    Icon(Icons.Default.VideoCall, contentDescription = null, tint = ThrynxTextMutedDark, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
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
                text = "${currentProject.fps} FPS • ${currentProject.resolutionHeight}p",
                style = MaterialTheme.typography.labelSmall,
                color = ThrynxTextSecondaryDark
            )
        }

        // Multi-Track Timeline View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
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
                                .height(36.dp)
                                .background(ThrynxTimelineTrack, RoundedCornerShape(6.dp))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = track.type.name.take(3),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThrynxTextSecondaryDark,
                                modifier = Modifier.width(30.dp)
                            )

                            if (track.clips.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .fillMaxHeight()
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Empty Track", fontSize = 10.sp, color = ThrynxTextMutedDark)
                                }
                            } else {
                                track.clips.forEach { clip ->
                                    val isClipSelected = selectedClipId == clip.id
                                    val clipWidth = ((clip.durationMs / 50L).toInt()).coerceIn(60, 400).dp

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
                                        Text(
                                            text = clip.name.ifBlank { clip.textContent.ifBlank { "Clip" } },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isClipSelected) ThrynxBgDark else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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

        // Action Toolbar (CapCut-grade controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .background(ThrynxCardDark)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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

            // Speed Action
            EditorToolButton(
                icon = Icons.Default.Speed,
                title = stringResource(id = R.string.editor_speed),
                enabled = selectedClip != null,
                onClick = { showSpeedDialog = true }
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
                                if (textTrack != null) {
                                    currentProject = timelineEngine.addClipToTrack(textTrack.id, textClip)
                                }
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
