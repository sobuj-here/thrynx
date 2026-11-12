package com.thrynx.app.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.feature.home.formatDuration
import kotlinx.coroutines.launch
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
    onOpenMedia: (MediaItemModel) -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { MediaStoreScanner(context) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Videos", "Audio", "Folders", "Favorites")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var durationFilter by remember { mutableStateOf(DurationFilter.ALL) }

    var showSortMenu by remember { mutableStateOf(false) }
    var selectedMediaForDetails by remember { mutableStateOf<MediaItemModel?>(null) }

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

    val folders = remember(videos, audios) {
        (videos + audios).groupBy { it.folderName }
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
                    Text("Import Media", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
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

            // Tabs
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
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
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
                            label = { Text(filter.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ThrynxCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ThrynxCyan
                            )
                        )
                    }
                }
            }

            // Content based on tab
            when (selectedTab) {
                0 -> { // Videos
                    val processedVideos = processList(videos)
                    if (processedVideos.isEmpty()) {
                        EmptyStateView(message = "No videos found matching criteria")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(processedVideos) { video ->
                                LibraryMediaRow(
                                    media = video,
                                    onClick = { onOpenMedia(video) },
                                    onInfoClick = { selectedMediaForDetails = video }
                                )
                            }
                        }
                    }
                }
                1 -> { // Audios
                    val processedAudios = processList(audios)
                    if (processedAudios.isEmpty()) {
                        EmptyStateView(message = "No audio files found")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(processedAudios) { audio ->
                                LibraryMediaRow(
                                    media = audio,
                                    onClick = { onOpenMedia(audio) },
                                    onInfoClick = { selectedMediaForDetails = audio }
                                )
                            }
                        }
                    }
                }
                2 -> { // Folders
                    if (selectedFolder != null) {
                        val folderItems = processList(folders[selectedFolder] ?: emptyList())
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolder = null }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ThrynxCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = selectedFolder ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${folderItems.size} items",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(folderItems) { item ->
                                    LibraryMediaRow(
                                        media = item,
                                        onClick = { onOpenMedia(item) },
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
                            items(folders.keys.sorted()) { folderName ->
                                val count = folders[folderName]?.size ?: 0
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
                3 -> { // Favorites
                    val favs = processList((videos + audios).filter { it.isFavorite })
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
}

@Composable
fun LibraryMediaRow(
    media: MediaItemModel,
    onClick: () -> Unit,
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

        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Details",
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
                if (media.artist.isNotBlank()) {
                    DetailRow(label = "Artist", value = media.artist)
                }
                DetailRow(label = "Date Added", value = dateStr)
                DetailRow(label = "Folder", value = media.folderName)
                if (media.path.isNotBlank()) {
                    DetailRow(label = "Location", value = media.path)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan)
            ) {
                Text("Play Now", color = ThrynxBgDark, fontWeight = FontWeight.Bold)
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
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
