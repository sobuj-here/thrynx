package com.thrynx.app.feature.videos

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.model.DurationFilter
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.SortOption
import kotlinx.coroutines.launch

@Composable
fun VideosScreen(
    videos: List<MediaItemModel>,
    favoritesUris: Set<String>,
    onOpenMedia: (MediaItemModel) -> Unit,
    onToggleFavorite: (MediaItemModel) -> Unit,
    onHideInVault: (MediaItemModel) -> Unit,
    onDeleteMedia: (MediaItemModel) -> Unit,
    onRenameMedia: (MediaItemModel, String) -> Unit,
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { MediaStoreScanner(context) }

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

    // SAF Import Launcher
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val resolved = scanner.resolveMetadataFromUri(uri)
                if (resolved != null) {
                    onOpenMedia(resolved)
                }
            }
        }
    }

    // Process and sort video list
    val processedVideos = remember(videos, searchQuery, sortOption, durationFilter) {
        var list = videos
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { fileImportLauncher.launch("video/*") },
                containerColor = ThrynxCyan,
                contentColor = ThrynxBgDark,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = stringResource(id = R.string.videos_import))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.videos_import), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.videos_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ThrynxCyan
                    )
                    Text(
                        text = "${processedVideos.size} videos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Layout Toggle (List / Grid)
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View",
                            tint = Color.White
                        )
                    }

                    // Sort Menu Button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) ThrynxCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Refresh Button
                    IconButton(onClick = onRescanLibrary) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(id = R.string.videos_rescan), tint = Color.White)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(id = R.string.videos_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ThrynxCyan) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = ThrynxCyan
                )
            )

            // Duration Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationFilter.values().forEach { filter ->
                    FilterChip(
                        selected = durationFilter == filter,
                        onClick = { durationFilter = filter },
                        label = { Text(filter.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ThrynxCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ThrynxCyan
                        )
                    )
                }
            }

            // Video Collection (Grid vs List)
            if (processedVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.videos_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(processedVideos) { item ->
                        val isFav = favoritesUris.contains(item.mediaUriString)
                        VideoGridItem(
                            video = item,
                            isFavorite = isFav,
                            onClick = { onOpenMedia(item) },
                            onMoreClick = { selectedMediaForAction = item }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(processedVideos) { item ->
                        val isFav = favoritesUris.contains(item.mediaUriString)
                        VideoListItem(
                            video = item,
                            isFavorite = isFav,
                            onClick = { onOpenMedia(item) },
                            onMoreClick = { selectedMediaForAction = item }
                        )
                    }
                }
            }
        }
    }

    // Video Action Bottom Sheet / Menu
    selectedMediaForAction?.let { media ->
        val isFav = favoritesUris.contains(media.mediaUriString)
        AlertDialog(
            onDismissRequest = { selectedMediaForAction = null },
            title = {
                Text(
                    text = media.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(id = R.string.player_play)) },
                        leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan) },
                        modifier = Modifier.clickable {
                            selectedMediaForAction = null
                            onOpenMedia(media)
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (isFav) "Remove from Favorites" else "Add to Favorites") },
                        leadingContent = {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isFav) ThrynxAmber else Color.White
                            )
                        },
                        modifier = Modifier.clickable {
                            selectedMediaForAction = null
                            onToggleFavorite(media)
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(id = R.string.folders_vault_hide_video)) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = ThrynxCyan) },
                        modifier = Modifier.clickable {
                            selectedMediaForAction = null
                            onHideInVault(media)
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(id = R.string.videos_share)) },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        modifier = Modifier.clickable {
                            selectedMediaForAction = null
                            context.startActivity(scanner.shareVideo(media))
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
                            mediaToRename = media
                            renameInput = media.title
                            selectedMediaForAction = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(id = R.string.videos_delete), color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            mediaToDelete = media
                            selectedMediaForAction = null
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMediaForAction = null }) {
                    Text(stringResource(id = R.string.action_close))
                }
            }
        )
    }

    // Video Details Dialog
    selectedMediaForDetails?.let { media ->
        AlertDialog(
            onDismissRequest = { selectedMediaForDetails = null },
            title = { Text(stringResource(id = R.string.videos_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Title: ${media.title}", fontWeight = FontWeight.Bold)
                    Text("Duration: ${formatDuration(media.durationMs)}")
                    if (media.width > 0 && media.height > 0) {
                        Text("Resolution: ${media.width} x ${media.height} (${media.resolutionLabel})")
                    }
                    Text("File Size: ${formatFileSize(media.sizeBytes)}")
                    Text("Format: ${media.mimeType}")
                    Text("Folder: ${media.folderName}")
                    if (media.path.isNotBlank()) {
                        Text("File Path: ${media.path}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Added: ${formatDate(media.dateAdded)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMediaForDetails = null }) {
                    Text(stringResource(id = R.string.action_close))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    mediaToDelete?.let { media ->
        AlertDialog(
            onDismissRequest = { mediaToDelete = null },
            title = { Text(stringResource(id = R.string.videos_delete)) },
            text = {
                Text(stringResource(id = R.string.videos_delete_confirm, media.title))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = media
                        mediaToDelete = null
                        onDeleteMedia(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.videos_delete))
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
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInput.trim()
                        if (newName.isNotBlank()) {
                            val target = media
                            mediaToRename = null
                            onRenameMedia(target, newName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
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
}

@Composable
fun VideoListItem(
    video: MediaItemModel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141C2B)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = video.uri ?: video.path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(24.dp))

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
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = video.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (isFavorite) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Favorite",
                tint = ThrynxAmber,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp)
            )
        }

        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
        }
    }
}

@Composable
fun VideoGridItem(
    video: MediaItemModel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(Color(0xFF141C2B)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = video.uri ?: video.path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(24.dp))
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

                if (isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = ThrynxAmber, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(video.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
