package com.thrynx.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.thrynx.app.R
import com.thrynx.app.core.database.FavoriteMediaEntity
import com.thrynx.app.core.database.PlaybackHistoryEntity
import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.feature.folders.FoldersScreen
import com.thrynx.app.feature.home.HomeScreen
import com.thrynx.app.feature.player.VideoPlayerScreen
import com.thrynx.app.feature.settings.SettingsScreen
import com.thrynx.app.feature.videos.VideosScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Videos : Screen("videos", R.string.nav_videos, Icons.Default.VideoLibrary)
    object Folders : Screen("folders", R.string.nav_folders, Icons.Default.Folder)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    object Player : Screen("player", R.string.player_title, Icons.Default.PlayCircle)
}

@Composable
fun ThrynxNavigationApp(
    navController: NavHostController,
    playerManager: ThrynxPlayerManager,
    videos: List<MediaItemModel>,
    folders: List<FolderItemModel>,
    recentHistory: List<PlaybackHistoryEntity>,
    favorites: List<FavoriteMediaEntity>,
    privateVideos: List<PrivateVideoEntity>,
    customFolders: List<com.thrynx.app.core.database.CustomFolderEntity> = emptyList(),
    vaultPin: String,
    hasStoragePermission: Boolean = true,
    canUseBiometric: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onSetVaultPin: (String) -> Unit,
    onUnhidePrivateVideo: (PrivateVideoEntity) -> Unit,
    onHideInVault: (MediaItemModel) -> Unit,
    onDeleteMedia: (MediaItemModel) -> Unit,
    onRenameMedia: (MediaItemModel, String) -> Unit,
    onAddCustomFolder: (android.net.Uri) -> Unit,
    onRemoveCustomFolder: (String) -> Unit = {},
    onToggleFavorite: (MediaItemModel) -> Unit,
    onRescanLibrary: () -> Unit,
    onEnterPiP: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLaunchBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    val playbackState by playerManager.playbackState.collectAsState()

    val favoritesUris = remember(favorites) { favorites.map { it.uri }.toSet() }

    // Exactly 4 Navigation Tabs as Required
    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Videos,
        Screen.Folders,
        Screen.Settings
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute != Screen.Player.route) {
                Column {
                    // Persistent Mini-Player Bar
                    playbackState.currentMedia?.let {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = { playerManager.togglePlayPause() },
                            onStop = { playerManager.stop() },
                            onDismiss = { playerManager.dismiss() },
                            onClick = {
                                navController.navigate(Screen.Player.route) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(screen.icon, contentDescription = stringResource(id = screen.titleRes))
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = screen.titleRes),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ThrynxCyan,
                                    selectedTextColor = ThrynxCyan,
                                    indicatorColor = ThrynxCyan.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Home Screen
            composable(Screen.Home.route) {
                HomeScreen(
                    videos = videos,
                    folders = folders,
                    recentHistory = recentHistory,
                    favorites = favorites,
                    hasStoragePermission = hasStoragePermission,
                    onRequestPermission = onRequestPermission,
                    onOpenMedia = { media, pos ->
                        playerManager.playMedia(media, pos)
                        navController.navigate(Screen.Player.route) { launchSingleTop = true }
                    },
                    onOpenFolder = { _ ->
                        navController.navigate(Screen.Folders.route) { launchSingleTop = true }
                    },
                    onNavigateToVideos = {
                        navController.navigate(Screen.Videos.route) { launchSingleTop = true }
                    },
                    onOpenStreamUrl = { url ->
                        val streamMedia = MediaItemModel(
                            id = System.currentTimeMillis(),
                            title = "Network Stream",
                            uri = android.net.Uri.parse(url),
                            path = url,
                            isVideo = true
                        )
                        playerManager.playMedia(streamMedia)
                        navController.navigate(Screen.Player.route) { launchSingleTop = true }
                    }
                )
            }

            // 2. Videos Screen
            composable(Screen.Videos.route) {
                VideosScreen(
                    videos = videos,
                    favoritesUris = favoritesUris,
                    hasStoragePermission = hasStoragePermission,
                    onRequestPermission = onRequestPermission,
                    onOpenMedia = { media ->
                        val idx = videos.indexOf(media)
                        if (idx >= 0) {
                            playerManager.playQueue(videos, idx)
                        } else {
                            playerManager.playMedia(media)
                        }
                        navController.navigate(Screen.Player.route) { launchSingleTop = true }
                    },
                    onToggleFavorite = onToggleFavorite,
                    onHideInVault = onHideInVault,
                    onDeleteMedia = onDeleteMedia,
                    onRenameMedia = onRenameMedia,
                    onRescanLibrary = onRescanLibrary
                )
            }

            // 3. Folders Screen
            composable(Screen.Folders.route) {
                FoldersScreen(
                    folders = folders,
                    videos = videos,
                    privateVideos = privateVideos,
                    customFolders = customFolders,
                    vaultPin = vaultPin,
                    hasStoragePermission = hasStoragePermission,
                    canUseBiometric = canUseBiometric,
                    onRequestPermission = onRequestPermission,
                    onSetVaultPin = onSetVaultPin,
                    onUnhidePrivateVideo = onUnhidePrivateVideo,
                    onAddCustomFolder = onAddCustomFolder,
                    onRemoveCustomFolder = onRemoveCustomFolder,
                    onOpenMedia = { media ->
                        playerManager.playMedia(media)
                        navController.navigate(Screen.Player.route) { launchSingleTop = true }
                    },
                    onPlayAll = { folderVideos ->
                        if (folderVideos.isNotEmpty()) {
                            playerManager.playQueue(folderVideos, 0)
                            navController.navigate(Screen.Player.route) { launchSingleTop = true }
                        }
                    },
                    onLaunchBiometric = onLaunchBiometric
                )
            }

            // 4. Settings Screen
            composable(Screen.Settings.route) {
                SettingsScreen(
                    preferencesManager = com.thrynx.app.ThrynxApplication.instance.preferencesManager,
                    onLanguageChanged = onLanguageChanged,
                    onClearHistory = onClearHistory,
                    onRescanLibrary = onRescanLibrary
                )
            }

            // Video Player Screen
            composable(Screen.Player.route) {
                VideoPlayerScreen(
                    playerManager = playerManager,
                    onBack = { navController.popBackStack() },
                    onEnterPiP = onEnterPiP
                )
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMedia = playbackState.currentMedia ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = ThrynxCardDark,
        tonalElevation = 8.dp
    ) {
        Column {
            // Linear Progress Indicator
            val progress = if (playbackState.durationMs > 0) {
                (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ThrynxCyan,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ThrynxCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = ThrynxCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentMedia.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThrynxTextSecondaryDark
                    )
                }

                // Play / Pause Button
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = ThrynxCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Stop Button
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Dismiss / Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
