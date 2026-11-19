package com.music.thrynx.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.thrynx.R
import com.music.thrynx.data.LocalVideo
import com.music.thrynx.data.LocalVideoRepository
import com.music.thrynx.data.MediaDeletionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalVideosScreen(
    onBack: () -> Unit,
    onPlayVideo: ((List<LocalVideo>, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(LocalVideoRepository.hasVideoPermission(context)) }
    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var folders by remember { mutableStateOf<Map<String, List<LocalVideo>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Folders
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    // Multi-selection & Deletion state
    var selectedVideoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedFolderNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedVideoIds.isNotEmpty() || selectedFolderNames.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var lastPlayedId by remember { mutableLongStateOf(LocalVideoRepository.getLastPlayedVideoId(context)) }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedVideoIds = emptySet()
            selectedFolderNames = emptySet()
            scope.launch {
                isLoading = true
                videos = LocalVideoRepository.queryVideos(context)
                folders = LocalVideoRepository.queryFolders(context)
                isLoading = false
                Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Playing state
    var activePlayingList by remember { mutableStateOf<List<LocalVideo>?>(null) }
    var activePlayingIndex by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        val granted = perms.values.any { it } || LocalVideoRepository.hasVideoPermission(context)
        hasPermission = granted
        if (granted) {
            scope.launch {
                isLoading = true
                videos = LocalVideoRepository.queryVideos(context)
                folders = LocalVideoRepository.queryFolders(context)
                isLoading = false
            }
        }
    }

    fun requestVideoPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            videos = LocalVideoRepository.queryVideos(context)
            folders = LocalVideoRepository.queryFolders(context)
            isLoading = false
            lastPlayedId = LocalVideoRepository.getLastPlayedVideoId(context)
        } else {
            requestVideoPermissions()
        }
    }

    // If a video is opened, show the full LocalVideoPlayerScreen
    if (activePlayingList != null) {
        LocalVideoPlayerScreen(
            videoList = activePlayingList!!,
            initialIndex = activePlayingIndex,
            onBack = {
                activePlayingList = null
                lastPlayedId = LocalVideoRepository.getLastPlayedVideoId(context)
            },
        )
        return
    }

    Scaffold(
        topBar = {
            val isFolderTab = selectedFolder == null && selectedTab == 1
            val displayVideos = if (selectedFolder != null) {
                folders[selectedFolder].orEmpty()
            } else if (selectedTab == 0) {
                videos
            } else {
                emptyList()
            }

            if (isSelectionMode) {
                val selectionCount = if (isFolderTab) selectedFolderNames.size else selectedVideoIds.size
                TopAppBar(
                    title = {
                        Text(
                            text = "$selectionCount ${stringResource(R.string.selected)}",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedVideoIds = emptySet()
                            selectedFolderNames = emptySet()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        if (isFolderTab) {
                            val allFoldersSelected = folders.keys.isNotEmpty() && folders.keys.all { it in selectedFolderNames }
                            IconButton(
                                onClick = {
                                    selectedFolderNames = if (allFoldersSelected) emptySet() else folders.keys.toSet()
                                },
                            ) {
                                Icon(
                                    imageVector = if (allFoldersSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = stringResource(if (allFoldersSelected) R.string.deselect_all else R.string.select_all),
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                enabled = selectedFolderNames.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = if (selectedFolderNames.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray,
                                )
                            }
                        } else {
                            val allSelected = displayVideos.isNotEmpty() && displayVideos.all { it.id in selectedVideoIds }
                            IconButton(
                                onClick = {
                                    selectedVideoIds = if (allSelected) emptySet() else displayVideos.map { it.id }.toSet()
                                },
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = stringResource(if (allSelected) R.string.deselect_all else R.string.select_all),
                                )
                            }
                            IconButton(
                                onClick = { showDeleteConfirmDialog = true },
                                enabled = selectedVideoIds.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = if (selectedVideoIds.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedFolder != null) selectedFolder!! else stringResource(R.string.local_video),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (selectedFolder != null) {
                                    selectedFolder = null
                                } else {
                                    onBack()
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (isFolderTab && folders.isNotEmpty()) {
                            IconButton(onClick = {
                                selectedFolderNames = setOf(folders.keys.first())
                            }) {
                                Icon(Icons.Rounded.Checklist, contentDescription = stringResource(R.string.select_all))
                            }
                        } else if (displayVideos.isNotEmpty()) {
                            IconButton(onClick = {
                                selectedVideoIds = setOf(displayVideos.first().id)
                            }) {
                                Icon(Icons.Rounded.Checklist, contentDescription = stringResource(R.string.select_all))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (!hasPermission) {
                // Permission Request State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.storage_required_read),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { requestVideoPermissions() },
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else if (isLoading) {
                // Loading spinner
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (videos.isEmpty()) {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_videos_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (selectedFolder == null) {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(R.string.all_videos)) },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(R.string.folders)) },
                            )
                        }
                    }

                    val displayVideos = if (selectedFolder != null) {
                        folders[selectedFolder].orEmpty()
                    } else if (selectedTab == 0) {
                        videos
                    } else {
                        emptyList()
                    }

                    if (selectedFolder == null && selectedTab == 1) {
                        // Folders list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(folders.keys.toList()) { _, folderName ->
                                val count = folders[folderName]?.size ?: 0
                                val isFolderSelected = folderName in selectedFolderNames
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isFolderSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    border = if (isFolderSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedFolderNames = if (isFolderSelected) selectedFolderNames - folderName else selectedFolderNames + folderName
                                                } else {
                                                    selectedFolder = folderName
                                                }
                                            },
                                            onLongClick = {
                                                selectedFolderNames = if (isFolderSelected) selectedFolderNames - folderName else selectedFolderNames + folderName
                                            },
                                        ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = if (isFolderSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = folderName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "$count videos",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        if (isSelectionMode) {
                                            Surface(
                                                color = if (isFolderSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                if (isFolderSelected) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(3.dp),
                                                    )
                                                }
                                            }
                                        } else {
                                            var showFolderMenu by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { showFolderMenu = true }) {
                                                    Icon(
                                                        Icons.Rounded.MoreVert,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showFolderMenu,
                                                    onDismissRequest = { showFolderMenu = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(R.string.delete),
                                                                color = MaterialTheme.colorScheme.error,
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Delete,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error,
                                                            )
                                                        },
                                                        onClick = {
                                                            showFolderMenu = false
                                                            selectedFolderNames = setOf(folderName)
                                                            selectedVideoIds = emptySet()
                                                            showDeleteConfirmDialog = true
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Videos list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(displayVideos) { index, video ->
                                val isSelected = video.id in selectedVideoIds
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedVideoIds = if (isSelected) selectedVideoIds - video.id else selectedVideoIds + video.id
                                                } else {
                                                    LocalVideoRepository.setLastPlayedVideoId(context, video.id)
                                                    lastPlayedId = video.id
                                                    if (onPlayVideo != null) {
                                                        onPlayVideo(displayVideos, index)
                                                    } else {
                                                        activePlayingList = displayVideos
                                                        activePlayingIndex = index
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                selectedVideoIds = if (isSelected) selectedVideoIds - video.id else selectedVideoIds + video.id
                                            },
                                        ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Thumbnail
                                        Box(
                                            modifier = Modifier
                                                .size(width = 120.dp, height = 75.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.2f)),
                                        ) {
                                            var thumbnail by remember(video.id) { mutableStateOf<Bitmap?>(null) }
                                            LaunchedEffect(video.id) {
                                                thumbnail = LocalVideoRepository.loadThumbnail(context, video)
                                            }

                                            if (thumbnail != null) {
                                                Image(
                                                    bitmap = thumbnail!!.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Rounded.PlayCircle,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .align(Alignment.Center),
                                                )
                                            }

                                            // Resolution pill (e.g. 1080p, 4K)
                                            if (video.resolutionLabel.isNotEmpty()) {
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(4.dp),
                                                ) {
                                                    Text(
                                                        text = video.resolutionLabel,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    )
                                                }
                                            }

                                            // Duration pill (e.g. 3:45)
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.75f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp),
                                            ) {
                                                Text(
                                                    text = video.formattedDuration,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }

                                            // Watch progress bar (if resumed)
                                            val resumePos = remember(video.id) { LocalVideoRepository.getResumePosition(context, video.id) }
                                            if (resumePos > 3000L && video.durationMs > 0L) {
                                                val progress = (resumePos.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .background(Color.White.copy(alpha = 0.35f)),
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(progress)
                                                            .height(3.dp)
                                                            .background(MaterialTheme.colorScheme.primary),
                                                    )
                                                }
                                            }

                                            // Selection checkmark on thumbnail
                                            if (isSelectionMode) {
                                                Surface(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
                                                    shape = CircleShape,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(5.dp)
                                                        .size(22.dp),
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.padding(3.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // Video Details (Title, Folder, Size)
                                        Column(Modifier.weight(1f)) {
                                            val isRecentlyPlayed = video.id == lastPlayedId
                                            Text(
                                                text = video.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isRecentlyPlayed) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isRecentlyPlayed) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "${video.folderName} · ${video.formattedSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }

                                        // 3-dot Menu when not in selection mode
                                        if (!isSelectionMode) {
                                            var showVideoMenu by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { showVideoMenu = true }) {
                                                    Icon(
                                                        Icons.Rounded.MoreVert,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showVideoMenu,
                                                    onDismissRequest = { showVideoMenu = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.play)) },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.PlayArrow,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            showVideoMenu = false
                                                            LocalVideoRepository.setLastPlayedVideoId(context, video.id)
                                                            lastPlayedId = video.id
                                                            if (onPlayVideo != null) {
                                                                onPlayVideo(displayVideos, index)
                                                            } else {
                                                                activePlayingList = displayVideos
                                                                activePlayingIndex = index
                                                            }
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(R.string.delete),
                                                                color = MaterialTheme.colorScheme.error,
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Delete,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error,
                                                            )
                                                        },
                                                        onClick = {
                                                            showVideoMenu = false
                                                            selectedVideoIds = setOf(video.id)
                                                            selectedFolderNames = emptySet()
                                                            showDeleteConfirmDialog = true
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Bottom Selection Bar (Highly visible Delete option)
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                val isFolderTab = selectedFolder == null && selectedTab == 1
                val selectionCount = if (isFolderTab) selectedFolderNames.size else selectedVideoIds.size
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 10.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$selectionCount ${stringResource(R.string.selected)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.delete),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        val hasSelection = selectedVideoIds.isNotEmpty() || selectedFolderNames.isNotEmpty()
        if (showDeleteConfirmDialog && hasSelection) {
            val allAvailableVideos = (videos + folders.values.flatten()).distinctBy { it.id }
            val folderVideos = selectedFolderNames.flatMap { folders[it].orEmpty() }
            val explicitVideos = allAvailableVideos.filter { it.id in selectedVideoIds }
            val toDelete = (folderVideos + explicitVideos).distinctBy { it.id }

            val deleteMessage = if (selectedFolderNames.isNotEmpty()) {
                "Delete ${selectedFolderNames.size} folder(s) (${toDelete.size} video(s)) permanently from your device?"
            } else {
                "Delete ${toDelete.size} video(s) permanently from your device?"
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    if (!isSelectionMode) {
                        selectedVideoIds = emptySet()
                        selectedFolderNames = emptySet()
                    }
                },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(deleteMessage) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            val uris = toDelete.map { it.contentUri }
                            val paths = toDelete.map { it.path }
                            when (val res = MediaDeletionHelper.deleteMediaUris(context, uris, paths)) {
                                is MediaDeletionHelper.DeleteResult.RequiresConsent -> {
                                    val req = IntentSenderRequest.Builder(res.intentSender).build()
                                    deleteIntentLauncher.launch(req)
                                }
                                is MediaDeletionHelper.DeleteResult.Success -> {
                                    selectedVideoIds = emptySet()
                                    selectedFolderNames = emptySet()
                                    scope.launch {
                                        isLoading = true
                                        videos = LocalVideoRepository.queryVideos(context)
                                        folders = LocalVideoRepository.queryFolders(context)
                                        isLoading = false
                                        Toast.makeText(context, "${toDelete.size} video(s) deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is MediaDeletionHelper.DeleteResult.Error -> {}
                            }
                        },
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showDeleteConfirmDialog = false
                        if (!isSelectionMode) {
                            selectedVideoIds = emptySet()
                            selectedFolderNames = emptySet()
                        }
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}
