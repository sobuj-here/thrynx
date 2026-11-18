package com.music.thrynx.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.music.thrynx.R
import com.music.thrynx.data.LocalVideo
import com.music.thrynx.data.LocalVideoRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
        } else {
            requestVideoPermissions()
        }
    }

    // If a video is opened, show the full LocalVideoPlayerScreen
    if (activePlayingList != null) {
        LocalVideoPlayerScreen(
            videoList = activePlayingList!!,
            initialIndex = activePlayingIndex,
            onBack = { activePlayingList = null },
        )
        return
    }

    Scaffold(
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
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
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(folders.keys.toList()) { _, folderName ->
                                val count = folders[folderName]?.size ?: 0
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedFolder = folderName },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
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
                                    }
                                }
                            }
                        }
                    } else {
                        // Videos list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(displayVideos) { index, video ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (onPlayVideo != null) {
                                                onPlayVideo(displayVideos, index)
                                            } else {
                                                activePlayingList = displayVideos
                                                activePlayingIndex = index
                                            }
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Video Thumbnail Container
                                        Box(
                                            modifier = Modifier
                                                .size(width = 110.dp, height = 66.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black),
                                        ) {
                                            var thumbBitmap by remember(video.id) {
                                                mutableStateOf(LocalVideoRepository.getCachedThumbnail(video.id))
                                            }
                                            LaunchedEffect(video.id) {
                                                if (thumbBitmap == null) {
                                                    thumbBitmap = LocalVideoRepository.loadThumbnail(context, video)
                                                }
                                            }

                                            if (thumbBitmap != null) {
                                                Image(
                                                    bitmap = thumbBitmap!!.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            }

                                            // Resolution pill (e.g. 1080p, 4K)
                                            if (video.resolutionLabel.isNotBlank()) {
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.7f),
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
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // Video Details (Title, Folder, Size)
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = video.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
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
