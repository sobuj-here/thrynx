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
    val resumePlayback by preferencesManager.resumePlaybackFlow.collectAsState(initial = true)
    val backgroundAudio by preferencesManager.backgroundAudioFlow.collectAsState(initial = false)
    val gesturesEnabled by preferencesManager.gesturesEnabledFlow.collectAsState(initial = true)
    val defaultOrientation by preferencesManager.defaultOrientationFlow.collectAsState(initial = "auto")
    val defaultResizeMode by preferencesManager.defaultResizeModeFlow.collectAsState(initial = "fit")
    val autoPlayNext by preferencesManager.autoPlayNextFlow.collectAsState(initial = true)
    val hwDecoderMode by preferencesManager.hwDecoderModeFlow.collectAsState(initial = "auto")
    val vaultPin by preferencesManager.privateVaultPinFlow.collectAsState(initial = "")
    val subtitleStyle by preferencesManager.subtitleStyleFlow.collectAsState(initial = com.thrynx.app.core.model.SubtitleStyle())

    // Dialog states
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showSubtitleDialog by remember { mutableStateOf(false) }

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

        // Section: Player Controls
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_player)) {
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
                SettingsToggleRow(
                    icon = Icons.Default.Restore,
                    title = stringResource(id = R.string.settings_resume_playback),
                    subtitle = stringResource(id = R.string.settings_resume_playback_desc),
                    checked = resumePlayback,
                    onCheckedChange = { coroutineScope.launch { preferencesManager.setResumePlayback(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    icon = Icons.Default.SkipNext,
                    title = stringResource(id = R.string.settings_auto_next),
                    subtitle = stringResource(id = R.string.settings_auto_next_desc),
                    checked = autoPlayNext,
                    onCheckedChange = { coroutineScope.launch { preferencesManager.setAutoPlayNext(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    icon = Icons.Default.Headphones,
                    title = stringResource(id = R.string.settings_background_play),
                    subtitle = stringResource(id = R.string.settings_background_play_desc),
                    checked = backgroundAudio,
                    onCheckedChange = { coroutineScope.launch { preferencesManager.setBackgroundAudio(it) } }
                )
            }
        }

        // Section: Gestures & Display
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_gestures)) {
                SettingsToggleRow(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(id = R.string.settings_gestures),
                    subtitle = stringResource(id = R.string.settings_gestures_desc),
                    checked = gesturesEnabled,
                    onCheckedChange = { coroutineScope.launch { preferencesManager.setGesturesEnabled(it) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.ScreenRotation,
                    title = stringResource(id = R.string.settings_orientation),
                    value = defaultOrientation.replaceFirstChar { it.uppercase() },
                    onClick = { showOrientationDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.AspectRatio,
                    title = stringResource(id = R.string.settings_resize_mode),
                    value = defaultResizeMode.replaceFirstChar { it.uppercase() },
                    onClick = { showResizeDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.Memory,
                    title = stringResource(id = R.string.settings_hw_decoder),
                    value = hwDecoderMode.replaceFirstChar { it.uppercase() },
                    onClick = { showDecoderDialog = true }
                )
            }
        }

        // Section: Subtitles
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_subtitles)) {
                SettingsClickableRow(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(id = R.string.player_subtitle_styling),
                    value = "${subtitleStyle.fontSizeSp.toInt()} sp • ${subtitleStyle.textColorHex}",
                    onClick = { showSubtitleDialog = true }
                )
            }
        }

        // Section: Security & Vault
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_security)) {
                SettingsClickableRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(id = R.string.settings_private_pin),
                    value = if (vaultPin.isBlank()) "Not Set" else "Active (••••)",
                    onClick = {
                        pinInput = ""
                        showPinDialog = true
                    }
                )
            }
        }

        // Section: Library & Storage
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_library)) {
                SettingsClickableRow(
                    icon = Icons.Default.Refresh,
                    title = stringResource(id = R.string.settings_rescan),
                    value = "Run Now",
                    onClick = {
                        onRescanLibrary()
                        Toast.makeText(context, "Scanning library…", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.CleaningServices,
                    title = stringResource(id = R.string.settings_clear_cache),
                    value = formatFileSize(cacheSizeBytes),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            deleteFolderContents(context.cacheDir)
                            context.externalCacheDir?.let { deleteFolderContents(it) }
                            withContext(Dispatchers.Main) {
                                refreshCacheSize()
                                Toast.makeText(context, context.getString(R.string.settings_clear_cache_confirm), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.History,
                    title = stringResource(id = R.string.settings_clear_history),
                    value = "Clear",
                    onClick = {
                        onClearHistory()
                        Toast.makeText(context, context.getString(R.string.settings_clear_history_confirm), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Section: App & Language
        item {
            SettingsCategoryCard(title = stringResource(id = R.string.settings_section_app)) {
                SettingsClickableRow(
                    icon = Icons.Default.Translate,
                    title = stringResource(id = R.string.settings_language),
                    value = if (currentLang == "bn") "বাংলা (Bangla)" else "English",
                    onClick = { showLanguageDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(id = R.string.settings_theme),
                    value = currentTheme.replaceFirstChar { it.uppercase() },
                    onClick = { showThemeDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsClickableRow(
                    icon = Icons.Default.Info,
                    title = stringResource(id = R.string.settings_app_info),
                    value = "v1.0.0 (Build 1)",
                    onClick = {
                        Toast.makeText(context, "THRYNX Professional Video Player", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Offline Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ThrynxCardDark)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(id = R.string.settings_offline_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Language Dialog
    if (showLanguageDialog) {
        val languages = listOf(
            Pair("en", "English"),
            Pair("bn", "বাংলা (Bangla)")
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(id = R.string.settings_language)) },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setLanguage(code)
                                        showLanguageDialog = false
                                        onLanguageChanged(code)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLang == code,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setLanguage(code)
                                        showLanguageDialog = false
                                        onLanguageChanged(code)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, fontWeight = FontWeight.Medium)
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

    // Theme Dialog
    if (showThemeDialog) {
        val themes = listOf(
            Pair("dark", "Dark Mode (Recommended)"),
            Pair("light", "Light Mode"),
            Pair("system", "Follow System")
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(id = R.string.settings_theme)) },
            text = {
                Column {
                    themes.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setTheme(code)
                                        showThemeDialog = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == code,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setTheme(code)
                                        showThemeDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name)
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

    // Default Speed Dialog
    if (showSpeedDialog) {
        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(id = R.string.settings_default_speed)) },
            text = {
                Column {
                    speedOptions.forEach { sp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultSpeed(sp)
                                        showSpeedDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultSpeed == sp,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultSpeed(sp)
                                        showSpeedDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${sp}x")
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

    // Seek Duration Dialog
    if (showSeekDialog) {
        val seekOptions = listOf(5, 10, 15, 30)
        AlertDialog(
            onDismissRequest = { showSeekDialog = false },
            title = { Text(stringResource(id = R.string.settings_double_tap_seek)) },
            text = {
                Column {
                    seekOptions.forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setDoubleTapSeekSec(sec)
                                        showSeekDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = doubleTapSeekSec == sec,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setDoubleTapSeekSec(sec)
                                        showSeekDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$sec seconds")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekDialog = false }) {
                    Text(stringResource(id = R.string.action_close))
                }
            }
        )
    }

    // Orientation Dialog
    if (showOrientationDialog) {
        val options = listOf("auto", "portrait", "landscape")
        AlertDialog(
            onDismissRequest = { showOrientationDialog = false },
            title = { Text(stringResource(id = R.string.settings_orientation)) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultOrientation(opt)
                                        showOrientationDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultOrientation == opt,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultOrientation(opt)
                                        showOrientationDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(opt.replaceFirstChar { it.uppercase() })
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

    // Resize Mode Dialog
    if (showResizeDialog) {
        val options = listOf(
            Pair("fit", "Fit to Screen"),
            Pair("fill", "Fill Screen (Crop)"),
            Pair("stretch", "Stretch to Window")
        )
        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            title = { Text(stringResource(id = R.string.settings_resize_mode)) },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultResizeMode(mode)
                                        showResizeDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultResizeMode == mode,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultResizeMode(mode)
                                        showResizeDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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

    // Decoder Mode Dialog
    if (showDecoderDialog) {
        val options = listOf(
            Pair("auto", "Automatic (Default)"),
            Pair("hardware", "Hardware Accelerated"),
            Pair("software", "Software Decoder")
        )
        AlertDialog(
            onDismissRequest = { showDecoderDialog = false },
            title = { Text(stringResource(id = R.string.settings_hw_decoder)) },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setHwDecoderMode(mode)
                                        showDecoderDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = hwDecoderMode == mode,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setHwDecoderMode(mode)
                                        showDecoderDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDecoderDialog = false }) {
                    Text(stringResource(id = R.string.action_close))
                }
            }
        )
    }

    // Private PIN Setup Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(id = R.string.settings_private_pin_setup)) },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit PIN for your private video vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) pinInput = it
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        placeholder = { Text("••••") },
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
                                Toast.makeText(context, "PIN updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                ) {
                    Text(stringResource(id = R.string.action_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Subtitle Customization Dialog
    if (showSubtitleDialog) {
        var fontSize by remember { mutableFloatStateOf(subtitleStyle.fontSizeSp) }
        var textColor by remember { mutableStateOf(subtitleStyle.textColorHex) }
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text(stringResource(id = R.string.player_subtitle_styling)) },
            text = {
                Column {
                    Text("Font Size: ${fontSize.toInt()} sp", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 12f..32f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Text Color", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#FFFFFF", "#FFFF00", "#00E5FF", "#00E676").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { textColor = hex }
                                    .then(
                                        if (textColor == hex) Modifier.padding(2.dp) else Modifier
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            preferencesManager.updateSubtitleStyle(
                                subtitleStyle.copy(fontSizeSp = fontSize, textColorHex = textColor)
                            )
                            showSubtitleDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
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
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ThrynxCyan,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(content = content)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
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
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ThrynxBgDark, checkedTrackColor = ThrynxCyan)
        )
    }
}

fun getFolderSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getFolderSize(file) else file.length()
    }
    return size
}

fun deleteFolderContents(dir: File?) {
    if (dir == null || !dir.exists()) return
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) deleteFolderContents(file)
        file.delete()
    }
}
