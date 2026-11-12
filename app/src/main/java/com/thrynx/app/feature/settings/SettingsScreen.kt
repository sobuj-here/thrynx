package com.thrynx.app.feature.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thrynx.app.R
import com.thrynx.app.core.datastore.PreferencesManager
import com.thrynx.app.core.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentLang by preferencesManager.languageFlow.collectAsState(initial = "en")
    val currentTheme by preferencesManager.themeFlow.collectAsState(initial = "dark")
    val resumePlayback by preferencesManager.resumePlaybackFlow.collectAsState(initial = true)
    val backgroundAudio by preferencesManager.backgroundAudioFlow.collectAsState(initial = true)
    val editorRes by preferencesManager.editorResolutionFlow.collectAsState(initial = "1080p")

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var historyClearedSnackbar by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(title = stringResource(id = R.string.settings_title))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Offline-first & Privacy Assurance Badge
            item {
                ThrynxCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ThrynxEmerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = ThrynxEmerald)
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
                                text = "Your media files, projects, and history remain strictly on your local device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Language / ভাষা
            item {
                SettingsSectionHeader(stringResource(id = R.string.settings_section_language))
                ThrynxCard {
                    SettingsRow(
                        icon = Icons.Default.Translate,
                        title = stringResource(id = R.string.settings_language),
                        subtitle = if (currentLang == "bn") stringResource(id = R.string.settings_lang_bn) else stringResource(id = R.string.settings_lang_en),
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // Appearance & Themes
            item {
                SettingsSectionHeader(stringResource(id = R.string.settings_section_appearance))
                ThrynxCard {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(id = R.string.settings_theme),
                        subtitle = currentTheme.replaceFirstChar { it.uppercase() },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // Player Preferences
            item {
                SettingsSectionHeader(stringResource(id = R.string.settings_section_player))
                ThrynxCard {
                    Column {
                        SettingsToggleRow(
                            icon = Icons.Default.History,
                            title = stringResource(id = R.string.settings_remember_position),
                            subtitle = "Automatically resumes videos from last watched point",
                            checked = resumePlayback,
                            onCheckedChange = {
                                coroutineScope.launch { preferencesManager.setResumePlayback(it) }
                            }
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        SettingsToggleRow(
                            icon = Icons.Default.Headphones,
                            title = stringResource(id = R.string.settings_auto_background_audio),
                            subtitle = stringResource(id = R.string.player_audio_only_desc),
                            checked = backgroundAudio,
                            onCheckedChange = {
                                coroutineScope.launch { preferencesManager.setBackgroundAudio(it) }
                            }
                        )
                    }
                }
            }

            // Editor Preferences
            item {
                SettingsSectionHeader(stringResource(id = R.string.settings_section_editor))
                ThrynxCard {
                    SettingsRow(
                        icon = Icons.Default.Hd,
                        title = stringResource(id = R.string.settings_editor_resolution),
                        subtitle = editorRes,
                        onClick = { showResolutionDialog = true }
                    )
                }
            }

            // Privacy & Data
            item {
                SettingsSectionHeader(stringResource(id = R.string.settings_section_privacy))
                ThrynxCard {
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = stringResource(id = R.string.settings_clear_history),
                        subtitle = "Deletes all local video and music playback history",
                        onClick = {
                            onClearHistory()
                            historyClearedSnackbar = true
                        }
                    )
                }
            }

            // About THRYNX
            item {
                SettingsSectionHeader(stringResource(id = R.string.settings_section_about))
                ThrynxCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${stringResource(id = R.string.app_name)} 1.0.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThrynxCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(id = R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.settings_copyright),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Language Selection Dialog
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(id = R.string.settings_section_language)) },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setLanguage("en") }
                                    onLanguageChanged("en")
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLang == "en",
                                onClick = {
                                    coroutineScope.launch { preferencesManager.setLanguage("en") }
                                    onLanguageChanged("en")
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(id = R.string.settings_lang_en))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { preferencesManager.setLanguage("bn") }
                                    onLanguageChanged("bn")
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLang == "bn",
                                onClick = {
                                    coroutineScope.launch { preferencesManager.setLanguage("bn") }
                                    onLanguageChanged("bn")
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(id = R.string.settings_lang_bn))
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
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text(stringResource(id = R.string.settings_theme)) },
                text = {
                    Column {
                        listOf("dark", "light", "system").forEach { theme ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch { preferencesManager.setTheme(theme) }
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentTheme == theme,
                                    onClick = {
                                        coroutineScope.launch { preferencesManager.setTheme(theme) }
                                        showThemeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(theme.replaceFirstChar { it.uppercase() })
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
