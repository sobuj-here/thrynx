package com.thrynx.app.feature.folders

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thrynx.app.R
import com.thrynx.app.core.database.CustomFolderEntity
import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel

enum class FolderSortOption(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    COUNT_DESC("Videos (Most first)"),
    COUNT_ASC("Videos (Least first)")
}

@Composable
fun FoldersScreen(
    folders: List<FolderItemModel>,
    videos: List<MediaItemModel>,
    hasStoragePermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onOpenMedia: (MediaItemModel) -> Unit,
    onPlayAll: (List<MediaItemModel>) -> Unit,
    onHideFolder: (FolderItemModel) -> Unit = {},
    onDeleteFolder: (FolderItemModel) -> Unit = {},
    onHideMultipleFolders: (List<FolderItemModel>) -> Unit = {},
    onDeleteMultipleFolders: (List<FolderItemModel>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFolder by remember { mutableStateOf<FolderItemModel?>(null) }
    var sortOption by remember { mutableStateOf(FolderSortOption.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Multi-select & Folder Action States
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedFolderPaths by remember { mutableStateOf(setOf<String>()) }
    var folderToDelete by remember { mutableStateOf<FolderItemModel?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    // Process and sort folders
    val processedFolders = remember(folders, sortOption) {
        when (sortOption) {
            FolderSortOption.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            FolderSortOption.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            FolderSortOption.COUNT_DESC -> folders.sortedByDescending { it.videoCount }
            FolderSortOption.COUNT_ASC -> folders.sortedBy { it.videoCount }
        }
    }

    // Handle back button when inside a folder or in selection mode
    BackHandler(enabled = selectedFolder != null || isSelectMode) {
        if (isSelectMode) {
            isSelectMode = false
            selectedFolderPaths = emptySet()
        } else {
            selectedFolder = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedFolder != null) {
                // Inside Specific Video Folder View
                val folder = selectedFolder!!
                val folderVideos = remember(videos, folder) {
                    videos.filter { it.folderName.equals(folder.name, ignoreCase = true) }
                }

                FolderDetailView(
                    folder = folder,
                    videos = folderVideos,
                    onBack = { selectedFolder = null },
                    onOpenMedia = onOpenMedia,
                    onPlayAll = { onPlayAll(folderVideos) }
                )
            } else {
                // Main Folders Overview Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isSelectMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isSelectMode = false
                                selectedFolderPaths = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.folders_selected_count, selectedFolderPaths.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ThrynxCyan
                            )
                        }

                        IconButton(onClick = {
                            selectedFolderPaths = if (selectedFolderPaths.size == processedFolders.size) {
                                emptySet()
                            } else {
                                processedFolders.map { it.path }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = ThrynxCyan)
                        }
                    } else {
                        Column {
                            Text(
                                text = stringResource(id = R.string.folders_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = ThrynxCyan
                            )
                            Text(
                                text = "${processedFolders.size} video folders",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Select Folders Mode Button
                            IconButton(onClick = { isSelectMode = true }) {
                                Icon(Icons.Default.Checklist, contentDescription = stringResource(id = R.string.folders_select_mode), tint = Color.White)
                            }

                            // Sort Folders Menu
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Folders", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    FolderSortOption.values().forEach { option ->
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
                        }
                    }
                }

                // Storage Permission Missing Warning Banner
                if (!hasStoragePermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage Permission Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Allow access to discover all videos on your device",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                            ) {
                                Text("Grant", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Folder List
                if (processedFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.folders_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (isSelectMode) 140.dp else 100.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(processedFolders) { folder ->
                            FolderRowCard(
                                folder = folder,
                                isSelectMode = isSelectMode,
                                isSelected = selectedFolderPaths.contains(folder.path),
                                onToggleSelect = {
                                    selectedFolderPaths = if (selectedFolderPaths.contains(folder.path)) {
                                        selectedFolderPaths - folder.path
                                    } else {
                                        selectedFolderPaths + folder.path
                                    }
                                },
                                onHide = { onHideFolder(folder) },
                                onDelete = { folderToDelete = folder },
                                onEnterSelectMode = {
                                    isSelectMode = true
                                    selectedFolderPaths = setOf(folder.path)
                                },
                                onClick = {
                                    if (isSelectMode) {
                                        selectedFolderPaths = if (selectedFolderPaths.contains(folder.path)) {
                                            selectedFolderPaths - folder.path
                                        } else {
                                            selectedFolderPaths + folder.path
                                        }
                                    } else {
                                        selectedFolder = folder
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sticky Bottom Bar for Multi-select Actions
        if (isSelectMode && selectedFolderPaths.isNotEmpty()) {
            val selectedItems = remember(processedFolders, selectedFolderPaths) {
                processedFolders.filter { selectedFolderPaths.contains(it.path) }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = ThrynxCardDark,
                tonalElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.folders_selected_count, selectedFolderPaths.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onHideMultipleFolders(selectedItems)
                                isSelectMode = false
                                selectedFolderPaths = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = ThrynxCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.folders_hide_selected), color = ThrynxCyan)
                        }
                        Button(
                            onClick = { showBulkDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.folders_delete_selected))
                        }
                    }
                }
            }
        }
    }

    // Single Folder Delete Confirmation Dialog
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(id = R.string.folders_delete)) },
            text = {
                Text(stringResource(id = R.string.folders_delete_confirm, folder.videoCount, folder.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = folderToDelete
                        folderToDelete = null
                        if (target != null) onDeleteFolder(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.videos_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirm) {
        val selectedItems = remember(processedFolders, selectedFolderPaths) {
            processedFolders.filter { selectedFolderPaths.contains(it.path) }
        }
        val totalVideos = selectedItems.sumOf { it.videoCount }
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(stringResource(id = R.string.folders_delete_selected)) },
            text = {
                Text("Permanently delete all $totalVideos videos across the ${selectedItems.size} selected folders?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkDeleteConfirm = false
                        onDeleteMultipleFolders(selectedItems)
                        isSelectMode = false
                        selectedFolderPaths = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.videos_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRowCard(
    folder: FolderItemModel,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onHide: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEnterSelectMode: () -> Unit = {},
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectMode) {
                        onToggleSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isSelectMode) {
                        onEnterSelectMode()
                    } else {
                        onToggleSelect()
                    }
                }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ThrynxCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, ThrynxCyan) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThrynxBgDark),
                contentAlignment = Alignment.Center
            ) {
                if (folder.latestMedia != null) {
                    AsyncImage(
                        model = folder.latestMedia.uri ?: folder.latestMedia.path,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = ThrynxCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${folder.videoCount} videos • ${folder.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Folder options",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.folders_hide)) },
                            leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null, tint = ThrynxCyan) },
                            onClick = {
                                showMenu = false
                                onHide()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.folders_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.folders_select_mode)) },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEnterSelectMode()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDetailView(
    folder: FolderItemModel,
    videos: List<MediaItemModel>,
    onBack: () -> Unit,
    onOpenMedia: (MediaItemModel) -> Unit,
    onPlayAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${videos.size} videos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (videos.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMedia(video) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ThrynxCardDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = video.uri ?: video.path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatDuration(video.durationMs)} • ${formatFileSize(video.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = ThrynxCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultContentView(
    privateVideos: List<PrivateVideoEntity>,
    onBack: () -> Unit,
    onOpenVideo: (PrivateVideoEntity) -> Unit,
    onUnhide: (PrivateVideoEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.folders_private_vault),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ThrynxCyan
                )
                Text(
                    text = "${privateVideos.size} hidden videos protected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (privateVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = ThrynxCyan.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Vault is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Move sensitive videos here from the Videos tab action menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(privateVideos) { pv ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenVideo(pv) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ThrynxCardDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = pv.mediaUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pv.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatDuration(pv.durationMs)} • ${formatFileSize(pv.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onUnhide(pv) }) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = "Unhide",
                                    tint = ThrynxCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
