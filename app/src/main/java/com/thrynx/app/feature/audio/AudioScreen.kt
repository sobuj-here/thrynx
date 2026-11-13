package com.thrynx.app.feature.audio

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.model.SortOption
import com.thrynx.app.core.player.ThrynxPlayerManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioScreen(
    audios: List<MediaItemModel>,
    audioFolders: List<FolderItemModel>,
    playbackState: PlaybackState,
    playerManager: ThrynxPlayerManager,
    hasStoragePermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onPlayAudio: (MediaItemModel) -> Unit,
    onDeleteAudio: (MediaItemModel) -> Unit,
    onDeleteMultipleAudios: (List<MediaItemModel>) -> Unit = {},
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferencesManager = remember { com.thrynx.app.ThrynxApplication.instance.preferencesManager }
    val audioViewMode by preferencesManager.audioViewModeFlow.collectAsState(initial = "files")
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedAudioIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var singleAudioToDelete by remember { mutableStateOf<MediaItemModel?>(null) }
    var selectedAudioForAction by remember { mutableStateOf<MediaItemModel?>(null) }
    var selectedFolderDrillDown by remember { mutableStateOf<FolderItemModel?>(null) }

    // Active audio list (either flat or filtered by folder)
    val baseList = remember(audios, selectedFolderDrillDown) {
        if (selectedFolderDrillDown != null) {
            audios.filter { audio ->
                val folderPath = if (audio.path.isNotBlank()) java.io.File(audio.path).parent ?: "" else ""
                folderPath == selectedFolderDrillDown!!.path
            }
        } else audios
    }

    val processedAudios = remember(baseList, searchQuery, sortOption) {
        var list = baseList
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        (it.path.contains(searchQuery, ignoreCase = true))
            }
        }
        when (sortOption) {
            SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> list.sortedBy { it.dateAdded }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC -> list.sortedBy { it.durationMs }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedAudioIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Text(
                            text = "${selectedAudioIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            selectedAudioIds = if (selectedAudioIds.size == processedAudios.size) {
                                emptySet()
                            } else {
                                processedAudios.map { it.id }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = ThrynxCyan)
                        }
                        if (selectedAudioIds.isNotEmpty()) {
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (selectedFolderDrillDown != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { selectedFolderDrillDown = null }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedFolderDrillDown!!.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "Audio",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Toggle View Mode (Files vs Folders)
                                if (selectedFolderDrillDown == null) {
                                    IconButton(onClick = {
                                        kotlinx.coroutines.runBlocking {
                                            preferencesManager.setAudioViewMode(
                                                if (audioViewMode == "files") "folders" else "files"
                                            )
                                        }
                                    }) {
                                        Icon(
                                            imageVector = if (audioViewMode == "folders") Icons.Default.Audiotrack else Icons.Default.Folder,
                                            contentDescription = "Toggle View",
                                            tint = ThrynxCyan
                                        )
                                    }
                                }

                                // Sort Button
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurface)
                                }

                                // Rescan
                                IconButton(onClick = onRescanLibrary) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Search Field
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search songs, artists...", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ThrynxCyan) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ThrynxCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            // Main Content Area
            if (!hasStoragePermission) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Storage Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Grant audio/storage permission to scan and play songs stored on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (audioViewMode == "folders" && selectedFolderDrillDown == null) {
                // Folder View
                if (audioFolders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No audio folders found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(audioFolders, key = { it.path }) { folder ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolderDrillDown = folder }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(Color(0xFF7C4DFF), ThrynxCyan)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${folder.videoCount} songs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            } else {
                // Track List View
                if (processedAudios.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No audio files found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(processedAudios, key = { it.id }) { audio ->
                            val isSelected = selectedAudioIds.contains(audio.id)
                            val isPlaying = playbackState.currentMedia?.uri == audio.uri ||
                                    (playbackState.currentMedia?.id == audio.id)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPlaying) ThrynxCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, ThrynxCyan) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedAudioIds = if (isSelected) {
                                                    selectedAudioIds - audio.id
                                                } else {
                                                    selectedAudioIds + audio.id
                                                }
                                                if (selectedAudioIds.isEmpty()) {
                                                    isSelectionMode = false
                                                }
                                            } else {
                                                onPlayAudio(audio)
                                            }
                                        },
                                        onLongClick = {
                                            isSelectionMode = true
                                            selectedAudioIds = selectedAudioIds + audio.id
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedAudioIds = if (checked) {
                                                    selectedAudioIds + audio.id
                                                } else {
                                                    selectedAudioIds - audio.id
                                                }
                                                if (selectedAudioIds.isEmpty()) isSelectionMode = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isPlaying) ThrynxCyan else Color(0xFF1E2838)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying && playbackState.isPlaying) Icons.Default.Audiotrack else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isPlaying) ThrynxBgDark else ThrynxCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = audio.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPlaying) ThrynxCyan else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = formatDuration(audio.durationMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = " • ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatFileSize(audio.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (!isSelectionMode) {
                                        IconButton(onClick = { selectedAudioForAction = audio }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Audio Mini-Player Bar (shown when an audio is currently playing)
        if (playbackState.currentMedia != null && playbackState.currentMedia?.isVideo == false) {
            val currentMedia = playbackState.currentMedia!!
            Surface(
                color = Color(0xFF161F2E),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ThrynxCyan.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ThrynxCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = ThrynxBgDark, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMedia.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThrynxCyan
                            )
                        }
                        // Rewind 10s
                        IconButton(onClick = { playerManager.seekBackward(10000L) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White)
                        }
                        // Play/Pause
                        IconButton(
                            onClick = { playerManager.togglePlayPause() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ThrynxCyan)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = ThrynxBgDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Forward 10s
                        IconButton(onClick = { playerManager.seekForward(10000L) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White)
                        }
                    }

                    // Progress Slider
                    if (playbackState.durationMs > 0L) {
                        val progress = (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = ThrynxCyan,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }

    // Sort Menu
    DropdownMenu(
        expanded = showSortMenu,
        onDismissRequest = { showSortMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("Name (A to Z)") },
            onClick = { sortOption = SortOption.NAME_ASC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text("Name (Z to A)") },
            onClick = { sortOption = SortOption.NAME_DESC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text("Date Added (Newest)") },
            onClick = { sortOption = SortOption.DATE_DESC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text("Duration (Longest)") },
            onClick = { sortOption = SortOption.DURATION_DESC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text("Size (Largest)") },
            onClick = { sortOption = SortOption.SIZE_DESC; showSortMenu = false }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Audios") },
            text = { Text("Delete ${selectedAudioIds.size} selected audio file(s) permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        val itemsToDelete = audios.filter { it.id in selectedAudioIds }
                        onDeleteMultipleAudios(itemsToDelete)
                        selectedAudioIds = emptySet()
                        isSelectionMode = false
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Audio Action Bottom Sheet
    selectedAudioForAction?.let { audio ->
        ModalBottomSheet(
            onDismissRequest = { selectedAudioForAction = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = audio.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                ListItem(
                    headlineContent = { Text("Play") },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan) },
                    modifier = Modifier.clickable {
                        onPlayAudio(audio)
                        selectedAudioForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        singleAudioToDelete = audio
                        selectedAudioForAction = null
                    }
                )
            }
        }
    }

    // Single Delete Dialog
    singleAudioToDelete?.let { audio ->
        AlertDialog(
            onDismissRequest = { singleAudioToDelete = null },
            title = { Text("Delete Audio") },
            text = { Text("Are you sure you want to delete '${audio.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAudio(audio)
                        singleAudioToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { singleAudioToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
