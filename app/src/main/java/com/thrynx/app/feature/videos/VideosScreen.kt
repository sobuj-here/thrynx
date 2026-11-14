package com.thrynx.app.feature.videos

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.model.DurationFilter
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.SortOption
import com.thrynx.app.ui.components.VideoThumbnail
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideosScreen(
    videos: List<MediaItemModel>,
    folders: List<FolderItemModel> = emptyList(),
    favoritesUris: Set<String>,
    currentPlayingUri: Uri? = null,
    lastPlayedUri: String? = null,
    hasStoragePermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onOpenMedia: (MediaItemModel) -> Unit,
    onToggleFavorite: (MediaItemModel) -> Unit,
    onHideInVault: (MediaItemModel) -> Unit,
    onDeleteMedia: (MediaItemModel) -> Unit,
    onDeleteMultipleVideos: (List<MediaItemModel>) -> Unit = {},
    onHideMultipleVideosInVault: (List<MediaItemModel>) -> Unit = {},
    onRenameMedia: (MediaItemModel, String) -> Unit,
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { MediaStoreScanner(context) }
    val preferencesManager = remember { com.thrynx.app.ThrynxApplication.instance.preferencesManager }

    val resumeMode by preferencesManager.resumeModeFlow.collectAsState(initial = "ask")
    val showPlayedTime by preferencesManager.showPlayedTimeFlow.collectAsState(initial = true)
    val videoViewMode by preferencesManager.videoViewModeFlow.collectAsState(initial = "files")

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var durationFilter by remember { mutableStateOf(DurationFilter.ALL) }
    var isGridView by remember { mutableStateOf(false) }

    var showSortMenu by remember { mutableStateOf(false) }
    var selectedMediaForAction by remember { mutableStateOf<MediaItemModel?>(null) }
    var selectedMediaForDetails by remember { mutableStateOf<MediaItemModel?>(null) }
    var mediaToDelete by remember { mutableStateOf<MediaItemModel?>(null) }
    var mediaToRename by remember { mutableStateOf<MediaItemModel?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var videoToResumePrompt by remember { mutableStateOf<MediaItemModel?>(null) }
    var rememberResumeChoice by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Folder drill down state
    var selectedFolderDrillDown by remember { mutableStateOf<FolderItemModel?>(null) }

    fun handlePlayRequest(video: MediaItemModel) {
        if (video.lastPositionMs > 5000L && (video.durationMs == 0L || video.lastPositionMs < video.durationMs - 5000L)) {
            when (resumeMode) {
                "resume" -> onOpenMedia(video)
                "beginning" -> onOpenMedia(video.copy(lastPositionMs = 0L))
                else -> videoToResumePrompt = video
            }
        } else {
            onOpenMedia(video)
        }
    }

    // Active video list
    val baseList = remember(videos, selectedFolderDrillDown) {
        if (selectedFolderDrillDown != null) {
            videos.filter { it.folderName.equals(selectedFolderDrillDown!!.name, ignoreCase = true) }
        } else videos
    }

    // Process and sort video list
    val processedVideos = remember(baseList, searchQuery, sortOption, durationFilter) {
        var list = baseList
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        list = when (durationFilter) {
            DurationFilter.ALL -> list
            DurationFilter.SHORT -> list.filter { it.durationMs < 120_000L }
            DurationFilter.MEDIUM -> list.filter { it.durationMs in 120_000L..600_000L }
            DurationFilter.LONG -> list.filter { it.durationMs > 600_000L }
        }
        when (sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> list.sortedBy { it.dateAdded }
            SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC -> list.sortedBy { it.durationMs }
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
                            selectedUris = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Text(
                            text = "${selectedUris.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            selectedUris = if (selectedUris.size == processedVideos.size) {
                                emptySet()
                            } else {
                                processedVideos.mapNotNull { it.uri?.toString() ?: it.path }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = ThrynxCyan)
                        }
                        if (selectedUris.isNotEmpty()) {
                            IconButton(onClick = {
                                val selectedItems = processedVideos.filter {
                                    (it.uri?.toString() ?: it.path) in selectedUris
                                }
                                onHideMultipleVideosInVault(selectedItems)
                                isSelectionMode = false
                                selectedUris = emptySet()
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Hide in Vault", tint = ThrynxCyan)
                            }
                            IconButton(onClick = { showBulkDeleteDialog = true }) {
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
                                    text = stringResource(id = R.string.nav_videos),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Toggle Files vs Folders
                                if (selectedFolderDrillDown == null) {
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            preferencesManager.setVideoViewMode(
                                                if (videoViewMode == "files") "folders" else "files"
                                            )
                                        }
                                    }) {
                                        Icon(
                                            imageVector = if (videoViewMode == "folders") Icons.Default.VideoLibrary else Icons.Default.Folder,
                                            contentDescription = "Toggle Files/Folders",
                                            tint = ThrynxCyan
                                        )
                                    }
                                }

                                // Layout Switcher (Grid vs List)
                                IconButton(onClick = { isGridView = !isGridView }) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = "Switch Layout",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Sort Menu
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurface)
                                }

                                // Rescan
                                IconButton(onClick = onRescanLibrary) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Search Input
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(id = R.string.videos_search_hint), style = MaterialTheme.typography.bodyMedium) },
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

                        // Duration Filter Chips
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = durationFilter == DurationFilter.ALL,
                                onClick = { durationFilter = DurationFilter.ALL },
                                label = { Text(stringResource(id = R.string.filter_all)) }
                            )
                            FilterChip(
                                selected = durationFilter == DurationFilter.SHORT,
                                onClick = { durationFilter = DurationFilter.SHORT },
                                label = { Text(stringResource(id = R.string.filter_short)) }
                            )
                            FilterChip(
                                selected = durationFilter == DurationFilter.MEDIUM,
                                onClick = { durationFilter = DurationFilter.MEDIUM },
                                label = { Text(stringResource(id = R.string.filter_medium)) }
                            )
                            FilterChip(
                                selected = durationFilter == DurationFilter.LONG,
                                onClick = { durationFilter = DurationFilter.LONG },
                                label = { Text(stringResource(id = R.string.filter_long)) }
                            )
                        }
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
                        Icon(Icons.Default.FolderOff, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.permission_storage_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.permission_storage_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                        ) {
                            Text(stringResource(id = R.string.permission_grant), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (videoViewMode == "folders" && selectedFolderDrillDown == null) {
                // Folder List View inside Videos
                if (folders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(id = R.string.folders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(folders, key = { it.path }) { folder ->
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
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E2838)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        VideoThumbnail(
                                            uri = folder.latestMedia?.uri,
                                            path = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
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
                                            text = "${folder.videoCount} videos",
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
                // Video Items View (Flat List or Grid)
                if (processedVideos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(id = R.string.videos_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(processedVideos, key = { it.id }) { video ->
                            val itemKey = video.uri?.toString() ?: video.path
                            val isSelected = selectedUris.contains(itemKey)
                            val isFav = favoritesUris.contains(video.uri?.toString() ?: "")
                            val isPlaying = video.uri != null && video.uri == currentPlayingUri
                            val isLastPlayed = (video.mediaUriString == lastPlayedUri)

                            VideoGridItem(
                                video = video,
                                isFavorite = isFav,
                                isPlaying = isPlaying,
                                isLastPlayed = isLastPlayed,
                                showPlayedTime = showPlayedTime,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedUris = if (isSelected) selectedUris - itemKey else selectedUris + itemKey
                                        if (selectedUris.isEmpty()) isSelectionMode = false
                                    } else {
                                        handlePlayRequest(video)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedUris = selectedUris + itemKey
                                },
                                onMoreClick = { selectedMediaForAction = video }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(processedVideos, key = { it.id }) { video ->
                            val itemKey = video.uri?.toString() ?: video.path
                            val isSelected = selectedUris.contains(itemKey)
                            val isFav = favoritesUris.contains(video.uri?.toString() ?: "")
                            val isPlaying = video.uri != null && video.uri == currentPlayingUri
                            val isLastPlayed = (video.mediaUriString == lastPlayedUri)

                            VideoListItem(
                                video = video,
                                isFavorite = isFav,
                                isPlaying = isPlaying,
                                isLastPlayed = isLastPlayed,
                                showPlayedTime = showPlayedTime,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedUris = if (isSelected) selectedUris - itemKey else selectedUris + itemKey
                                        if (selectedUris.isEmpty()) isSelectionMode = false
                                    } else {
                                        handlePlayRequest(video)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedUris = selectedUris + itemKey
                                },
                                onMoreClick = { selectedMediaForAction = video }
                            )
                        }
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
            text = { Text(stringResource(id = R.string.sort_date_desc)) },
            onClick = { sortOption = SortOption.DATE_DESC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.sort_date_asc)) },
            onClick = { sortOption = SortOption.DATE_ASC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.sort_name_asc)) },
            onClick = { sortOption = SortOption.NAME_ASC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.sort_name_desc)) },
            onClick = { sortOption = SortOption.NAME_DESC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.sort_size_desc)) },
            onClick = { sortOption = SortOption.SIZE_DESC; showSortMenu = false }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.sort_duration_desc)) },
            onClick = { sortOption = SortOption.DURATION_DESC; showSortMenu = false }
        )
    }

    // Resume Dialog
    videoToResumePrompt?.let { media ->
        AlertDialog(
            onDismissRequest = { videoToResumePrompt = null },
            title = { Text(stringResource(id = R.string.dialog_resume_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.dialog_resume_message, formatDuration(media.lastPositionMs)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberResumeChoice = !rememberResumeChoice }
                    ) {
                        Checkbox(
                            checked = rememberResumeChoice,
                            onCheckedChange = { rememberResumeChoice = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.dialog_resume_remember),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rememberResumeChoice) {
                            coroutineScope.launch { preferencesManager.setResumeMode("resume") }
                        }
                        videoToResumePrompt = null
                        onOpenMedia(media)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                ) {
                    Text(stringResource(id = R.string.dialog_resume_action_resume, formatDuration(media.lastPositionMs)), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (rememberResumeChoice) {
                            coroutineScope.launch { preferencesManager.setResumeMode("beginning") }
                        }
                        videoToResumePrompt = null
                        onOpenMedia(media.copy(lastPositionMs = 0L))
                    }
                ) {
                    Text(stringResource(id = R.string.dialog_resume_action_restart))
                }
            }
        )
    }

    // Bulk Delete Dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(stringResource(id = R.string.videos_delete)) },
            text = { Text("Delete ${selectedUris.size} selected video(s) permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        val itemsToDelete = processedVideos.filter {
                            (it.uri?.toString() ?: it.path) in selectedUris
                        }
                        onDeleteMultipleVideos(itemsToDelete)
                        selectedUris = emptySet()
                        isSelectionMode = false
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.videos_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Contextual Action Bottom Sheet
    selectedMediaForAction?.let { media ->
        val isFav = favoritesUris.contains(media.uri?.toString() ?: "")
        ModalBottomSheet(
            onDismissRequest = { selectedMediaForAction = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.player_play)) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan) },
                    modifier = Modifier.clickable {
                        onOpenMedia(media)
                        selectedMediaForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(if (isFav) stringResource(id = R.string.videos_favorite_removed) else stringResource(id = R.string.videos_favorite_added)) },
                    leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = ThrynxAmber) },
                    modifier = Modifier.clickable {
                        onToggleFavorite(media)
                        selectedMediaForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.folders_vault_hide_video)) },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = ThrynxCyan) },
                    modifier = Modifier.clickable {
                        onHideInVault(media)
                        selectedMediaForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.videos_share)) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val shareIntent = scanner.shareVideo(media)
                        context.startActivity(shareIntent)
                        selectedMediaForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.videos_details)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedMediaForDetails = media
                        selectedMediaForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.videos_rename)) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        renameInput = media.title
                        mediaToRename = media
                        selectedMediaForAction = null
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.videos_delete)) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        mediaToDelete = media
                        selectedMediaForAction = null
                    }
                )
            }
        }
    }

    // Single Delete Dialog
    mediaToDelete?.let { media ->
        AlertDialog(
            onDismissRequest = { mediaToDelete = null },
            title = { Text(stringResource(id = R.string.videos_delete)) },
            text = { Text(stringResource(id = R.string.videos_delete_confirm, media.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMedia(media)
                        mediaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.videos_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mediaToDelete = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Rename Dialog
    mediaToRename?.let { media ->
        AlertDialog(
            onDismissRequest = { mediaToRename = null },
            title = { Text(stringResource(id = R.string.videos_rename)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameMedia(media, renameInput.trim())
                        }
                        mediaToRename = null
                    }
                ) {
                    Text(stringResource(id = R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { mediaToRename = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Details Dialog
    selectedMediaForDetails?.let { media ->
        AlertDialog(
            onDismissRequest = { selectedMediaForDetails = null },
            title = { Text(stringResource(id = R.string.videos_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Title: ${media.title}", fontWeight = FontWeight.Bold)
                    Text("Duration: ${formatDuration(media.durationMs)}")
                    Text("Size: ${formatFileSize(media.sizeBytes)}")
                    Text("Resolution: ${media.resolutionLabel}")
                    Text("Format: ${media.mimeType.ifBlank { "video" }}")
                    Text("Path: ${media.path}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMediaForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: MediaItemModel,
    isFavorite: Boolean,
    isPlaying: Boolean,
    isLastPlayed: Boolean = false,
    showPlayedTime: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPlaying) ThrynxCyan.copy(alpha = 0.12f) else if (isLastPlayed) ThrynxCobalt.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isPlaying || isLastPlayed) BorderStroke(1.5.dp, ThrynxCyan) else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141C2B)),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(
                    uri = video.uri,
                    path = video.path,
                    modifier = Modifier.fillMaxSize()
                )

                if (video.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = formatDuration(video.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    }
                }

                // Progress indicator under thumbnail
                if (showPlayedTime && video.lastPositionMs > 0L && video.durationMs > 0L) {
                    val progress = (video.lastPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ThrynxCyan,
                        trackColor = Color.DarkGray,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) ThrynxCyan else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLastPlayed && !isPlaying) {
                        Surface(
                            color = ThrynxCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LAST PLAYED",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThrynxCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = video.resolutionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ThrynxCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatFileSize(video.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showPlayedTime && video.lastPositionMs > 0L) {
                        Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${formatDuration(video.lastPositionMs)} played",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThrynxCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isFavorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = ThrynxAmber,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: MediaItemModel,
    isFavorite: Boolean,
    isPlaying: Boolean,
    isLastPlayed: Boolean = false,
    showPlayedTime: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isPlaying || isLastPlayed) BorderStroke(1.5.dp, ThrynxCyan) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) ThrynxCyan.copy(alpha = 0.12f) else if (isLastPlayed) ThrynxCobalt.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(Color(0xFF141C2B)),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(
                    uri = video.uri,
                    path = video.path,
                    modifier = Modifier.fillMaxSize()
                )

                if (isLastPlayed && !isPlaying && !isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ThrynxCyan)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LAST PLAYED",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThrynxBgDark,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                    }
                }

                if (video.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDuration(video.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ThrynxCobalt.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.resolutionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress bar
                if (showPlayedTime && video.lastPositionMs > 0L && video.durationMs > 0L) {
                    val progress = (video.lastPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ThrynxCyan,
                        trackColor = Color.DarkGray,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) ThrynxCyan else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPlayedTime && video.lastPositionMs > 0L) "${formatDuration(video.lastPositionMs)} played" else formatFileSize(video.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showPlayedTime && video.lastPositionMs > 0L) ThrynxCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
                    }
                }
            }
        }
    }
}
