package com.thrynx.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.thrynx.app.core.database.CustomFolderEntity
import com.thrynx.app.core.database.FavoriteMediaEntity
import com.thrynx.app.core.database.PlaybackHistoryEntity
import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.designsystem.ThrynxTheme
import com.thrynx.app.core.localization.LocaleHelper
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.ui.navigation.ThrynxNavigationApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val app by lazy { ThrynxApplication.instance }
    private var currentLanguage = "en"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        lifecycleScope.launch {
            loadVideosFromDevice()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = runBlocking {
            try {
                ThrynxApplication.instance.preferencesManager.languageFlow.first()
            } catch (e: Exception) {
                "en"
            }
        }
        currentLanguage = lang
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()
        handleIncomingIntent(intent)

        setContent {
            val preferencesManager = app.preferencesManager
            val selectedTheme by preferencesManager.themeFlow.collectAsState(initial = "dark")
            val isDark = when (selectedTheme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            ThrynxTheme(darkTheme = isDark) {
                val navController = rememberNavController()

                var rawVideos by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }

                val recentHistory by app.database.historyDao().getRecentHistory(30).collectAsState(initial = emptyList())
                val favorites by app.database.favoriteDao().getAllFavorites().collectAsState(initial = emptyList())
                val privateVideos by app.database.privateVaultDao().getAllPrivateVideosFlow().collectAsState(initial = emptyList())
                val vaultPin by preferencesManager.privateVaultPinFlow.collectAsState(initial = "")

                // Filter out private videos from public list
                val visibleVideos = remember(rawVideos, privateVideos) {
                    val privateUris = privateVideos.map { it.mediaUri }.toSet()
                    rawVideos.filter { !privateUris.contains(it.mediaUriString) }
                }

                val folders = remember(visibleVideos) {
                    app.mediaScanner.groupIntoFolders(visibleVideos)
                }

                fun refreshVideos() {
                    lifecycleScope.launch {
                        loadVideosFromDevice { v ->
                            rawVideos = v
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    refreshVideos()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThrynxNavigationApp(
                        navController = navController,
                        playerManager = app.playerManager,
                        videos = visibleVideos,
                        folders = folders,
                        recentHistory = recentHistory,
                        favorites = favorites,
                        privateVideos = privateVideos,
                        vaultPin = vaultPin,
                        onSetVaultPin = { pin ->
                            lifecycleScope.launch {
                                preferencesManager.setPrivateVaultPin(pin)
                            }
                        },
                        onUnhidePrivateVideo = { pv ->
                            lifecycleScope.launch {
                                app.database.privateVaultDao().removePrivateVideo(pv.mediaUri)
                                Toast.makeText(this@MainActivity, "Video restored to library", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onHideInVault = { media ->
                            lifecycleScope.launch {
                                app.database.privateVaultDao().addPrivateVideo(
                                    PrivateVideoEntity(
                                        mediaUri = media.mediaUriString,
                                        originalPath = media.path,
                                        title = media.title,
                                        durationMs = media.durationMs,
                                        sizeBytes = media.sizeBytes
                                    )
                                )
                                Toast.makeText(this@MainActivity, "Video hidden in vault", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteMedia = { media ->
                            lifecycleScope.launch {
                                val success = app.mediaScanner.deleteVideo(media)
                                if (success) {
                                    Toast.makeText(this@MainActivity, "Video deleted", Toast.LENGTH_SHORT).show()
                                    refreshVideos()
                                } else {
                                    Toast.makeText(this@MainActivity, "Could not delete file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onRenameMedia = { media, newName ->
                            lifecycleScope.launch {
                                val updated = app.mediaScanner.renameVideo(media, newName)
                                if (updated != null) {
                                    Toast.makeText(this@MainActivity, "Video renamed", Toast.LENGTH_SHORT).show()
                                    refreshVideos()
                                } else {
                                    Toast.makeText(this@MainActivity, "Rename failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onAddCustomFolder = { uri ->
                            lifecycleScope.launch {
                                app.database.customFolderDao().insertCustomFolder(
                                    CustomFolderEntity(
                                        folderUri = uri.toString(),
                                        folderName = uri.lastPathSegment ?: "Custom Folder"
                                    )
                                )
                                refreshVideos()
                            }
                        },
                        onToggleFavorite = { media ->
                            lifecycleScope.launch {
                                val uriStr = media.mediaUriString
                                val exists = favorites.any { it.uri == uriStr }
                                if (exists) {
                                    app.database.favoriteDao().removeFavorite(uriStr)
                                } else {
                                    app.database.favoriteDao().addFavorite(
                                        FavoriteMediaEntity(
                                            uri = uriStr,
                                            title = media.title
                                        )
                                    )
                                }
                            }
                        },
                        onRescanLibrary = {
                            refreshVideos()
                        },
                        onEnterPiP = { enterPictureInPicture() },
                        onLanguageChanged = { newLang ->
                            if (newLang != currentLanguage) {
                                recreate()
                            }
                        },
                        onClearHistory = {
                            lifecycleScope.launch {
                                app.database.historyDao().clearAllHistory()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestPermissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    private suspend fun loadVideosFromDevice(onLoaded: (List<MediaItemModel>) -> Unit = {}) {
        val v = app.mediaScanner.loadVideos()
        onLoaded(v)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                val media = MediaItemModel(
                    title = uri.lastPathSegment ?: "External Video",
                    uri = uri,
                    path = uri.toString(),
                    isVideo = true
                )
                app.playerManager.playMedia(media)
            }
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.playerManager.setControlsLocked(isInPictureInPictureMode)
    }

    override fun onStop() {
        saveCurrentPlaybackPosition()
        super.onStop()
    }

    override fun onDestroy() {
        saveCurrentPlaybackPosition()
        super.onDestroy()
    }

    private fun saveCurrentPlaybackPosition() {
        val state = app.playerManager.playbackState.value
        state.currentMedia?.let { media ->
            lifecycleScope.launch {
                app.database.historyDao().upsertHistory(
                    PlaybackHistoryEntity(
                        uri = media.mediaUriString,
                        title = media.title,
                        durationMs = state.durationMs,
                        lastPositionMs = state.currentPositionMs,
                        isVideo = true,
                        lastWatchedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
