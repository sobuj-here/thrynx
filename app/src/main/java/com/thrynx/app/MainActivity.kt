package com.thrynx.app

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Rational
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.OutputStream
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.thrynx.app.core.database.CustomFolderEntity
import com.thrynx.app.core.database.FavoriteMediaEntity
import com.thrynx.app.core.database.PlaybackHistoryEntity
import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.designsystem.ThrynxTheme
import com.thrynx.app.core.localization.LocaleHelper
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.service.ThrynxMediaPlaybackService
import com.thrynx.app.ui.navigation.ThrynxNavigationApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {

    private val app by lazy { ThrynxApplication.instance }
    private var currentLanguage = "en"
    private var onPermissionGrantedCallback: (() -> Unit)? = null
    private val hasStoragePermissionState = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = MediaStoreScanner.hasStoragePermission(this)
        hasStoragePermissionState.value = granted
        if (granted) {
            onPermissionGrantedCallback?.invoke()
        }
    }

    private var pendingDeleteCallback: (() -> Unit)? = null

    private val deleteResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteCallback?.invoke()
            pendingDeleteCallback = null
            Toast.makeText(this, "Media deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performDeleteMediaItems(items: List<MediaItemModel>, onComplete: () -> Unit) {
        if (items.isEmpty()) return
        lifecycleScope.launch {
            val nonMediaStoreItems = mutableListOf<MediaItemModel>()
            val mediaStoreItems = mutableListOf<MediaItemModel>()

            for (item in items) {
                if (item.path.startsWith(filesDir.absolutePath)) {
                    try {
                        File(item.path).delete()
                        nonMediaStoreItems.add(item)
                    } catch (_: Exception) {}
                } else {
                    mediaStoreItems.add(item)
                }
            }

            if (mediaStoreItems.isEmpty()) {
                Toast.makeText(this@MainActivity, "${nonMediaStoreItems.size} file(s) deleted", Toast.LENGTH_SHORT).show()
                onComplete()
                return@launch
            }

            val uris = mediaStoreItems.mapNotNull {
                it.uri ?: if (it.mediaUriString.isNotBlank()) Uri.parse(it.mediaUriString) else null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                    pendingDeleteCallback = onComplete
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    deleteResultLauncher.launch(request)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Could not delete file", Toast.LENGTH_SHORT).show()
                }
            } else {
                var count = 0
                try {
                    for (u in uris) {
                        val deleted = contentResolver.delete(u, null, null)
                        if (deleted > 0) count++
                    }
                    if (count > 0 || nonMediaStoreItems.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "${count + nonMediaStoreItems.size} file(s) deleted", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        pendingDeleteCallback = onComplete
                        val request = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                        deleteResultLauncher.launch(request)
                    } else {
                        Toast.makeText(this@MainActivity, "Could not delete file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not delete file", Toast.LENGTH_SHORT).show()
                }
            }
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

        hasStoragePermissionState.value = MediaStoreScanner.hasStoragePermission(this)
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
                var rawAudios by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }

                val recentHistory by app.database.historyDao().getRecentHistory(30).collectAsState(initial = emptyList())
                val recentAudioHistory by app.database.historyDao().getRecentAudioHistory(50).collectAsState(initial = emptyList())
                val lastPlayedVideo by app.database.historyDao().getLastPlayedVideoFlow().collectAsState(initial = null)
                val lastPlayedAudio by app.database.historyDao().getLastPlayedAudioFlow().collectAsState(initial = null)

                val favorites by app.database.favoriteDao().getAllFavorites().collectAsState(initial = emptyList())
                val privateVideos by app.database.privateVaultDao().getAllPrivateVideosFlow().collectAsState(initial = emptyList())
                val customFolders by app.database.customFolderDao().getAllCustomFoldersFlow().collectAsState(initial = emptyList())
                val vaultPin by preferencesManager.privateVaultPinFlow.collectAsState(initial = "")
                val hiddenFolders by preferencesManager.hiddenFoldersFlow.collectAsState(initial = emptySet())

                // Attach persistent playback positions from Room DB to videos
                val videoHistoryMap = remember(recentHistory) { recentHistory.associateBy({ it.uri }, { it.lastPositionMs }) }
                val visibleVideos = remember(rawVideos, privateVideos, hiddenFolders, videoHistoryMap) {
                    val privateUris = privateVideos.map { it.mediaUri }.toSet()
                    rawVideos.filter {
                        !privateUris.contains(it.mediaUriString) && !hiddenFolders.contains(it.folderName)
                    }.map { video ->
                        val savedPos = videoHistoryMap[video.mediaUriString]
                        if (savedPos != null && savedPos > 0L) video.copy(lastPositionMs = savedPos) else video
                    }
                }

                // Attach persistent playback positions from Room DB to audios
                val audioHistoryMap = remember(recentAudioHistory) { recentAudioHistory.associateBy({ it.uri }, { it.lastPositionMs }) }
                val mappedAudios = remember(rawAudios, audioHistoryMap) {
                    rawAudios.map { audio ->
                        val savedPos = audioHistoryMap[audio.mediaUriString]
                        if (savedPos != null && savedPos > 0L) audio.copy(lastPositionMs = savedPos) else audio
                    }
                }

                val folders = remember(visibleVideos) {
                    app.mediaScanner.groupIntoFolders(visibleVideos)
                }

                val audioFolders = remember(mappedAudios) {
                    app.mediaScanner.loadAudioFolders(mappedAudios)
                }

                fun refreshVideos() {
                    lifecycleScope.launch {
                        val foldersToScan = try {
                            app.database.customFolderDao().getAllCustomFolders()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val v = app.mediaScanner.loadVideos(customFolders = foldersToScan)
                        rawVideos = v
                        val a = app.mediaScanner.loadAudios()
                        rawAudios = a
                    }
                }

                onPermissionGrantedCallback = {
                    hasStoragePermissionState.value = true
                    refreshVideos()
                }

                LaunchedEffect(Unit) {
                    hasStoragePermissionState.value = MediaStoreScanner.hasStoragePermission(this@MainActivity)
                    refreshVideos()
                }

                LaunchedEffect(customFolders) {
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
                        audios = mappedAudios,
                        folders = folders,
                        audioFolders = audioFolders,
                        recentHistory = recentHistory,
                        favorites = favorites,
                        privateVideos = privateVideos,
                        customFolders = customFolders,
                        vaultPin = vaultPin,
                        lastPlayedVideoUri = lastPlayedVideo?.uri,
                        lastPlayedAudioUri = lastPlayedAudio?.uri,
                        hasStoragePermission = hasStoragePermissionState.value,
                        canUseBiometric = canUseBiometric(),
                        onRequestPermission = { checkAndRequestPermissions() },
                        onSetVaultPin = { pin ->
                            lifecycleScope.launch {
                                preferencesManager.setPrivateVaultPin(pin)
                            }
                        },
                        onUnhidePrivateVideo = { pv ->
                            lifecycleScope.launch {
                                app.database.privateVaultDao().removePrivateVideo(pv.mediaUri)
                                Toast.makeText(this@MainActivity, "Video restored to library", Toast.LENGTH_SHORT).show()
                                refreshVideos()
                            }
                        },
                        onHideInVault = { media ->
                            lifecycleScope.launch {
                                val vaultDir = File(filesDir, "vault").also { it.mkdirs() }
                                File(vaultDir, ".nomedia").createNewFile()
                                val destFile = File(vaultDir, "vault_${System.currentTimeMillis()}_${File(media.path).name}")
                                try {
                                    val input = if (media.uri != null) contentResolver.openInputStream(media.uri) else if (media.path.isNotBlank()) File(media.path).inputStream() else null
                                    input?.use { inStream ->
                                        destFile.outputStream().use { outStream: OutputStream ->
                                            inStream.copyTo(outStream)
                                        }
                                    }
                                    app.mediaScanner.deleteVideo(media)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                app.database.privateVaultDao().addPrivateVideo(
                                    PrivateVideoEntity(
                                        mediaUri = Uri.fromFile(destFile).toString(),
                                        originalPath = destFile.absolutePath,
                                        title = media.title,
                                        durationMs = media.durationMs,
                                        sizeBytes = if (destFile.exists()) destFile.length() else media.sizeBytes
                                    )
                                )
                                Toast.makeText(this@MainActivity, "Video hidden in vault", Toast.LENGTH_SHORT).show()
                                refreshVideos()
                            }
                        },
                        onDeleteMedia = { media ->
                            performDeleteMediaItems(listOf(media)) { refreshVideos() }
                        },
                        onDeleteMultipleVideos = { list ->
                            performDeleteMediaItems(list) { refreshVideos() }
                        },
                        onHideMultipleVideosInVault = { list ->
                            lifecycleScope.launch {
                                val vaultDir = File(filesDir, "vault").also { it.mkdirs() }
                                File(vaultDir, ".nomedia").createNewFile()
                                for (media in list) {
                                    val destFile = File(vaultDir, "vault_${System.currentTimeMillis()}_${File(media.path).name}")
                                    try {
                                        val input = if (media.uri != null) contentResolver.openInputStream(media.uri) else if (media.path.isNotBlank()) File(media.path).inputStream() else null
                                        input?.use { inStream ->
                                            destFile.outputStream().use { outStream: OutputStream ->
                                                inStream.copyTo(outStream)
                                            }
                                        }
                                        app.mediaScanner.deleteVideo(media)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    app.database.privateVaultDao().addPrivateVideo(
                                        PrivateVideoEntity(
                                            mediaUri = Uri.fromFile(destFile).toString(),
                                            originalPath = destFile.absolutePath,
                                            title = media.title,
                                            durationMs = media.durationMs,
                                            sizeBytes = if (destFile.exists()) destFile.length() else media.sizeBytes
                                        )
                                    )
                                }
                                Toast.makeText(this@MainActivity, "${list.size} videos hidden in vault", Toast.LENGTH_SHORT).show()
                                refreshVideos()
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
                            try {
                                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                contentResolver.takePersistableUriPermission(uri, flags)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            lifecycleScope.launch {
                                val folderName = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null }
                                    ?: uri.lastPathSegment ?: "Custom Folder"
                                app.database.customFolderDao().insertCustomFolder(
                                    CustomFolderEntity(
                                        folderUri = uri.toString(),
                                        folderName = folderName
                                    )
                                )
                                refreshVideos()
                            }
                        },
                        onRemoveCustomFolder = { folderUri ->
                            lifecycleScope.launch {
                                app.database.customFolderDao().deleteCustomFolder(folderUri)
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
                        },
                        onLaunchBiometric = { onSuccess, onError ->
                            launchBiometricPrompt(onSuccess, onError)
                        },
                        onHideFolder = { folder ->
                            lifecycleScope.launch {
                                preferencesManager.hideFolder(folder.name)
                                Toast.makeText(this@MainActivity, "Folder hidden", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteFolder = { folder ->
                            val folderVideos = rawVideos.filter { it.folderName.equals(folder.name, ignoreCase = true) }
                            performDeleteMediaItems(folderVideos) { refreshVideos() }
                        },
                        onHideMultipleFolders = { list ->
                            lifecycleScope.launch {
                                for (f in list) {
                                    preferencesManager.hideFolder(f.name)
                                }
                                Toast.makeText(this@MainActivity, "${list.size} folders hidden", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteMultipleFolders = { list ->
                            val names = list.map { it.name.lowercase() }.toSet()
                            val targets = rawVideos.filter { names.contains(it.folderName.lowercase()) }
                            performDeleteMediaItems(targets) { refreshVideos() }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val perm = MediaStoreScanner.hasStoragePermission(this)
        if (perm != hasStoragePermissionState.value) {
            hasStoragePermissionState.value = perm
            if (perm) {
                onPermissionGrantedCallback?.invoke()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestPermissionLauncher.launch(ungranted.toTypedArray())
        } else {
            hasStoragePermissionState.value = true
        }
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
            val currentMedia = app.playerManager.playbackState.value.currentMedia
            val rational = if (currentMedia != null && currentMedia.width > 0 && currentMedia.height > 0) {
                val aspect = currentMedia.width.toFloat() / currentMedia.height.toFloat()
                // Coerce aspect ratio within Android limits (1/2.39 to 2.39/1)
                val clampedAspect = aspect.coerceIn(0.4184f, 2.39f)
                Rational((clampedAspect * 1000).toInt(), 1000)
            } else {
                Rational(16, 9)
            }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.playerManager.setControlsLocked(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (app.playerManager.playbackState.value.isPlaying && app.playerManager.playbackState.value.currentMedia?.isVideo == true) {
            enterPictureInPicture()
        }
    }

    override fun onStop() {
        saveCurrentPlaybackPosition()
        val currentMedia = app.playerManager.playbackState.value.currentMedia
        val isPlaying = app.playerManager.playbackState.value.isPlaying
        val isVideo = currentMedia?.isVideo == true

        if (!isVideo && isPlaying) {
            // Audio background playback ALWAYS continues seamlessly via foreground service
            val serviceIntent = Intent(this, ThrynxMediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else if (isVideo) {
            val backgroundVideoEnabled = runBlocking {
                try {
                    app.preferencesManager.backgroundPlaybackFlow.first()
                } catch (e: Exception) {
                    false
                }
            }
            if (!backgroundVideoEnabled && !isInPictureInPictureMode) {
                app.playerManager.pause()
            } else if (backgroundVideoEnabled && isPlaying) {
                val serviceIntent = Intent(this, ThrynxMediaPlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        saveCurrentPlaybackPosition()
        super.onDestroy()
    }

    private fun saveCurrentPlaybackPosition() {
        val state = app.playerManager.playbackState.value
        state.currentMedia?.let { media ->
            val pos = state.currentPositionMs
            val duration = state.durationMs
            if (pos > 1000L || duration > 0L) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        app.database.historyDao().upsertHistory(
                            PlaybackHistoryEntity(
                                uri = media.mediaUriString,
                                title = media.title,
                                durationMs = if (duration > 0L) duration else media.durationMs,
                                lastPositionMs = pos,
                                isVideo = media.isVideo,
                                lastWatchedTimestamp = System.currentTimeMillis()
                            )
                        )
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    private fun canUseBiometric(): Boolean {
        return try {
            val biometricManager = BiometricManager.from(this)
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun launchBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString.toString())
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Private Vault")
                .setSubtitle("Confirm your biometric credentials to access hidden videos")
                .setNegativeButtonText("Use PIN")
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "Biometric error")
        }
    }
}
