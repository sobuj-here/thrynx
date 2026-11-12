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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.thrynx.app.core.database.PlaybackHistoryEntity
import com.thrynx.app.core.database.ProjectEntity
import com.thrynx.app.core.designsystem.ThrynxTheme
import com.thrynx.app.core.editor.export.ExportConfiguration
import com.thrynx.app.core.editor.export.ExportStage
import com.thrynx.app.core.localization.LocaleHelper
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.feature.editor.export.ExportProgressDialog
import com.thrynx.app.ui.navigation.ThrynxNavigationApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class MainActivity : ComponentActivity() {

    private val app by lazy { ThrynxApplication.instance }
    private var currentLanguage = "en"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted, reload media
        lifecycleScope.launch {
            loadMediaLibrary()
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
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ThrynxTheme(darkTheme = isDark) {
                val navController = rememberNavController()

                var videos by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
                var audios by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
                var images by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }

                val projectsEntities by app.database.projectDao().getAllProjectsFlow().collectAsState(initial = emptyList())
                val projects = remember(projectsEntities) { projectsEntities.map { it.toDomain() } }

                val downloads by app.database.downloadDao().getAllDownloads().collectAsState(initial = emptyList())
                val recentHistory by app.database.historyDao().getRecentHistory(1).collectAsState(initial = emptyList())
                val lastWatched = recentHistory.firstOrNull()

                // Export state
                var isExporting by remember { mutableStateOf(false) }
                var exportProgress by remember { mutableIntStateOf(0) }
                var exportStage by remember { mutableStateOf(ExportStage.PREPARING) }
                var exportingProject by remember { mutableStateOf<ProjectModel?>(null) }
                var exportingConfig by remember { mutableStateOf(ExportConfiguration()) }

                LaunchedEffect(Unit) {
                    loadMediaLibrary { v, a, img ->
                        videos = v
                        audios = a
                        images = img
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThrynxNavigationApp(
                        navController = navController,
                        playerManager = app.playerManager,
                        videos = videos,
                        audios = audios,
                        images = images,
                        projects = projects,
                        downloads = downloads,
                        lastWatched = lastWatched,
                        onSaveProject = { proj ->
                            lifecycleScope.launch {
                                app.database.projectDao().insertOrUpdate(ProjectEntity.fromDomain(proj))
                            }
                        },
                        onDeleteProject = { proj ->
                            lifecycleScope.launch {
                                app.database.projectDao().deleteById(proj.id)
                            }
                        },
                        onDuplicateProject = { proj ->
                            lifecycleScope.launch {
                                val dup = proj.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = "${proj.name} (Copy)",
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                app.database.projectDao().insertOrUpdate(ProjectEntity.fromDomain(dup))
                            }
                        },
                        onExportProject = { proj, config ->
                            isExporting = true
                            exportProgress = 0
                            exportStage = ExportStage.PREPARING
                            exportingProject = proj
                            exportingConfig = config

                            val exportDir = File(getExternalFilesDir(null), "Exports").also { it.mkdirs() }
                            val outputFile = File(exportDir, "THRYNX_${proj.name.replace(" ", "_")}_${System.currentTimeMillis()}.mp4")

                            lifecycleScope.launch {
                                val result = app.exportEngine.exportProject(
                                    project = proj,
                                    config = config,
                                    outputFile = outputFile,
                                    onStageChanged = { stage -> exportStage = stage },
                                    onProgress = { p -> exportProgress = p }
                                )
                                isExporting = false
                                result.onSuccess { file ->
                                    Toast.makeText(this@MainActivity, "${getString(R.string.export_success)} (${file.name})", Toast.LENGTH_LONG).show()
                                }.onFailure { err ->
                                    Toast.makeText(this@MainActivity, "${getString(R.string.error_render_failed)}: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
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

                    // Export Progress Dialog (Phase 3 Verified Pipeline)
                    if (isExporting && exportingProject != null) {
                        ExportProgressDialog(
                            project = exportingProject!!,
                            config = exportingConfig,
                            stage = exportStage,
                            progress = exportProgress,
                            onCancelExport = {
                                app.exportEngine.cancelExport()
                                isExporting = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.RECORD_AUDIO)

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestPermissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    private suspend fun loadMediaLibrary(
        onLoaded: (List<MediaItemModel>, List<MediaItemModel>, List<MediaItemModel>) -> Unit = { _, _, _ -> }
    ) {
        val v = app.mediaScanner.loadVideos()
        val a = app.mediaScanner.loadAudios()
        val img = app.mediaScanner.loadImages()
        onLoaded(v, a, img)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
                val isVideo = mimeType.startsWith("video/")
                val media = MediaItemModel(
                    title = uri.lastPathSegment ?: "External Media",
                    uri = uri,
                    path = uri.toString(),
                    isVideo = isVideo
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
        if (isInPictureInPictureMode) {
            app.playerManager.setControlsLocked(true)
        } else {
            app.playerManager.setControlsLocked(false)
        }
    }

    override fun onDestroy() {
        // Save current playback position
        val state = app.playerManager.playbackState.value
        state.currentMedia?.let { media ->
            lifecycleScope.launch {
                app.database.historyDao().upsertHistory(
                    PlaybackHistoryEntity(
                        uri = media.uri.toString(),
                        title = media.title,
                        durationMs = state.durationMs,
                        lastPositionMs = state.currentPositionMs,
                        isVideo = media.isVideo,
                        lastWatchedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        super.onDestroy()
    }
}
