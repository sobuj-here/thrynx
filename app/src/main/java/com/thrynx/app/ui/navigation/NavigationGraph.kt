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
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.player.ThrynxPlayerManager
import com.thrynx.app.feature.downloads.DownloadsScreen
import com.thrynx.app.feature.editor.EditorScreen
import com.thrynx.app.feature.home.HomeScreen
import com.thrynx.app.feature.home.formatDuration
import com.thrynx.app.feature.library.LibraryScreen
import com.thrynx.app.feature.music.MusicPlayerScreen
import com.thrynx.app.feature.online.OnlineScreen
import com.thrynx.app.feature.photomaker.PhotoMakerScreen
import com.thrynx.app.feature.player.VideoPlayerScreen
import com.thrynx.app.feature.projects.ProjectsScreen
import com.thrynx.app.feature.settings.SettingsScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Player : Screen("player", R.string.nav_player, Icons.Default.PlayCircle)
    object Music : Screen("music", R.string.nav_music, Icons.Default.MusicNote)
    object Editor : Screen("editor", R.string.nav_editor, Icons.Default.MovieFilter)
    object Library : Screen("library", R.string.nav_library, Icons.Default.Folder)
    object Online : Screen("online", R.string.nav_online, Icons.Default.Language)
    object PhotoMaker : Screen("photo_maker", R.string.nav_photo_maker, Icons.Default.PhotoLibrary)
    object Projects : Screen("projects", R.string.nav_projects, Icons.Default.FolderSpecial)
    object Downloads : Screen("downloads", R.string.nav_downloads, Icons.Default.Download)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun ThrynxNavigationApp(
    navController: NavHostController,
    playerManager: ThrynxPlayerManager,
    videos: List<MediaItemModel>,
    audios: List<MediaItemModel>,
    images: List<MediaItemModel>,
    projects: List<ProjectModel>,
    downloads: List<com.thrynx.app.core.database.DownloadEntity>,
    lastWatched: com.thrynx.app.core.database.PlaybackHistoryEntity?,
    onSaveProject: (ProjectModel) -> Unit,
    onDeleteProject: (ProjectModel) -> Unit,
    onDuplicateProject: (ProjectModel) -> Unit,
    onExportProject: (ProjectModel) -> Unit,
    onEnterPiP: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route
    val playbackState by playerManager.playbackState.collectAsState()

    var activeEditingProject by remember { mutableStateOf<ProjectModel?>(null) }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Editor,
        Screen.Music,
        Screen.Library,
        Screen.Settings
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute != Screen.Player.route && currentRoute != Screen.Editor.route) {
                Column {
                    // Persistent Mini-Player Bar
                    playbackState.currentMedia?.let { current ->
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = { playerManager.togglePlayPause() },
                            onClick = {
                                if (current.isVideo) {
                                    navController.navigate(Screen.Player.route)
                                } else {
                                    navController.navigate(Screen.Music.route)
                                }
                            }
                        )
                    }

                    // Navigation Bar
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
            composable(Screen.Home.route) {
                HomeScreen(
                    recentProjects = projects,
                    recentMedia = (videos + audios).sortedByDescending { it.dateAdded },
                    lastWatchedHistory = lastWatched,
                    onOpenMedia = { media ->
                        if (media.isVideo) {
                            playerManager.playMedia(media, media.lastPositionMs)
                            navController.navigate(Screen.Player.route)
                        } else {
                            playerManager.playMedia(media, 0L)
                            navController.navigate(Screen.Music.route)
                        }
                    },
                    onOpenProject = { proj ->
                        activeEditingProject = proj
                        navController.navigate(Screen.Editor.route)
                    },
                    onNewProject = {
                        activeEditingProject = ProjectModel(name = "Project_${System.currentTimeMillis() / 1000}")
                        navController.navigate(Screen.Editor.route)
                    },
                    onNewSlideshow = {
                        navController.navigate(Screen.PhotoMaker.route)
                    },
                    onNavigateToMusic = {
                        navController.navigate(Screen.Music.route)
                    },
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route)
                    }
                )
            }

            composable(Screen.Player.route) {
                VideoPlayerScreen(
                    playerManager = playerManager,
                    onBack = { navController.popBackStack() },
                    onEnterPiP = onEnterPiP
                )
            }

            composable(Screen.Music.route) {
                MusicPlayerScreen(
                    playerManager = playerManager,
                    songs = audios,
                    onToggleFavorite = { /* toggle favorite */ }
                )
            }

            composable(Screen.Editor.route) {
                val proj = activeEditingProject ?: ProjectModel()
                EditorScreen(
                    initialProject = proj,
                    onSaveProject = onSaveProject,
                    onExportProject = onExportProject,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PhotoMaker.route) {
                PhotoMakerScreen(
                    availablePhotos = images,
                    onCreateVideoProject = { proj ->
                        activeEditingProject = proj
                        navController.navigate(Screen.Editor.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    videos = videos,
                    audios = audios,
                    onOpenMedia = { media ->
                        if (media.isVideo) {
                            playerManager.playMedia(media)
                            navController.navigate(Screen.Player.route)
                        } else {
                            playerManager.playMedia(media)
                            navController.navigate(Screen.Music.route)
                        }
                    }
                )
            }

            composable(Screen.Online.route) {
                OnlineScreen()
            }

            composable(Screen.Projects.route) {
                ProjectsScreen(
                    projects = projects,
                    onOpenProject = { proj ->
                        activeEditingProject = proj
                        navController.navigate(Screen.Editor.route)
                    },
                    onDeleteProject = onDeleteProject,
                    onDuplicateProject = onDuplicateProject,
                    onNewProject = {
                        activeEditingProject = ProjectModel(name = "Project_${System.currentTimeMillis() / 1000}")
                        navController.navigate(Screen.Editor.route)
                    }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    downloads = downloads,
                    onPauseDownload = {},
                    onResumeDownload = {},
                    onCancelDownload = {},
                    onPlayCompletedMedia = { media ->
                        playerManager.playMedia(media)
                        navController.navigate(Screen.Player.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    preferencesManager = com.thrynx.app.ThrynxApplication.instance.preferencesManager,
                    onLanguageChanged = onLanguageChanged,
                    onClearHistory = onClearHistory
                )
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Column {
            // Linear Progress Indicator
            val progress = if (playbackState.durationMs > 0) {
                playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ThrynxCyan,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ThrynxCardDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.currentMedia?.isVideo == true) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ThrynxCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playbackState.currentMedia?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = ThrynxCyan
                    )
                }
            }
        }
    }
}
