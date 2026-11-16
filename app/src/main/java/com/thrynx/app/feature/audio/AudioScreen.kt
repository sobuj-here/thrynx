package com.thrynx.app.feature.audio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.model.SortOption
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.core.player.YouTubeAudioResolver
import com.thrynx.app.core.player.YouTubeSearchTrack
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioScreen(
    audios: List<MediaItemModel>,
    audioFolders: List<FolderItemModel>,
    playbackState: PlaybackState,
    playerManager: ThrynxPlayerManager,
    lastPlayedUri: String? = null,
    hasStoragePermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onPlayAudio: (MediaItemModel) -> Unit,
    onDeleteAudio: (MediaItemModel) -> Unit,
    onDeleteMultipleAudios: (List<MediaItemModel>) -> Unit = {},
    onRescanLibrary: () -> Unit,
    onPlayOnlineTrack: (YouTubeSearchTrack) -> Unit = {},
    onWatchOnlineVideo: (YouTubeSearchTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val preferencesManager = remember { com.thrynx.app.ThrynxApplication.instance.preferencesManager }
    val audioViewMode by preferencesManager.audioViewModeFlow.collectAsState(initial = "files")
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val database = remember { com.thrynx.app.ThrynxApplication.instance.database }
    val favoriteOnlineTracks by database.favoriteOnlineTrackDao().getAllFavorites().collectAsState(initial = emptyList())
    val downloadingIds by com.thrynx.app.core.player.AudioDownloadManager.downloadingIds.collectAsState()
    val downloadedIds by com.thrynx.app.core.player.AudioDownloadManager.downloadedIds.collectAsState()

    var selectedAudioTab by remember { mutableIntStateOf(0) } // 0 = Local Music, 1 = Online (YouTube Music)
    var onlineQuery by remember { mutableStateOf("") }
    var onlineResults by remember { mutableStateOf<List<YouTubeSearchTrack>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (onlineResults.isEmpty()) {
            isSearchingOnline = true
            try {
                onlineResults = YouTubeAudioResolver.getTrendingTracks()
            } catch (_: Exception) {}
            isSearchingOnline = false
        }
    }

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

    // Apple Music style Now Playing sheet and 10-band Equalizer sheet states
    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }

    BackHandler(
        enabled = showEqualizerSheet || showNowPlayingSheet || selectedFolderDrillDown != null || isSelectionMode || selectedAudioForAction != null || (selectedAudioTab == 0 && searchQuery.isNotEmpty()) || (selectedAudioTab == 1 && onlineQuery.isNotEmpty())
    ) {
        if (showEqualizerSheet) {
            showEqualizerSheet = false
        } else if (showNowPlayingSheet) {
            showNowPlayingSheet = false
        } else if (selectedAudioForAction != null) {
            selectedAudioForAction = null
        } else if (isSelectionMode) {
            isSelectionMode = false
            selectedAudioIds = emptySet()
        } else if (selectedAudioTab == 0 && searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (selectedAudioTab == 1 && onlineQuery.isNotEmpty()) {
            onlineQuery = ""
            coroutineScope.launch {
                isSearchingOnline = true
                onlineResults = YouTubeAudioResolver.getTrendingTracks()
                isSearchingOnline = false
            }
        } else if (selectedFolderDrillDown != null) {
            selectedFolderDrillDown = null
        }
    }

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
                                if (selectedFolderDrillDown == null && selectedAudioTab == 0) {
                                    IconButton(onClick = {
                                        coroutineScope.launch {
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

                                // Equalizer Button
                                IconButton(onClick = { showEqualizerSheet = true }) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = ThrynxCyan)
                                }

                                if (selectedAudioTab == 0) {
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
                        }

                        // Tab Switcher: Local Tracks vs YouTube Music
                        if (selectedFolderDrillDown == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TabRow(
                                selectedTabIndex = selectedAudioTab,
                                containerColor = Color.Transparent,
                                contentColor = ThrynxCyan,
                                divider = {},
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedAudioTab]),
                                        color = ThrynxCyan,
                                        height = 2.5.dp
                                    )
                                }
                            ) {
                                Tab(
                                    selected = selectedAudioTab == 0,
                                    onClick = { selectedAudioTab = 0 },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Local Tracks (${audios.size})", fontWeight = if (selectedAudioTab == 0) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                )
                                Tab(
                                    selected = selectedAudioTab == 1,
                                    onClick = { selectedAudioTab = 1 },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("YouTube Music", fontWeight = if (selectedAudioTab == 1) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedAudioTab == 0) {
                            // Local Audio Search Field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search local songs, artists...", style = MaterialTheme.typography.bodyMedium) },
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
                        } else {
                            // YouTube Music Search Field
                            OutlinedTextField(
                                value = onlineQuery,
                                onValueChange = { onlineQuery = it },
                                placeholder = { Text("Search YouTube Music, songs, artists...", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ThrynxCyan) },
                                trailingIcon = {
                                    if (onlineQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            onlineQuery = ""
                                            coroutineScope.launch {
                                                isSearchingOnline = true
                                                onlineResults = YouTubeAudioResolver.getTrendingTracks()
                                                isSearchingOnline = false
                                            }
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (onlineQuery.isNotBlank()) {
                                        coroutineScope.launch {
                                            isSearchingOnline = true
                                            onlineResults = YouTubeAudioResolver.searchTracks(onlineQuery)
                                            isSearchingOnline = false
                                        }
                                    }
                                }),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ThrynxCyan,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick Mood & Genre Chips (Including Liked Songs)
                            val likedSongsChip = "❤️ Liked Songs (${favoriteOnlineTracks.size})"
                            val genres = remember(favoriteOnlineTracks.size) {
                                listOf(likedSongsChip, "🔥 Trending Hits", "🎶 Top Global", "🇧🇩 Bangla Hits", "🇮🇳 Hindi Songs", "✨ Pop Hits", "🎸 Rock", "🎧 Lofi Beats", "⚡ EDM")
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(genres) { genre ->
                                    FilterChip(
                                        selected = onlineQuery == genre,
                                        onClick = {
                                            if (genre == likedSongsChip) {
                                                onlineQuery = likedSongsChip
                                                onlineResults = favoriteOnlineTracks.map {
                                                    YouTubeSearchTrack(it.videoId, it.title, it.artist, "", it.thumbnailUrl)
                                                }
                                            } else {
                                                onlineQuery = genre
                                                val cleanTerm = genre.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
                                                coroutineScope.launch {
                                                    isSearchingOnline = true
                                                    onlineResults = YouTubeAudioResolver.searchTracks(cleanTerm)
                                                    isSearchingOnline = false
                                                }
                                            }
                                        },
                                        label = { Text(genre, fontSize = 11.sp, maxLines = 1) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ThrynxCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = ThrynxCyan
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Content Area
            if (selectedAudioTab == 1) {
                // ==================== YOUTUBE MUSIC VIEW ====================
                if (isSearchingOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ThrynxCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Searching YouTube Music...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (onlineResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MusicOff,
                                contentDescription = null,
                                tint = ThrynxCyan.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (onlineQuery.isNotBlank()) "No songs found for '$onlineQuery'" else "Search any song, artist or album",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap any genre chip above or search to explore YouTube Music",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = if (onlineQuery.isNotBlank()) "Search Results (${onlineResults.size})" else "Trending on YouTube Music",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ThrynxCyan,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(onlineResults, key = { it.videoId }) { track ->
                            val isPlayingThis = playbackState.currentMedia?.path?.contains(track.videoId) == true
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPlayingThis) ThrynxCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isPlayingThis) BorderStroke(1.5.dp, ThrynxCyan) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayOnlineTrack(track) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF141C2B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (track.thumbnailUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = track.thumbnailUrl,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis && playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = ThrynxCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPlayingThis) ThrynxCyan else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Like / Favorite button
                                    val isFav = favoriteOnlineTracks.any { it.videoId == track.videoId }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                if (isFav) {
                                                    database.favoriteOnlineTrackDao().removeFavorite(track.videoId)
                                                } else {
                                                    database.favoriteOnlineTrackDao().addFavorite(
                                                        com.thrynx.app.core.database.FavoriteOnlineTrackEntity(
                                                            videoId = track.videoId,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            thumbnailUrl = track.thumbnailUrl
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (isFav) Color(0xFFFF4081) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Download for Offline Listening button
                                    val isDownloading = downloadingIds.contains(track.videoId)
                                    val isDownloaded = downloadedIds.contains(track.videoId)
                                    IconButton(
                                        onClick = {
                                            if (!isDownloading) {
                                                coroutineScope.launch {
                                                    android.widget.Toast.makeText(context, "Downloading '${track.title}'...", android.widget.Toast.LENGTH_SHORT).show()
                                                    com.thrynx.app.core.player.AudioDownloadManager.downloadTrack(
                                                        context = context,
                                                        videoId = track.videoId,
                                                        title = track.title,
                                                        artist = track.artist,
                                                        onSuccess = {
                                                            android.widget.Toast.makeText(context, "Downloaded: $it", android.widget.Toast.LENGTH_SHORT).show()
                                                            onRescanLibrary()
                                                        },
                                                        onError = { err ->
                                                            android.widget.Toast.makeText(context, "Download failed: $err", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                color = ThrynxCyan,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                contentDescription = "Download Offline",
                                                tint = if (isDownloaded) ThrynxCyan else Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Watch as Video action button
                                    IconButton(
                                        onClick = { onWatchOnlineVideo(track) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = "Watch Video",
                                            tint = ThrynxCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!hasStoragePermission) {
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(processedAudios, key = { it.id }) { audio ->
                            val isSelected = selectedAudioIds.contains(audio.id)
                            val isPlaying = playbackState.currentMedia?.uri == audio.uri ||
                                    (playbackState.currentMedia?.id == audio.id)
                            val isLastPlayed = (audio.mediaUriString == lastPlayedUri)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isPlaying) ThrynxCyan.copy(alpha = 0.12f) else if (isLastPlayed) ThrynxCobalt.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isPlaying || isLastPlayed) androidx.compose.foundation.BorderStroke(1.5.dp, ThrynxCyan) else null,
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
                                        val artUri = audio.artworkUri ?: audio.uri
                                        if (artUri != null) {
                                            AsyncImage(
                                                model = artUri,
                                                contentDescription = audio.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying && playbackState.isPlaying) Icons.Default.Audiotrack else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isPlaying) ThrynxBgDark else ThrynxCyan,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        if (audio.isNew) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(ThrynxCyan)
                                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = "NEW",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ThrynxBgDark,
                                                    fontSize = 6.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
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
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (audio.isNew && !isPlaying) {
                                                Surface(
                                                    color = ThrynxCyan,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "NEW",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ThrynxBgDark,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            } else if (isLastPlayed && !isPlaying) {
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
                                        if (audio.lastPositionMs > 0L) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Played ${formatDuration(audio.lastPositionMs)} / ${formatDuration(audio.durationMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ThrynxCyan,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (audio.lastPositionMs > 0L && audio.durationMs > 0L) {
                                            val progress = (audio.lastPositionMs.toFloat() / audio.durationMs.toFloat()).coerceIn(0f, 1f)
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                color = ThrynxCyan,
                                                trackColor = Color.DarkGray,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                                    .height(2.5.dp)
                                                    .clip(RoundedCornerShape(2.dp))
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

        // Frozen Audio Bottom Bar (Pinned/Docked at the bottom of AudioScreen)
        AnimatedVisibility(
            visible = playbackState.currentMedia != null && !playbackState.currentMedia!!.isVideo,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            playbackState.currentMedia?.let { activeSong ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { showNowPlayingSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    color = ThrynxCardDark,
                    tonalElevation = 12.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, ThrynxCyan.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val currentPos = playbackState.currentPositionMs.toFloat()
                        val duration = playbackState.durationMs.toFloat().coerceAtLeast(1f)
                        val progressFraction = (currentPos / duration).coerceIn(0f, 1f)

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = ThrynxCyan,
                            trackColor = ThrynxTimelineTrack
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2838)),
                                contentAlignment = Alignment.Center
                            ) {
                                val artModel = activeSong.artworkUri ?: activeSong.uri
                                if (artModel != null) {
                                    AsyncImage(
                                        model = artModel,
                                        contentDescription = activeSong.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = ThrynxCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeSong.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ThrynxCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            IconButton(
                                onClick = { playerManager.seekTo((playbackState.currentPositionMs - 10000L).coerceAtLeast(0L)) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Replay10,
                                    contentDescription = "Replay 10s",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = { playerManager.togglePlayPause() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = ThrynxCyan,
                                    contentColor = ThrynxBgDark
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = { playerManager.seekTo((playbackState.currentPositionMs + 10000L).coerceAtMost(playbackState.durationMs)) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { playerManager.playNext() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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

    // Apple Music Style Now Playing Fullscreen Sheet
    if (showNowPlayingSheet) {
        NowPlayingSheet(
            playerManager = playerManager,
            onDismiss = { showNowPlayingSheet = false },
            onOpenEqualizer = {
                showNowPlayingSheet = false
                showEqualizerSheet = true
            }
        )
    }

    // 10-Band Parametric Equalizer & DSP Sheet
    if (showEqualizerSheet) {
        EqualizerSheet(
            playerManager = playerManager,
            onDismiss = { showEqualizerSheet = false }
        )
    }
}
