package com.thrynx.app.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.datastore.PreferencesManager
import com.thrynx.app.core.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 6 Primary Professional Settings Categories.
 */
enum class SettingsSection(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    LIST(
        title = "List & Library",
        description = "Display layout, hidden folders, media scanner & cache storage",
        icon = Icons.Default.Folder
    ),
    PLAYER_CONTROLS(
        title = "Player Controls",
        description = "Speed, double-tap seek, gestures, orientation & auto-play",
        icon = Icons.Default.PlayCircle
    ),
    AUDIO(
        title = "Audio & Sound Engine",
        description = "Quality, loudness normalization, silence skipping & 10-band EQ",
        icon = Icons.Default.Headphones
    ),
    GENERAL(
        title = "General",
        description = "App theme, language (English / বাংলা) & THRYNX v1.2.0 info",
        icon = Icons.Default.Settings
    ),
    DEVELOPMENT(
        title = "Development & Security",
        description = "Private Vault PIN, biometric unlock, decoder mode & nerd stats",
        icon = Icons.Default.Security
    ),
    SUBTITLE(
        title = "Subtitle",
        description = "Font size, text color, background opacity & sync delay",
        icon = Icons.Default.Subtitles
    )
}

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    playerManager: com.thrynx.app.core.player.ThrynxPlayerManager? = null,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Currently opened section (null = Main Hub)
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }

    // Intercept back button when inside a subcategory
    BackHandler(enabled = currentSection != null) {
        currentSection = null
    }

    // State flows from PreferencesManager
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

    // Advanced Audio Engine flows
    val audioQuality by preferencesManager.audioQualityFlow.collectAsState(initial = "high")
    val loudnessNormalization by preferencesManager.loudnessNormalizationFlow.collectAsState(initial = true)
    val skipSilence by preferencesManager.skipSilenceFlow.collectAsState(initial = false)
    val crossfadeSeconds by preferencesManager.crossfadeSecondsFlow.collectAsState(initial = 0)
    val reduceAnimation by preferencesManager.reduceAnimationFlow.collectAsState(initial = false)
    val showNerdStats by preferencesManager.showNerdStatsFlow.collectAsState(initial = false)

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
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }

    // Dynamic Cache Size calculation
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    fun refreshCacheSize() {
        coroutineScope.launch(Dispatchers.IO) {
            val total = getFolderSize(context.cacheDir) + (context.externalCacheDir?.let { getFolderSize(it) } ?: 0L)
            withContext(Dispatchers.Main) { cacheSizeBytes = total }
        }
    }
    LaunchedEffect(Unit) { refreshCacheSize() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ThrynxBgDark)
    ) {
        // Top Header
        Surface(
            color = ThrynxCardDark,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentSection != null) {
                    IconButton(
                        onClick = { currentSection = null },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ThrynxCyan.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThrynxCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSection?.title ?: stringResource(id = R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentSection?.description ?: "THRYNX Media Studio Configuration",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThrynxTextMutedDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentSection == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThrynxCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v1.2.0",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ThrynxCyan
                        )
                    }
                }
            }
        }

        // Animated Body: Main Category Grid / Detailed Category List
        AnimatedContent(
            targetState = currentSection,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width / 2 } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width / 2 } + fadeIn() togetherWith
                        slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            label = "SettingsSectionTransition",
            modifier = Modifier.weight(1f)
        ) { section ->
            if (section == null) {
                // ==================== MAIN SETTINGS HUB ====================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "PREFERENCES & ENGINES",
                            style = MaterialTheme.typography.labelMedium,
                            color = ThrynxCyan,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    items(SettingsSection.values().size) { idx ->
                        val itemSection = SettingsSection.values()[idx]
                        SettingsCategoryHubCard(
                            section = itemSection,
                            onClick = { currentSection = itemSection }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ThrynxCardDark)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "THRYNX Media Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "High-performance ExoPlayer & THRYNX Audio Architecture",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ThrynxTextMutedDark
                                )
                            }
                        }
                    }
                }
            } else {
                // ==================== CATEGORY DETAIL PAGE ====================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (section) {
                        SettingsSection.LIST -> {
                            item {
                                SettingsCategoryCard(title = "Display & View Modes") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.ViewList,
                                        title = "Video Library View",
                                        value = if (videoViewMode == "files") "Files (List)" else "Folders (Grid)",
                                        onClick = { showVideoViewModeDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.Audiotrack,
                                        title = "Audio Library View",
                                        value = if (audioViewMode == "files") "Files (List)" else "Folders (Grid)",
                                        onClick = { showAudioViewModeDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.AccessTime,
                                        title = "Show Played Duration",
                                        description = "Display played time on media cards",
                                        checked = showPlayedTime,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setShowPlayedTime(checked) }
                                        }
                                    )
                                }
                            }

                            item {
                                SettingsCategoryCard(title = "Folder & Storage Management") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.FolderOff,
                                        title = "Hidden Folders",
                                        value = "${hiddenFolders.size} Hidden",
                                        onClick = { showHiddenFoldersDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsActionRow(
                                        icon = Icons.Default.Refresh,
                                        title = "Rescan Media Library",
                                        value = "Update index",
                                        onClick = {
                                            onRescanLibrary()
                                            Toast.makeText(context, "Scanning storage for media...", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsActionRow(
                                        icon = Icons.Default.CleaningServices,
                                        title = "Clear Cache",
                                        value = formatFileSize(cacheSizeBytes),
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                context.cacheDir.deleteRecursively()
                                                context.externalCacheDir?.deleteRecursively()
                                                refreshCacheSize()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Cache Cleared", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsActionRow(
                                        icon = Icons.Default.History,
                                        title = "Clear Playback History",
                                        value = "Reset recent list",
                                        onClick = {
                                            onClearHistory()
                                            Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        SettingsSection.PLAYER_CONTROLS -> {
                            item {
                                SettingsCategoryCard(title = "Playback Behaviors") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.Speed,
                                        title = "Default Playback Speed",
                                        value = "${defaultSpeed}x",
                                        onClick = { showSpeedDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.FastForward,
                                        title = "Double-Tap Seek Duration",
                                        value = "${doubleTapSeekSec}s",
                                        onClick = { showSeekDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.Replay,
                                        title = "Resume Playback",
                                        value = when (resumeMode) {
                                            "resume" -> "Resume automatically"
                                            "beginning" -> "Always start from beginning"
                                            else -> "Always ask"
                                        },
                                        onClick = { showResumeModeDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.SkipNext,
                                        title = "Auto-Play Next Video",
                                        description = "Automatically play next item in queue",
                                        checked = autoPlayNext,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setAutoPlayNext(checked) }
                                        }
                                    )
                                }
                            }

                            item {
                                SettingsCategoryCard(title = "Display & Canvas") {
                                    SettingsSwitchRow(
                                        icon = Icons.Default.TouchApp,
                                        title = "Gesture Controls",
                                        description = "Brightness, volume & seek swiping",
                                        checked = gesturesEnabled,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setGesturesEnabled(checked) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.ScreenRotation,
                                        title = "Default Orientation",
                                        value = when (defaultOrientation) {
                                            "landscape" -> "Landscape"
                                            "portrait" -> "Portrait"
                                            else -> "Auto-Rotate"
                                        },
                                        onClick = { showOrientationDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.AspectRatio,
                                        title = "Default Aspect Ratio",
                                        value = when (defaultResizeMode) {
                                            "crop" -> "Fill (Crop)"
                                            "stretch" -> "Stretch"
                                            "16_9" -> "16:9"
                                            "4_3" -> "4:3"
                                            else -> "Fit to Screen"
                                        },
                                        onClick = {
                                            val nextMode = when (defaultResizeMode) {
                                                "fit" -> "crop"
                                                "crop" -> "stretch"
                                                "stretch" -> "16_9"
                                                "16_9" -> "4_3"
                                                else -> "fit"
                                            }
                                            coroutineScope.launch { preferencesManager.setDefaultResizeMode(nextMode) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.Info,
                                        title = "Show Info HUD Overlay",
                                        description = "Display resolution, clock & battery in player",
                                        checked = showPlayerInfoHud,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setShowPlayerInfoHud(checked) }
                                        }
                                    )
                                }
                            }
                        }

                        SettingsSection.AUDIO -> {
                            item {
                                SettingsCategoryCard(title = "Audio Playback & Streaming") {
                                    SettingsSwitchRow(
                                        icon = Icons.Default.Headphones,
                                        title = "Background Audio Playback",
                                        description = "Continue playing when screen is locked or app minimized",
                                        checked = backgroundAudio,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setBackgroundAudio(checked) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.HeadsetOff,
                                        title = "Pause on Disconnect",
                                        description = "Automatically pause playback when headphones are unplugged",
                                        checked = pauseOnHeadsetDisconnect,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setPauseOnHeadsetDisconnect(checked) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.NetworkCheck,
                                        title = "Streaming Audio Quality",
                                        value = when (audioQuality) {
                                            "high" -> "High (320kbps / Opus)"
                                            "medium" -> "Medium (160kbps)"
                                            else -> "Auto Adaptive"
                                        },
                                        onClick = { showAudioQualityDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.VolumeUp,
                                        title = "Loudness Normalization",
                                        description = "Equalize perceived loudness across different tracks",
                                        checked = loudnessNormalization,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setLoudnessNormalization(checked) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.VolumeOff,
                                        title = "Skip Silence",
                                        description = "Automatically skip silent pauses in audio",
                                        checked = skipSilence,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setSkipSilence(checked) }
                                        }
                                    )
                                }
                            }

                            item {
                                SettingsCategoryCard(title = "Tuning & Transitions") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.GraphicEq,
                                        title = "10-Band Equalizer & Bass",
                                        value = "Custom Curve",
                                        onClick = { showEqualizerDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.CompareArrows,
                                        title = "Automix Crossfade",
                                        value = if (crossfadeSeconds > 0) "${crossfadeSeconds}s" else "Disabled",
                                        onClick = {
                                            val nextSeconds = when (crossfadeSeconds) {
                                                0 -> 2
                                                2 -> 4
                                                4 -> 6
                                                6 -> 8
                                                8 -> 12
                                                else -> 0
                                            }
                                            coroutineScope.launch { preferencesManager.setCrossfadeSeconds(nextSeconds) }
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.Animation,
                                        title = "Reduce Ambient Animation",
                                        description = "Pause fluid mesh gradient drift for lower power consumption",
                                        checked = reduceAnimation,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setReduceAnimation(checked) }
                                        }
                                    )
                                }
                            }
                        }

                        SettingsSection.GENERAL -> {
                            item {
                                SettingsCategoryCard(title = "Language & Appearance") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.Translate,
                                        title = "Display Language",
                                        value = if (currentLang == "bn") "বাংলা (Bangla)" else "English",
                                        onClick = { showLanguageDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.Palette,
                                        title = "Theme Accent",
                                        value = "THRYNX Dark Space (Cyan)",
                                        onClick = {
                                            Toast.makeText(context, "Dark Space Theme Active", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            item {
                                SettingsCategoryCard(title = "Application Information") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.Info,
                                        title = "About THRYNX",
                                        value = "v1.2.0 (Build 3)",
                                        onClick = { showDevInfoDialog = true }
                                    )
                                }
                            }
                        }

                        SettingsSection.DEVELOPMENT -> {
                            item {
                                SettingsCategoryCard(title = "Security & Vault") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.Lock,
                                        title = "Private Vault PIN",
                                        value = if (vaultPin.isBlank()) "Not Set" else "Active (••••)",
                                        onClick = { showPinDialog = true }
                                    )
                                }
                            }

                            item {
                                SettingsCategoryCard(title = "Engine & Diagnostics") {
                                    SettingsClickableRow(
                                        icon = Icons.Default.Memory,
                                        title = "Hardware Video Decoder",
                                        value = when (hwDecoderMode) {
                                            "hardware" -> "Hardware Accelerated"
                                            "software" -> "Software Decoder"
                                            else -> "Automatic (Recommended)"
                                        },
                                        onClick = { showDecoderDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsSwitchRow(
                                        icon = Icons.Default.BarChart,
                                        title = "Stats for Nerds",
                                        description = "Overlay stream telemetry, codecs, bitrate & dropped frames",
                                        checked = showNerdStats,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch { preferencesManager.setShowNerdStats(checked) }
                                        }
                                    )
                                }
                            }
                        }

                        SettingsSection.SUBTITLE -> {
                            item {
                                SettingsCategoryCard(title = "Subtitle Styling & Font") {
                                    // Live Preview Card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = subtitleStyle.backgroundOpacity),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Sample Subtitle (নমুনা সাবটাইটেল)",
                                                color = Color(android.graphics.Color.parseColor(subtitleStyle.textColorHex)),
                                                fontSize = subtitleStyle.fontSizeSp.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SettingsClickableRow(
                                        icon = Icons.Default.FormatSize,
                                        title = "Font Size",
                                        value = "${subtitleStyle.fontSizeSp.toInt()} sp",
                                        onClick = { showSubtitleDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.ColorLens,
                                        title = "Text Color",
                                        value = subtitleStyle.textColorHex,
                                        onClick = { showSubtitleDialog = true }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    SettingsClickableRow(
                                        icon = Icons.Default.Opacity,
                                        title = "Background Opacity",
                                        value = "${(subtitleStyle.backgroundOpacity * 100).toInt()}%",
                                        onClick = { showSubtitleDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== DIALOG IMPLEMENTATIONS ====================

    // Audio Quality Dialog
    if (showAudioQualityDialog) {
        val qualities = listOf(
            "high" to "High (320kbps / Opus)",
            "medium" to "Medium (160kbps)",
            "auto" to "Auto (Adaptive)"
        )
        AlertDialog(
            onDismissRequest = { showAudioQualityDialog = false },
            title = { Text("Streaming Audio Quality") },
            text = {
                Column {
                    qualities.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setAudioQuality(code)
                                        showAudioQualityDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = audioQuality == code,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setAudioQuality(code)
                                        showAudioQualityDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        val languages = listOf("en" to "English", "bn" to "বাংলা (Bangla)")
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
                                        onLanguageChanged(code)
                                        showLanguageDialog = false
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
                                        onLanguageChanged(code)
                                        showLanguageDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(id = R.string.settings_default_speed)) },
            text = {
                Column {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultSpeed == speed,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultSpeed(speed)
                                        showSpeedDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${speed}x", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Double Tap Seek Dialog
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
                                .padding(vertical = 10.dp),
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
                            Text("$sec seconds", style = MaterialTheme.typography.bodyLarge)
                        }
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
            title = { Text("Video Library View Mode") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    preferencesManager.setVideoViewMode("files")
                                    showVideoViewModeDialog = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = videoViewMode == "files",
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setVideoViewMode("files")
                                    showVideoViewModeDialog = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Files (All Videos List)", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    preferencesManager.setVideoViewMode("folders")
                                    showVideoViewModeDialog = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = videoViewMode == "folders",
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setVideoViewMode("folders")
                                    showVideoViewModeDialog = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Folders (Folder Directory Grid)", style = MaterialTheme.typography.bodyLarge)
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
            title = { Text("Audio Library View Mode") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    preferencesManager.setAudioViewMode("files")
                                    showAudioViewModeDialog = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioViewMode == "files",
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setAudioViewMode("files")
                                    showAudioViewModeDialog = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Files (All Tracks List)", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    preferencesManager.setAudioViewMode("folders")
                                    showAudioViewModeDialog = false
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioViewMode == "folders",
                            onClick = {
                                coroutineScope.launch {
                                    preferencesManager.setAudioViewMode("folders")
                                    showAudioViewModeDialog = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Folders (Albums & Directories)", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Orientation Dialog
    if (showOrientationDialog) {
        val orientations = listOf(
            "auto" to "Auto-Rotate",
            "landscape" to "Landscape",
            "portrait" to "Portrait"
        )
        AlertDialog(
            onDismissRequest = { showOrientationDialog = false },
            title = { Text(stringResource(id = R.string.settings_default_orientation)) },
            text = {
                Column {
                    orientations.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultOrientation(mode)
                                        showOrientationDialog = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultOrientation == mode,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setDefaultOrientation(mode)
                                        showOrientationDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
        val decoders = listOf(
            "auto" to "Automatic (Recommended)",
            "hardware" to "Hardware Accelerated",
            "software" to "Software Decoder"
        )
        AlertDialog(
            onDismissRequest = { showDecoderDialog = false },
            title = { Text(stringResource(id = R.string.settings_hw_decoder)) },
            text = {
                Column {
                    decoders.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setHwDecoderMode(mode)
                                        showDecoderDialog = false
                                    }
                                }
                                .padding(vertical = 10.dp),
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
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Resume Mode Dialog
    if (showResumeModeDialog) {
        val modes = listOf(
            "ask" to "Always Ask",
            "resume" to "Resume Automatically",
            "beginning" to "Start from Beginning"
        )
        AlertDialog(
            onDismissRequest = { showResumeModeDialog = false },
            title = { Text(stringResource(id = R.string.settings_resume_mode_title)) },
            text = {
                Column {
                    modes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesManager.setResumeMode(mode)
                                        showResumeModeDialog = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = resumeMode == mode,
                                onClick = {
                                    coroutineScope.launch {
                                        preferencesManager.setResumeMode(mode)
                                        showResumeModeDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Hidden Folders Dialog
    if (showHiddenFoldersDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenFoldersDialog = false },
            title = { Text(stringResource(id = R.string.settings_hidden_folders_title)) },
            text = {
                if (hiddenFolders.isEmpty()) {
                    Text(stringResource(id = R.string.settings_hidden_folders_empty))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(hiddenFolders.toList().size) { idx ->
                            val folderPath = hiddenFolders.toList()[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = folderPath.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    val updated = hiddenFolders.toMutableSet().apply { remove(folderPath) }
                                    coroutineScope.launch { preferencesManager.setHiddenFolders(updated) }
                                }) {
                                    Text(stringResource(id = R.string.folders_unhide))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenFoldersDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // Vault PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pinInput = "" },
            title = { Text(stringResource(id = R.string.settings_private_pin)) },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit PIN for your Private Vault. Leave blank and tap Clear to remove PIN.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        placeholder = { Text("4-digit PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        preferencesManager.setPrivateVaultPin(pinInput)
                        showPinDialog = false
                        pinInput = ""
                        Toast.makeText(context, "PIN Updated", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(if (pinInput.isBlank()) "Clear PIN" else stringResource(id = R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false; pinInput = "" }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    // App Information Dialog
    if (showDevInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDevInfoDialog = false },
            title = { Text("THRYNX Media Studio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Version: 1.2.0 (Build 3)", fontWeight = FontWeight.Bold, color = ThrynxCyan)
                    Text("Video Engine: AndroidX Media3 (ExoPlayer)")
                    Text("Audio Engine: THRYNX Adaptive Stream Resolver")
                    Text("UI Architecture: Jetpack Compose Material 3")
                    Text("Audio Scrubber: Apple Music ThinSlider Engine")
                    Text("Ambient Backdrop: Fluid Blur Mesh Gradient")
                    Text("Target SDK: Android 14 (API 34)")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevInfoDialog = false }) {
                    Text("OK", color = ThrynxCyan)
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
                Column {
                    Text("Font Size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 12f..36f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Background Opacity: ${(bgOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = bgOpacity,
                        onValueChange = { bgOpacity = it },
                        valueRange = 0f..1f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Color:", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("#FFFFFF", "#FFFF00", "#00E5FF", "#00FF00").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
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
                    Text(stringResource(id = R.string.action_save), color = ThrynxCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }

    if (showEqualizerDialog && playerManager != null) {
        com.thrynx.app.feature.audio.EqualizerSheet(
            playerManager = playerManager,
            onDismiss = { showEqualizerDialog = false }
        )
    }
}

// ==================== REUSABLE UI COMPONENTS ====================

@Composable
fun SettingsCategoryHubCard(
    section: SettingsSection,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ThrynxCardDark),
        border = BorderStroke(1.dp, ThrynxCardDark.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThrynxCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = ThrynxCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ThrynxTextMutedDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ThrynxCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = ThrynxCardDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = ThrynxCyan,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ThrynxCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = ThrynxTextMutedDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ThrynxTextMutedDark.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
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
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ThrynxCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            description?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = ThrynxTextMutedDark
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
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
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ThrynxCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        value?.let {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = ThrynxTextMutedDark
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ThrynxTextMutedDark.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun getFolderSize(file: File): Long {
    var size = 0L
    file.listFiles()?.forEach {
        size += if (it.isDirectory) getFolderSize(it) else it.length()
    }
    return size
}
