package com.thrynx.app.feature.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.thrynx.app.core.database.ExportHistoryEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.feature.home.formatDuration
import com.thrynx.app.feature.settings.formatFileSize
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SortOption(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    DURATION_DESC("Duration (Longest)"),
    DURATION_ASC("Duration (Shortest)")
}

enum class DurationFilter(val label: String) {
    ALL("All"),
    SHORT("< 2 mins"),
    MEDIUM("2 - 10 mins"),
    LONG("> 10 mins")
}

@Composable
fun LibraryScreen(
    videos: List<MediaItemModel>,
    audios: List<MediaItemModel>,
    photos: List<MediaItemModel> = emptyList(),
    exports: List<ExportHistoryEntity> = emptyList(),
    onOpenMedia: (MediaItemModel) -> Unit,
    onAddToEditor: (MediaItemModel) -> Unit = {},
    onDeleteExport: (ExportHistoryEntity) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { MediaStoreScanner(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Audio", "Photos", "Folders", "Exports", "Favorites")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var durationFilter by remember { mutableStateOf(DurationFilter.ALL) }

    var showSortMenu by remember { mutableStateOf(false) }
    var selectedMediaForDetails by remember { mutableStateOf<MediaItemModel?>(null) }
    var selectedMediaActionSheet by remember { mutableStateOf<MediaItemModel?>(null) }

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

    val folders = remember(videos, audios, photos) {
        (videos + audios + photos).groupBy { it.folderName }
    }

    // Sorting and Filtering logic
    fun processList(list: List<MediaItemModel>): List<MediaItemModel> {
        var filtered = list
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        filtered = when (durationFilter) {
            DurationFilter.ALL -> filtered
            DurationFilter.SHORT -> filtered.filter { it.durationMs < 120_000L }
            DurationFilter.MEDIUM -> filtered.filter { it.durationMs in 120_000L..600_000L }
            DurationFilter.LONG -> filtered.filter { it.durationMs > 600_000L }
        }
        return when (sortOption) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> filtered.sortedBy { it.dateAdded }
            SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
            SortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC -> filtered.sortedBy { it.durationMs }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { fileImportLauncher.launch("*/*") },
                containerColor = ThrynxCyan,
                contentColor = ThrynxBgDark,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Import File")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
            // Header with action icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.nav_library),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ThrynxCyan
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            }

            // Tabs (Videos, Audio, Photos, Folders, Exports, Favorites)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ThrynxCyan
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            selectedFolder = null
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by filename or title…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Duration Filter Chips (for Videos & Audio)
            if (selectedTab == 0 || selectedTab == 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DurationFilter.values()) { filter ->
                        FilterChip(
                            selected = durationFilter == filter,
                            onClick = { durationFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ThrynxCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ThrynxCyan
                            )
                        )
                    }
                }
            }

            // Main Content by Selected Tab
            when (selectedTab) {
                0 -> { // Videos
                    val list = processList(videos)
                    if (list.isEmpty()) {
                        EmptyStateView(message = "No videos discovered on device")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(list) { item ->
                                LibraryMediaRow(
                                    media = item,
                                    onClick = { onOpenMedia(item) },
                                    onMoreClick = { selectedMediaActionSheet = item },
                                    onInfoClick = { selectedMediaForDetails = item }
                                )
                            }
                        }
                    }
                }
                1 -> { // Audio
                    val list = processList(audios)
                    if (list.isEmpty()) {
                        EmptyStateView(message = "No audio files found on device")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(list) { item ->
                                LibraryMediaRow(
                                    media = item,
                                    onClick = { onOpenMedia(item) },
                                    onMoreClick = { selectedMediaActionSheet = item },
                                    onInfoClick = { selectedMediaForDetails = item }
                                )
                            }
                        }
                    }
                }
                2 -> { // Photos
                    val list = if (searchQuery.isBlank()) photos else photos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                    if (list.isEmpty()) {
                        EmptyStateView(message = "No photos found on device")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(list) { photo ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ThrynxCardDark)
                                        .clickable { selectedMediaActionSheet = photo }
                                ) {
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = photo.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> { // Folders
                    if (selectedFolder != null) {
                        // Folder Detail View
                        val folderClips = processList((videos + audios + photos).filter { it.folderName == selectedFolder })
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { selectedFolder = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    text = selectedFolder ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(folderClips) { item ->
                                    LibraryMediaRow(
                                        media = item,
                                        onClick = { onOpenMedia(item) },
                                        onMoreClick = { selectedMediaActionSheet = item },
                                        onInfoClick = { selectedMediaForDetails = item }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            folders.forEach { (folderName, items) ->
                                item {
                                    val count = items.size
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedFolder = folderName }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(ThrynxCardDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = ThrynxCyan)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folderName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$count media files",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> { // Exports
                    if (exports.isEmpty()) {
                        EmptyStateView(message = "No exported videos yet. Start creating in Editor!")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(exports) { export ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ThrynxEmerald.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = ThrynxEmerald)
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = export.projectName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${export.resolution} • ${formatDuration(export.durationMs)} • ${formatFileSize(export.fileSizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Play Export
                                    IconButton(onClick = {
                                        val media = MediaItemModel(
                                            title = export.projectName,
                                            uri = Uri.fromFile(File(export.filePath)),
                                            path = export.filePath,
                                            durationMs = export.durationMs,
                                            isVideo = true
                                        )
                                        onOpenMedia(media)
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = ThrynxCyan)
                                    }

                                    // Share Export
                                    IconButton(onClick = {
                                        try {
                                            val file = File(export.filePath)
                                            val contentUri = try {
                                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            } catch (_: Exception) {
                                                Uri.fromFile(file)
                                            }
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Exported Video"))
                                        } catch (_: Exception) {}
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    }

                                    // Delete Export
                                    IconButton(onClick = { onDeleteExport(export) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ThrynxCoral, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                5 -> { // Favorites
                    val favs = processList((videos + audios + photos).filter { it.isFavorite })
                    if (favs.isEmpty()) {
                        EmptyStateView(message = "No favorite media added yet")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(favs) { item ->
                                LibraryMediaRow(
                                    media = item,
                                    onClick = { onOpenMedia(item) },
                                    onMoreClick = { selectedMediaActionSheet = item },
                                    onInfoClick = { selectedMediaForDetails = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Details Dialog
    selectedMediaForDetails?.let { media ->
        MediaDetailsDialog(
            media = media,
            onDismiss = { selectedMediaForDetails = null },
            onPlay = {
                selectedMediaForDetails = null
                onOpenMedia(media)
            }
        )
    }

    // Media Quick Action Sheet / Dialog
    selectedMediaActionSheet?.let { media ->
        AlertDialog(
            onDismissRequest = { selectedMediaActionSheet = null },
            title = {
                Text(
                    text = media.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMediaActionSheet = null
                                onOpenMedia(media)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = ThrynxCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Play in THRYNX Player")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMediaActionSheet = null
                                onAddToEditor(media)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = ThrynxPurple)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Add to Video Editor Project")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMediaActionSheet = null
                                selectedMediaForDetails = media
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("View Media Details & Codec")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMediaActionSheet = null }) {
                    Text(stringResource(id = R.string.action_close))
                }
            }
        )
    }
}

@Composable
fun LibraryMediaRow(
    media: MediaItemModel,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (media.isVideo) ThrynxCyan.copy(alpha = 0.15f)
                    else ThrynxPurple.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (media.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (media.isVideo) ThrynxCyan else ThrynxPurple
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(media.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (media.sizeBytes > 0) {
                    Text(
                        text = " • ${formatFileSize(media.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (media.isVideo && media.width > 0 && media.height > 0) {
                    Text(
                        text = " • ${media.width}x${media.height}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThrynxCyan
                    )
                }
            }
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun EmptyStateView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MediaDetailsDialog(
    media: MediaItemModel,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    val dateStr = remember(media.dateAdded) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(media.dateAdded * 1000L))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (media.isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = ThrynxCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Media Information", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = "Name", value = media.title)
                DetailRow(label = "Type", value = if (media.isVideo) "Video" else "Audio")
                DetailRow(label = "Format", value = media.mimeType.ifBlank { "Unknown" })
                DetailRow(label = "Duration", value = formatDuration(media.durationMs))
                DetailRow(label = "File Size", value = formatFileSize(media.sizeBytes))
                if (media.isVideo && media.width > 0 && media.height > 0) {
                    DetailRow(label = "Resolution", value = "${media.width} x ${media.height}")
                }
                DetailRow(label = "Path", value = media.path)
                DetailRow(label = "Folder", value = media.folderName)
                DetailRow(label = "Date Added", value = dateStr)
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan)
            ) {
                Text("Play", color = ThrynxBgDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_close))
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
