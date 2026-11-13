package com.thrynx.app.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.datastore.PreferencesManager
import com.thrynx.app.core.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State flows
    val currentLang by preferencesManager.languageFlow.collectAsState(initial = "en")
    val currentTheme by preferencesManager.themeFlow.collectAsState(initial = "dark")
    val defaultSpeed by preferencesManager.defaultSpeedFlow.collectAsState(initial = 1.0f)
    val doubleTapSeekSec by preferencesManager.doubleTapSeekSecFlow.collectAsState(initial = 10)
    val resumeMode by preferencesManager.resumeModeFlow.collectAsState(initial = "ask")
    val showPlayerInfoHud by preferencesManager.showPlayerInfoHudFlow.collectAsState(initial = true)
    val hiddenFolders by preferencesManager.hiddenFoldersFlow.collectAsState(initial = emptySet())
    val backgroundAudio by preferencesManager.backgroundAudioFlow.collectAsState(initial = false)
    val gesturesEnabled by preferencesManager.gesturesEnabledFlow.collectAsState(initial = true)
    val defaultOrientation by preferencesManager.defaultOrientationFlow.collectAsState(initial = "auto")
    val defaultResizeMode by preferencesManager.defaultResizeModeFlow.collectAsState(initial = "fit")
    val autoPlayNext by preferencesManager.autoPlayNextFlow.collectAsState(initial = true)
    val hwDecoderMode by preferencesManager.hwDecoderModeFlow.collectAsState(initial = "auto")
    val vaultPin by preferencesManager.privateVaultPinFlow.collectAsState(initial = "")
    val subtitleStyle by preferencesManager.subtitleStyleFlow.collectAsState(initial = com.thrynx.app.core.model.SubtitleStyle())

    val showPlayedTime by preferencesManager.showPlayedTimeFlow.collectAsState(initial = true)
    val videoViewMode by preferencesManager.videoViewModeFlow.collectAsState(initial = "files")
    val audioViewMode by preferencesManager.audioViewModeFlow.collectAsState(initial = "files")
    val pauseOnHeadsetDisconnect by preferencesManager.pauseOnHeadsetDisconnectFlow.collectAsState(initial = true)

    // Dialog states
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showResumeModeDialog by remember { mutableStateOf(false) }
    var showHiddenFoldersDialog by remember { mutableStateOf(false) }
    var showVideoViewModeDialog by remember { mutableStateOf(false) }
    var showAudioViewModeDialog by remember { mutableStateOf(false) }
    var showDevInfoDialog by remember { mutableStateOf(false) }

    // Dynamic Cache Size calculation
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    fun refreshCacheSize() {
        coroutineScope.launch(Dispatchers.IO) {
            val total = getFolderSize(context.cacheDir) + (context.externalCacheDir?.let { getFolderSize(it) } ?: 0L)
            withContext(Dispatchers.Main) { cacheSizeBytes = total }
        }
    }
    LaunchedEffect(Unit) { refreshCacheSize() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Text(
                text = stringResource(id = R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ThrynxCyan
            )
        }

        // 1. SECTION: LIST
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_list)) {
                SettingsClickableRow(
                    icon = Icons.Default.VideoLibrary,
                    title = stringResource(id = R.string.settings_video_view_mode),
                    value = if (videoViewMode == "folders") "By Folders" else "All Videos",
                    onClick = { showVideoViewModeDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.Audiotrack,
                    title = stringResource(id = R.string.settings_audio_view_mode),
                    value = if (audioViewMode == "folders") "By Folders" else "All Songs",
                    onClick = { showAudioViewModeDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    icon = Icons.Default.AvTimer,
                    title = stringResource(id = R.string.settings_show_played_time),
                    description = stringResource(id = R.string.settings_show_played_time_desc),
                    checked = showPlayedTime,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { preferencesManager.setShowPlayedTime(checked) }
                    }
                )
            }
        }

        // 2. SECTION: PLAYER
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_player)) {
                SettingsClickableRow(
                    icon = Icons.Default.Restore,
                    title = stringResource(id = R.string.dialog_resume_title),
                    value = when (resumeMode) {
                        "resume" -> stringResource(id = R.string.settings_resume_mode_always)
                        "beginning" -> stringResource(id = R.string.settings_resume_mode_never)
                        else -> stringResource(id = R.string.settings_resume_mode_ask)
                    },
                    onClick = { showResumeModeDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.ScreenRotation,
                    title = stringResource(id = R.string.settings_default_orientation),
                    value = when (defaultOrientation) {
                        "landscape" -> stringResource(id = R.string.settings_orientation_landscape)
                        "portrait" -> stringResource(id = R.string.settings_orientation_portrait)
                        else -> stringResource(id = R.string.settings_orientation_auto)
                    },
                    onClick = { showOrientationDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.Speed,
                    title = stringResource(id = R.string.settings_default_speed),
                    value = "${defaultSpeed}x",
                    onClick = { showSpeedDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.AvTimer,
                    title = stringResource(id = R.string.settings_double_tap_seek),
                    value = "$doubleTapSeekSec s",
                    onClick = { showSeekDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    icon = Icons.Default.Schedule,
                    title = stringResource(id = R.string.settings_player_hud_info),
                    description = stringResource(id = R.string.settings_player_hud_info_desc),
                    checked = showPlayerInfoHud,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { preferencesManager.setShowPlayerInfoHud(checked) }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    icon = Icons.Default.SkipNext,
                    title = stringResource(id = R.string.settings_auto_play_next),
                    checked = autoPlayNext,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { preferencesManager.setAutoPlayNext(checked) }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(id = R.string.settings_gestures_enabled),
                    checked = gesturesEnabled,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { preferencesManager.setGesturesEnabled(checked) }
                    }
                )
            }
        }

        // 3. SECTION: DECODER
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_decoder)) {
                SettingsClickableRow(
                    icon = Icons.Default.Memory,
                    title = stringResource(id = R.string.settings_hw_decoder),
                    value = when (hwDecoderMode) {
                        "hardware" -> stringResource(id = R.string.settings_decoder_hw)
                        "software" -> stringResource(id = R.string.settings_decoder_sw)
                        else -> stringResource(id = R.string.settings_decoder_auto)
                    },
                    onClick = { showDecoderDialog = true }
                )
            }
        }

        // 4. SECTION: AUDIO
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_audio)) {
                SettingsSwitchRow(
                    icon = Icons.Default.Headphones,
                    title = stringResource(id = R.string.settings_pause_on_headset),
                    description = stringResource(id = R.string.settings_pause_on_headset_desc),
                    checked = pauseOnHeadsetDisconnect,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { preferencesManager.setPauseOnHeadsetDisconnect(checked) }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow(
                    icon = Icons.Default.Audiotrack,
                    title = stringResource(id = R.string.settings_background_audio),
                    description = stringResource(id = R.string.settings_background_audio_desc),
                    checked = backgroundAudio,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { preferencesManager.setBackgroundAudio(checked) }
                    }
                )
            }
        }

        // 5. SECTION: SUBTITLES
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_subtitles)) {
                SettingsClickableRow(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(id = R.string.settings_subtitle_style),
                    value = "${subtitleStyle.fontSizeSp.toInt()}sp, ${subtitleStyle.textColorHex}",
                    onClick = { showSubtitleDialog = true }
                )
            }
        }

        // 6. SECTION: GENERAL
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_general)) {
                SettingsClickableRow(
                    icon = Icons.Default.FolderOff,
                    title = stringResource(id = R.string.settings_hidden_folders_title),
                    value = "${hiddenFolders.size} hidden",
                    onClick = { showHiddenFoldersDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = stringResource(id = R.string.settings_rescan_media),
                    onClick = {
                        onRescanLibrary()
                        Toast.makeText(context, context.getString(R.string.settings_rescan_media), Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.History,
                    title = stringResource(id = R.string.settings_clear_history),
                    onClick = {
                        onClearHistory()
                        Toast.makeText(context, context.getString(R.string.settings_clear_history_confirm), Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Default.CleaningServices,
                    title = stringResource(id = R.string.settings_clear_cache),
                    value = formatFileSize(cacheSizeBytes),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            context.cacheDir.deleteRecursively()
                            context.externalCacheDir?.deleteRecursively()
                            refreshCacheSize()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.settings_clear_cache_confirm), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        // 7. SECTION: DEVELOPMENT & SECURITY
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_dev)) {
                SettingsClickableRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(id = R.string.settings_private_pin),
                    value = if (vaultPin.isBlank()) "Not Set" else "Active (••••)",
                    onClick = { showPinDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.Info,
                    title = stringResource(id = R.string.settings_app_info),
                    value = "Architecture & Licenses",
                    onClick = { showDevInfoDialog = true }
                )
            }
        }

        // 8. SECTION: APP LANGUAGE
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_language)) {
                SettingsClickableRow(
                    icon = Icons.Default.Translate,
                    title = stringResource(id = R.string.settings_language),
                    value = if (currentLang == "bn") "বাংলা (Bangla)" else "English",
                    onClick = { showLanguageDialog = true }
                )
            }
        }
    }

    // Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(id = R.string.settings_language)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    preferencesManager.setLanguage("en")
                                    onLanguageChanged("en")
                                    showLanguageDialog = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentLang == "en", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("English", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    preferencesManager.setLanguage("bn")
                                    onLanguageChanged("bn")
                                    showLanguageDialog = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentLang == "bn", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("বাংলা (Bangla)", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Video View Mode Dialog
    if (showVideoViewModeDialog) {
        AlertDialog(
            onDismissRequest = { showVideoViewModeDialog = false },
            title = { Text(stringResource(id = R.string.settings_video_view_mode)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch { preferencesManager.setVideoViewMode("files") }
                                showVideoViewModeDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = videoViewMode == "files", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("All Videos (Flat List)", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch { preferencesManager.setVideoViewMode("folders") }
                                showVideoViewModeDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = videoViewMode == "folders", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("By Folders", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Audio View Mode Dialog
    if (showAudioViewModeDialog) {
        AlertDialog(
            onDismissRequest = { showAudioViewModeDialog = false },
            title = { Text(stringResource(id = R.string.settings_audio_view_mode)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch { preferencesManager.setAudioViewMode("files") }
                                showAudioViewModeDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioViewMode == "files", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("All Songs (Flat List)", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch { preferencesManager.setAudioViewMode("folders") }
                                showAudioViewModeDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioViewMode == "folders", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("By Folders", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Resume Mode Dialog
    if (showResumeModeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeModeDialog = false },
            title = { Text(stringResource(id = R.string.dialog_resume_title)) },
            text = {
                Column {
                    listOf(
                        "ask" to stringResource(id = R.string.settings_resume_mode_ask),
                        "resume" to stringResource(id = R.string.settings_resume_mode_always),
                        "beginning" to stringResource(id = R.string.settings_resume_mode_never)
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setResumeMode(mode) }
                                    showResumeModeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = resumeMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Speed Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(id = R.string.settings_default_speed)) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setDefaultSpeed(speed) }
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = defaultSpeed == speed, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${speed}x", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Seek Dialog
    if (showSeekDialog) {
        AlertDialog(
            onDismissRequest = { showSeekDialog = false },
            title = { Text(stringResource(id = R.string.settings_double_tap_seek)) },
            text = {
                Column {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setDoubleTapSeekSec(sec) }
                                    showSeekDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = doubleTapSeekSec == sec, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$sec seconds", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Orientation Dialog
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
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setDefaultOrientation(mode) }
                                    showOrientationDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = defaultOrientation == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Decoder Dialog
    if (showDecoderDialog) {
        AlertDialog(
            onDismissRequest = { showDecoderDialog = false },
            title = { Text(stringResource(id = R.string.settings_hw_decoder)) },
            text = {
                Column {
                    listOf(
                        "auto" to stringResource(id = R.string.settings_decoder_auto),
                        "hardware" to stringResource(id = R.string.settings_decoder_hw),
                        "software" to stringResource(id = R.string.settings_decoder_sw)
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setHwDecoderMode(mode) }
                                    showDecoderDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = hwDecoderMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Hidden Folders Management Dialog
    if (showHiddenFoldersDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenFoldersDialog = false },
            title = { Text(stringResource(id = R.string.settings_hidden_folders_title)) },
            text = {
                if (hiddenFolders.isEmpty()) {
                    Text(stringResource(id = R.string.settings_hidden_folders_empty))
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        hiddenFolders.forEach { folderName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(folderName, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.unhideFolder(folderName)
                                    }
                                }) {
                                    Text(stringResource(id = R.string.folders_unhide), color = ThrynxCyan)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenFoldersDialog = false }) {
                    Text(stringResource(id = R.string.action_done))
                }
            }
        )
    }

    // Private Vault PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(id = R.string.settings_private_pin)) },
            text = {
                Column {
                    Text("Enter a 4-digit PIN for your private video vault.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            coroutineScope.launch {
                                preferencesManager.setPrivateVaultPin(pinInput)
                                showPinDialog = false
                                pinInput = ""
                                Toast.makeText(context, "PIN updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = pinInput.length == 4
                ) {
                    Text(stringResource(id = R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pinInput = "" }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Development & Architecture Dialog
    if (showDevInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDevInfoDialog = false },
            title = { Text("THRYNX Video Player") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Engine: AndroidX Media3 ExoPlayer", fontWeight = FontWeight.Bold)
                    Text("Architecture: Jetpack Compose + Room + DataStore")
                    Text("Language: Kotlin 1.9.22")
                    Text("Target SDK: Android 14 (API 34)")
                    Text("Min SDK: Android 8.0 (API 26)")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Built for smooth local playback, pinch-to-zoom, gesture HUD, and hardware decoding.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Subtitle Customization Dialog
    if (showSubtitleDialog) {
        var fontSize by remember { mutableFloatStateOf(subtitleStyle.fontSizeSp) }
        var selectedColor by remember { mutableStateOf(subtitleStyle.textColorHex) }
        var bgOpacity by remember { mutableFloatStateOf(subtitleStyle.backgroundOpacity) }

        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text(stringResource(id = R.string.settings_subtitle_style)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Font Size: ${fontSize.toInt()}sp", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 12f..32f,
                        steps = 9
                    )

                    Text("Color", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("#FFFFFF" to Color.White, "#FFEB3B" to Color.Yellow, "#00E5FF" to Color.Cyan, "#00E676" to Color.Green).forEach { (hex, color) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = hex }
                                    .then(
                                        if (selectedColor == hex) Modifier.clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f)) else Modifier
                                    )
                            )
                        }
                    }

                    Text("Background Opacity: ${(bgOpacity * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = bgOpacity,
                        onValueChange = { bgOpacity = it },
                        valueRange = 0f..1f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.updateSubtitleStyle(
                                subtitleStyle.copy(
                                    fontSizeSp = fontSize,
                                    textColorHex = selectedColor,
                                    backgroundOpacity = bgOpacity
                                )
                            )
                            showSubtitleDialog = false
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ThrynxCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ThrynxBgDark,
                checkedTrackColor = ThrynxCyan
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun getFolderSize(file: File): Long {
    var size = 0L
    file.listFiles()?.forEach {
        size += if (it.isDirectory) getFolderSize(it) else it.length()
    }
    return size
}
