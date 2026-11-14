package com.thrynx.app.feature.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thrynx.app.ui.components.VideoThumbnail
import com.thrynx.app.R
import com.thrynx.app.core.database.FavoriteMediaEntity
import com.thrynx.app.core.database.PlaybackHistoryEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    videos: List<MediaItemModel>,
    folders: List<FolderItemModel>,
    recentHistory: List<PlaybackHistoryEntity>,
    favorites: List<FavoriteMediaEntity>,
    hasStoragePermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onOpenMedia: (MediaItemModel, Long) -> Unit,
    onOpenFolder: (FolderItemModel) -> Unit,
    onNavigateToVideos: () -> Unit,
    onOpenStreamUrl: (String) -> Unit,
    privateVideosCount: Int = 0,
    onOpenVault: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { com.thrynx.app.ThrynxApplication.instance.preferencesManager }
    val resumeMode by preferencesManager.resumeModeFlow.collectAsState(initial = "ask")

    var searchQuery by remember { mutableStateOf("") }
    var showStreamDialog by remember { mutableStateOf(false) }
    var streamUrlInput by remember { mutableStateOf("") }

    fun handlePlayRequest(media: MediaItemModel, requestedPosition: Long = 0L) {
        val targetPos = if (requestedPosition > 0L) requestedPosition else media.lastPositionMs
        onOpenMedia(media, targetPos)
    }

    val lastWatched = recentHistory.firstOrNull()

    // Map favorites to media items
    val favoriteVideos = remember(favorites, videos) {
        val favUris = favorites.map { it.uri }.toSet()
        videos.filter { favUris.contains(it.mediaUriString) }
    }

    // Filtered search results
    val searchResults = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        // Brand Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ThrynxCobalt.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ThrynxCardDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_thrynx_brand_mark),
                                    contentDescription = null,
                                    tint = ThrynxCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ThrynxCyan
                                )
                                Text(
                                    text = stringResource(id = R.string.app_tagline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Network Stream Action Icon
                        IconButton(
                            onClick = { showStreamDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ThrynxCyan.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = stringResource(id = R.string.home_open_network_stream),
                                tint = ThrynxCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Video Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(id = R.string.home_search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ThrynxCyan) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = ThrynxCyan
                        )
                    )
                }
            }
        }

        // Storage Permission Warning Banner
        if (!hasStoragePermission) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                text = "Grant media permission to discover and play videos",
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
        }

        // Live Search Results
        if (searchQuery.isNotBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Search Results (${searchResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ThrynxCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (searchResults.isEmpty()) {
                        Text(
                            text = "No videos matching \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(searchResults) { video ->
                VideoRowItem(
                    video = video,
                    onClick = { handlePlayRequest(video, 0L) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        } else {
            // 1. Continue Watching Card
            if (lastWatched != null && lastWatched.durationMs > 0 && !lastWatched.isCompleted) {
                item {
                    val matchingVideo = videos.firstOrNull { it.mediaUriString == lastWatched.uri }
                    val targetMedia = matchingVideo ?: MediaItemModel(
                        title = lastWatched.title,
                        uri = Uri.parse(lastWatched.uri),
                        path = lastWatched.uri,
                        durationMs = lastWatched.durationMs,
                        lastPositionMs = lastWatched.lastPositionMs,
                        isVideo = true
                    )

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(id = R.string.home_continue_watching),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        ThrynxCard(
                            onClick = { handlePlayRequest(targetMedia, lastWatched.lastPositionMs) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ThrynxCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = ThrynxCyan,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lastWatched.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${formatDuration(lastWatched.lastPositionMs)} / ${formatDuration(lastWatched.durationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                val progress = (lastWatched.lastPositionMs.toFloat() / lastWatched.durationMs.toFloat()).coerceIn(0f, 1f)
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = ThrynxCyan,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                }
            }

            // 2. Recently Played Carousel
            if (recentHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(id = R.string.home_recently_played),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentHistory.take(10)) { item ->
                                val matching = videos.firstOrNull { it.mediaUriString == item.uri }
                                val media = matching ?: MediaItemModel(
                                    title = item.title,
                                    uri = Uri.parse(item.uri),
                                    durationMs = item.durationMs,
                                    lastPositionMs = item.lastPositionMs
                                )
                                RecentVideoCard(
                                    title = item.title,
                                    uri = item.uri,
                                    durationMs = item.durationMs,
                                    onClick = { handlePlayRequest(media, item.lastPositionMs) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Recently Added Carousel
            if (videos.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.home_recently_added),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onNavigateToVideos) {
                                Text(
                                    text = stringResource(id = R.string.home_all_videos),
                                    color = ThrynxCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(videos.take(10)) { video ->
                                RecentVideoCard(
                                    title = video.title,
                                    uri = video.mediaUriString,
                                    durationMs = video.durationMs,
                                    resolution = video.resolutionLabel,
                                    onClick = { handlePlayRequest(video, 0L) }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Favorites Carousel
            if (favoriteVideos.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(id = R.string.home_favorites),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favoriteVideos) { video ->
                                RecentVideoCard(
                                    title = video.title,
                                    uri = video.mediaUriString,
                                    durationMs = video.durationMs,
                                    resolution = video.resolutionLabel,
                                    onClick = { handlePlayRequest(video, 0L) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Video Folders Row
            if (folders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(id = R.string.home_folders),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders.take(8)) { folder ->
                                FolderChipCard(
                                    folder = folder,
                                    onClick = { onOpenFolder(folder) }
                                )
                            }
                        }
                    }
                }
            }
            // 6. Private Vault Card Banner on HomeScreen
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(id = R.string.folders_private_vault),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenVault),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ThrynxCardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ThrynxCyan.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ThrynxCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = ThrynxCyan,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.folders_private_vault),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (privateVideosCount > 0) "$privateVideosCount videos protected" else stringResource(id = R.string.folders_private_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ThrynxCyan
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = ThrynxCyan
                            )
                        }
                    }
                }
            }
        }
    }

    // Network Stream Dialog with URL auto-sanitization, YouTube detection & test streams
    if (showStreamDialog) {
        val context = LocalContext.current
        val isYouTubeUrl = remember(streamUrlInput) {
            val lower = streamUrlInput.trim().lowercase()
            lower.contains("youtube.com") || lower.contains("youtu.be")
        }

        AlertDialog(
            onDismissRequest = { showStreamDialog = false },
            title = { Text(stringResource(id = R.string.stream_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.stream_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        placeholder = { Text("https://example.com/video.m3u8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // YouTube URL Warning & External Launch Handoff
                    if (isYouTubeUrl) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "YouTube Link Detected",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "YouTube pages are encrypted web streams and cannot be streamed directly. Open it directly in the YouTube app or browser below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        var url = streamUrlInput.trim()
                                        if (!url.contains("://")) url = "https://$url"
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                            showStreamDialog = false
                                        } catch (e: Exception) {
                                            // Handle activity not found
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ThrynxCyan,
                                        contentColor = ThrynxBgDark
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in YouTube / Browser", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Quick Sample Streams (Direct Media):",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThrynxCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { streamUrlInput = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text("HLS (.m3u8)", fontSize = 10.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { streamUrlInput = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text("MP4 Stream", fontSize = 10.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { streamUrlInput = "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/elephants_dream_480p_heaac5_1.mpd" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text("DASH (.mpd)", fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                if (!isYouTubeUrl) {
                    Button(
                        onClick = {
                            var trimmed = streamUrlInput.trim()
                            if (trimmed.isNotBlank()) {
                                if (!trimmed.contains("://")) {
                                    trimmed = "https://$trimmed"
                                }
                                showStreamDialog = false
                                onOpenStreamUrl(trimmed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                    ) {
                        Text(stringResource(id = R.string.stream_play), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showStreamDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

}

@Composable
fun RecentVideoCard(
    title: String,
    uri: String,
    durationMs: Long,
    resolution: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(170.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp)
                    .background(Color(0xFF141C2B)),
                contentAlignment = Alignment.Center
            ) {
                VideoThumbnail(
                    uri = try { Uri.parse(uri) } catch (_: Exception) { null },
                    path = uri,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ThrynxCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }

                if (!resolution.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ThrynxCobalt.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = resolution,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun FolderChipCard(
    folder: FolderItemModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ThrynxCobalt.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = ThrynxCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.videoCount} videos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VideoRowItem(
    video: MediaItemModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141C2B)),
            contentAlignment = Alignment.Center
        ) {
            VideoThumbnail(
                uri = video.uri,
                path = video.path,
                modifier = Modifier.fillMaxSize()
            )
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(video.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
        }
    }
}
