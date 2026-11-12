package com.thrynx.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thrynx.app.R
import com.thrynx.app.core.datastore.PreferencesManager
import com.thrynx.app.core.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // General Preferences
    val currentLang by preferencesManager.languageFlow.collectAsState(initial = "en")
    val currentTheme by preferencesManager.themeFlow.collectAsState(initial = "dark")
    val startupScreen by preferencesManager.startupScreenFlow.collectAsState(initial = "home")

    // Player Preferences
    val resumePlayback by preferencesManager.resumePlaybackFlow.collectAsState(initial = true)
    val defaultSpeed by preferencesManager.defaultSpeedFlow.collectAsState(initial = 1.0f)
    val defaultOrientation by preferencesManager.defaultOrientationFlow.collectAsState(initial = "auto")
    val defaultResizeMode by preferencesManager.defaultResizeModeFlow.collectAsState(initial = "fit")
    val gesturesEnabled by preferencesManager.gesturesEnabledFlow.collectAsState(initial = true)
    val backgroundAudio by preferencesManager.backgroundAudioFlow.collectAsState(initial = true)
    val subtitleStyle by preferencesManager.subtitleStyleFlow.collectAsState(initial = com.thrynx.app.core.model.SubtitleStyle())

    // Editor Preferences
    val editorRes by preferencesManager.editorResolutionFlow.collectAsState(initial = "1080p")
    val editorFps by preferencesManager.editorFpsFlow.collectAsState(initial = 30)
    val autosaveInterval by preferencesManager.autosaveIntervalFlow.collectAsState(initial = 30)
    val exportLocation by preferencesManager.exportLocationFlow.collectAsState(initial = "Movies/THRYNX")

    // Dynamic Cache Size calculation
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    fun refreshCacheSize() {
        coroutineScope.launch(Dispatchers.IO) {
            val total = getFolderSize(context.cacheDir) + (context.externalCacheDir?.let { getFolderSize(it) } ?: 0L)
            withContext(Dispatchers.Main) { cacheSizeBytes = total }
        }
    }
    LaunchedEffect(Unit) { refreshCacheSize() }

    // Dynamic Permissions
    var hasStoragePerm by remember { mutableStateOf(checkStoragePermission(context)) }
    var hasMicPerm by remember { mutableStateOf(checkMicPermission(context)) }

    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasStoragePerm = checkStoragePermission(context)
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasMicPerm = checkMicPermission(context)
    }

    // Dialog Dialog States
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showStartupDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showAutosaveDialog by remember { mutableStateOf(false) }
    var showConfirmClearHistoryDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ThrynxHeader(title = stringResource(id = R.string.settings_title))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==========================================
                // SECTION 1: GENERAL
                // ==========================================
                item {
                    SettingsSectionHeader(stringResource(id = R.string.settings_section_general))
                    ThrynxCard {
                        Column {
                            // Language
                            SettingsRow(
                                icon = Icons.Default.Translate,
                                title = stringResource(id = R.string.settings_language),
                                subtitle = if (currentLang == "bn") stringResource(id = R.string.settings_lang_bn) else stringResource(id = R.string.settings_lang_en),
                                onClick = { showLanguageDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Theme
                            SettingsRow(
                                icon = Icons.Default.Palette,
                                title = stringResource(id = R.string.settings_theme),
                                subtitle = when (currentTheme) {
                                    "light" -> stringResource(id = R.string.settings_theme_light)
                                    "system" -> stringResource(id = R.string.settings_theme_system)
                                    else -> stringResource(id = R.string.settings_theme_dark)
                                },
                                onClick = { showThemeDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Startup Screen
                            SettingsRow(
                                icon = Icons.Default.Home,
                                title = stringResource(id = R.string.settings_startup_screen),
                                subtitle = when (startupScreen) {
                                    "library" -> stringResource(id = R.string.settings_startup_library)
                                    "editor" -> stringResource(id = R.string.settings_startup_editor)
                                    else -> stringResource(id = R.string.settings_startup_home)
                                },
                                onClick = { showStartupDialog = true }
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 2: PLAYER PREFERENCES
                // ==========================================
                item {
                    SettingsSectionHeader(stringResource(id = R.string.settings_section_player))
                    ThrynxCard {
                        Column {
                            // Remember Last Position
                            SettingsToggleRow(
                                icon = Icons.Default.History,
                                title = stringResource(id = R.string.settings_remember_position),
                                subtitle = stringResource(id = R.string.settings_remember_position_desc),
                                checked = resumePlayback,
                                onCheckedChange = {
                                    coroutineScope.launch { preferencesManager.setResumePlayback(it) }
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Default Playback Speed
                            SettingsRow(
                                icon = Icons.Default.Speed,
                                title = stringResource(id = R.string.settings_default_speed),
                                subtitle = "${defaultSpeed}x",
                                onClick = { showSpeedDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Default Orientation
                            SettingsRow(
                                icon = Icons.Default.ScreenRotation,
                                title = stringResource(id = R.string.settings_default_orientation),
                                subtitle = when (defaultOrientation) {
                                    "landscape" -> stringResource(id = R.string.settings_orientation_landscape)
                                    "portrait" -> stringResource(id = R.string.settings_orientation_portrait)
                                    else -> stringResource(id = R.string.settings_orientation_auto)
                                },
                                onClick = { showOrientationDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Default Video Fit Mode
                            SettingsRow(
                                icon = Icons.Default.AspectRatio,
                                title = stringResource(id = R.string.settings_default_resize_mode),
                                subtitle = if (defaultResizeMode == "fill") stringResource(id = R.string.settings_resize_fill) else stringResource(id = R.string.settings_resize_fit),
                                onClick = { showResizeDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Gestures Enabled
                            SettingsToggleRow(
                                icon = Icons.Default.TouchApp,
                                title = stringResource(id = R.string.settings_gestures_enabled),
                                subtitle = stringResource(id = R.string.settings_gestures_desc),
                                checked = gesturesEnabled,
                                onCheckedChange = {
                                    coroutineScope.launch { preferencesManager.setGesturesEnabled(it) }
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Background Audio
                            SettingsToggleRow(
                                icon = Icons.Default.Headphones,
                                title = stringResource(id = R.string.settings_auto_background_audio),
                                subtitle = stringResource(id = R.string.settings_background_audio_desc),
                                checked = backgroundAudio,
                                onCheckedChange = {
                                    coroutineScope.launch { preferencesManager.setBackgroundAudio(it) }
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Subtitle Appearance
                            SettingsRow(
                                icon = Icons.Default.Subtitles,
                                title = stringResource(id = R.string.settings_subtitle_styling),
                                subtitle = "${subtitleStyle.fontSizeSp.toInt()} sp • ${subtitleStyle.textColorHex}",
                                onClick = { showSubtitleDialog = true }
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 3: EDITOR PREFERENCES
                // ==========================================
                item {
                    SettingsSectionHeader(stringResource(id = R.string.settings_section_editor))
                    ThrynxCard {
                        Column {
                            // Resolution
                            SettingsRow(
                                icon = Icons.Default.Hd,
                                title = stringResource(id = R.string.settings_editor_resolution),
                                subtitle = editorRes,
                                onClick = { showResolutionDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Frame Rate
                            SettingsRow(
                                icon = Icons.Default.VideoSettings,
                                title = stringResource(id = R.string.settings_editor_fps),
                                subtitle = "$editorFps FPS",
                                onClick = { showFpsDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Autosave
                            SettingsRow(
                                icon = Icons.Default.Save,
                                title = stringResource(id = R.string.settings_editor_autosave),
                                subtitle = "$autosaveInterval seconds",
                                onClick = { showAutosaveDialog = true }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Export Directory
                            SettingsRow(
                                icon = Icons.Default.FolderSpecial,
                                title = stringResource(id = R.string.settings_export_location),
                                subtitle = exportLocation,
                                onClick = {}
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 4: STORAGE & CACHE
                // ==========================================
                item {
                    val cacheClearedMsg = stringResource(id = R.string.settings_cache_cleared)
                    val historyClearedMsg = stringResource(id = R.string.settings_history_cleared)
                    SettingsSectionHeader(stringResource(id = R.string.settings_section_storage))
                    ThrynxCard {
                        Column {
                            // Cache Size & Clear Cache
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(ThrynxCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Cached, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(id = R.string.settings_cache_size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = formatFileSize(cacheSizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ThrynxCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            clearAppCache(context)
                                            withContext(Dispatchers.Main) {
                                                refreshCacheSize()
                                                snackbarHostState.showSnackbar(cacheClearedMsg)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThrynxCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(id = R.string.settings_clear_cache), fontSize = 12.sp)
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Clear Playback History
                            SettingsRow(
                                icon = Icons.Default.DeleteSweep,
                                title = stringResource(id = R.string.settings_clear_history),
                                subtitle = stringResource(id = R.string.settings_clear_history_desc),
                                onClick = { showConfirmClearHistoryDialog = true }
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 5: PRIVACY & PERMISSIONS
                // ==========================================
                item {
                    SettingsSectionHeader(stringResource(id = R.string.settings_section_privacy))
                    ThrynxCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // 100% Offline-First Architecture Banner
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(ThrynxEmerald.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = ThrynxEmerald, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.settings_offline_first_badge),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ThrynxEmerald
                                    )
                                    Text(
                                        text = stringResource(id = R.string.settings_offline_first_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Storage Permission Status
                            PermissionStatusRow(
                                title = stringResource(id = R.string.settings_perm_storage),
                                isGranted = hasStoragePerm,
                                onRequest = {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
                                    } else {
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    storagePermLauncher.launch(perms)
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Microphone Permission Status
                            PermissionStatusRow(
                                title = stringResource(id = R.string.settings_perm_mic),
                                isGranted = hasMicPerm,
                                onRequest = {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // SECTION 6: ABOUT THRYNX
                // ==========================================
                item {
                    SettingsSectionHeader(stringResource(id = R.string.settings_section_about))
                    ThrynxCard {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ThrynxCobalt.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_thrynx_brand_mark),
                                        contentDescription = null,
                                        tint = ThrynxCyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "${stringResource(id = R.string.app_name)} 1.0.0",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ThrynxCyan
                                    )
                                    Text(
                                        text = stringResource(id = R.string.app_tagline),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = stringResource(id = R.string.settings_copyright),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // Licenses
                            SettingsRow(
                                icon = Icons.Default.Description,
                                title = stringResource(id = R.string.settings_licenses),
                                subtitle = "ExoPlayer, Jetpack Compose, Room, Kotlin Coroutines",
                                onClick = { showLicensesDialog = true }
                            )

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // GitHub Repo
                            SettingsRow(
                                icon = Icons.Default.Code,
                                title = stringResource(id = R.string.settings_github),
                                subtitle = "https://github.com/sobuj-here/thrynx",
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sobuj-here/thrynx"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // DIALOGS
        // ==========================================

        // 1. Language Dialog
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(id = R.string.settings_section_language)) },
                text = {
                    Column {
                        listOf("en" to stringResource(id = R.string.settings_lang_en), "bn" to stringResource(id = R.string.settings_lang_bn)).forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setLanguage(code) }
                                        onLanguageChanged(code)
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentLang == code,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setLanguage(code) }
                                        onLanguageChanged(code)
                                        showLanguageDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 2. Theme Dialog
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text(stringResource(id = R.string.settings_theme)) },
                text = {
                    Column {
                        listOf(
                            "dark" to stringResource(id = R.string.settings_theme_dark),
                            "light" to stringResource(id = R.string.settings_theme_light),
                            "system" to stringResource(id = R.string.settings_theme_system)
                        ).forEach { (themeCode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setTheme(themeCode) }
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentTheme == themeCode,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setTheme(themeCode) }
                                        showThemeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 3. Startup Screen Dialog
        if (showStartupDialog) {
            AlertDialog(
                onDismissRequest = { showStartupDialog = false },
                title = { Text(stringResource(id = R.string.settings_startup_screen)) },
                text = {
                    Column {
                        listOf(
                            "home" to stringResource(id = R.string.settings_startup_home),
                            "library" to stringResource(id = R.string.settings_startup_library),
                            "editor" to stringResource(id = R.string.settings_startup_editor)
                        ).forEach { (screenKey, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setStartupScreen(screenKey) }
                                        showStartupDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = startupScreen == screenKey,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setStartupScreen(screenKey) }
                                        showStartupDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStartupDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 4. Speed Dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text(stringResource(id = R.string.settings_default_speed)) },
                text = {
                    Column {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setDefaultSpeed(spd) }
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultSpeed == spd,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setDefaultSpeed(spd) }
                                        showSpeedDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("${spd}x")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 5. Orientation Dialog
        if (showOrientationDialog) {
            AlertDialog(
                onDismissRequest = { showOrientationDialog = false },
                title = { Text(stringResource(id = R.string.settings_default_orientation)) },
                text = {
                    Column {
                        listOf(
                            "auto" to stringResource(id = R.string.settings_orientation_auto),
                            "landscape" to stringResource(id = R.string.settings_orientation_landscape),
                            "portrait" to stringResource(id = R.string.settings_orientation_portrait)
                        ).forEach { (ori, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setDefaultOrientation(ori) }
                                        showOrientationDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultOrientation == ori,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setDefaultOrientation(ori) }
                                        showOrientationDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOrientationDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 6. Resize Mode Dialog
        if (showResizeDialog) {
            AlertDialog(
                onDismissRequest = { showResizeDialog = false },
                title = { Text(stringResource(id = R.string.settings_default_resize_mode)) },
                text = {
                    Column {
                        listOf(
                            "fit" to stringResource(id = R.string.settings_resize_fit),
                            "fill" to stringResource(id = R.string.settings_resize_fill)
                        ).forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setDefaultResizeMode(mode) }
                                        showResizeDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultResizeMode == mode,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setDefaultResizeMode(mode) }
                                        showResizeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showResizeDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 7. Subtitle Styling Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text(stringResource(id = R.string.settings_subtitle_styling)) },
                text = {
                    Column {
                        Text("Font Size", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(14f, 18f, 22f, 26f).forEach { size ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            preferencesManager.updateSubtitleStyle(subtitleStyle.copy(fontSizeSp = size))
                                        }
                                        showSubtitleDialog = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = subtitleStyle.fontSizeSp == size,
                                    onClick = {
                                        coroutineScope.launch {
                                            preferencesManager.updateSubtitleStyle(subtitleStyle.copy(fontSizeSp = size))
                                        }
                                        showSubtitleDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("${size.toInt()} sp")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 8. Editor Resolution Dialog
        if (showResolutionDialog) {
            AlertDialog(
                onDismissRequest = { showResolutionDialog = false },
                title = { Text(stringResource(id = R.string.settings_editor_resolution)) },
                text = {
                    Column {
                        listOf("720p", "1080p", "4K").forEach { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setEditorResolution(res) }
                                        showResolutionDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = editorRes == res,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setEditorResolution(res) }
                                        showResolutionDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(res)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showResolutionDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 9. Editor FPS Dialog
        if (showFpsDialog) {
            AlertDialog(
                onDismissRequest = { showFpsDialog = false },
                title = { Text(stringResource(id = R.string.settings_editor_fps)) },
                text = {
                    Column {
                        listOf(24, 30, 60).forEach { fps ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setEditorFps(fps) }
                                        showFpsDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = editorFps == fps,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setEditorFps(fps) }
                                        showFpsDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("$fps FPS")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFpsDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 10. Autosave Interval Dialog
        if (showAutosaveDialog) {
            AlertDialog(
                onDismissRequest = { showAutosaveDialog = false },
                title = { Text(stringResource(id = R.string.settings_editor_autosave)) },
                text = {
                    Column {
                        listOf(15, 30, 60).forEach { sec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setAutosaveInterval(sec) }
                                        showAutosaveDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = autosaveInterval == sec,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setAutosaveInterval(sec) }
                                        showAutosaveDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("$sec seconds")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAutosaveDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }

        // 11. Confirm Clear History Dialog
        if (showConfirmClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmClearHistoryDialog = false },
                title = { Text(stringResource(id = R.string.settings_clear_history)) },
                text = { Text(stringResource(id = R.string.settings_clear_history_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearHistory()
                            showConfirmClearHistoryDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_history_cleared))
                            }
                        }
                    ) {
                        Text(stringResource(id = R.string.action_confirm), color = ThrynxCoral)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClearHistoryDialog = false }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        // 12. Open Source Licenses Dialog
        if (showLicensesDialog) {
            AlertDialog(
                onDismissRequest = { showLicensesDialog = false },
                title = { Text(stringResource(id = R.string.settings_licenses)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "THRYNX is built using modern, reliable open-source Android libraries:\n\n" +
                                    "• AndroidX Media3 (ExoPlayer & Transformer) — Apache 2.0\n" +
                                    "• Jetpack Compose & Material 3 — Apache 2.0\n" +
                                    "• AndroidX Room & DataStore — Apache 2.0\n" +
                                    "• Kotlin Coroutines & Flow — Apache 2.0\n\n" +
                                    "All media playback, editing, and rendering run locally on-device.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLicensesDialog = false }) {
                        Text(stringResource(id = R.string.action_close))
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = ThrynxCyan,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ThrynxCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ThrynxCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ThrynxCyan)
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        if (isGranted) {
            Surface(
                color = ThrynxEmerald.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_perm_granted),
                    color = ThrynxEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            OutlinedButton(
                onClick = onRequest,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ThrynxCoral),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(stringResource(id = R.string.settings_perm_denied), fontSize = 12.sp)
            }
        }
    }
}

fun getFolderSize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (!file.isDirectory) return file.length()
    var size = 0L
    file.listFiles()?.forEach { child ->
        size += if (child.isDirectory) getFolderSize(child) else child.length()
    }
    return size
}

fun clearAppCache(context: Context) {
    context.cacheDir.deleteRecursively()
    context.externalCacheDir?.deleteRecursively()
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

fun checkMicPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

