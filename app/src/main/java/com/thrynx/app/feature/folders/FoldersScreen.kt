package com.thrynx.app.feature.folders

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thrynx.app.R
import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel
import kotlinx.coroutines.launch

@Composable
fun FoldersScreen(
    folders: List<FolderItemModel>,
    videos: List<MediaItemModel>,
    privateVideos: List<PrivateVideoEntity>,
    vaultPin: String,
    onSetVaultPin: (String) -> Unit,
    onUnhidePrivateVideo: (PrivateVideoEntity) -> Unit,
    onAddCustomFolder: (Uri) -> Unit,
    onOpenMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFolder by remember { mutableStateOf<FolderItemModel?>(null) }
    var isVaultUnlocked by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isSettingNewPin by remember { mutableStateOf(false) }

    // SAF Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddCustomFolder(uri)
            Toast.makeText(context, "Folder added to library", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle back button when inside a folder or vault
    BackHandler(enabled = selectedFolder != null || isVaultUnlocked) {
        if (isVaultUnlocked) {
            isVaultUnlocked = false
        } else {
            selectedFolder = null
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedFolder == null && !isVaultUnlocked) {
                FloatingActionButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    containerColor = ThrynxCyan,
                    contentColor = ThrynxBgDark,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(id = R.string.folders_add_custom))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.folders_add_custom), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isVaultUnlocked) {
                // Inside Private Vault View
                VaultContentView(
                    privateVideos = privateVideos,
                    onBack = { isVaultUnlocked = false },
                    onOpenVideo = { pv ->
                        onOpenMedia(
                            MediaItemModel(
                                title = pv.title,
                                uri = Uri.parse(pv.mediaUri),
                                path = pv.originalPath,
                                durationMs = pv.durationMs,
                                sizeBytes = pv.sizeBytes,
                                isVideo = true
                            )
                        )
                    },
                    onUnhide = onUnhidePrivateVideo
                )
            } else if (selectedFolder != null) {
                // Inside Specific Video Folder View
                val folder = selectedFolder!!
                val folderVideos = remember(videos, folder) {
                    videos.filter { it.folderName.equals(folder.name, ignoreCase = true) }
                }

                FolderDetailView(
                    folder = folder,
                    videos = folderVideos,
                    onBack = { selectedFolder = null },
                    onOpenMedia = onOpenMedia
                )
            } else {
                // Main Folders Overview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.folders_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ThrynxCyan
                        )
                        Text(
                            text = "${folders.size} video folders",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Private Vault Card Banner
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (vaultPin.isBlank()) {
                                        isSettingNewPin = true
                                    } else {
                                        isSettingNewPin = false
                                    }
                                    pinInput = ""
                                    pinError = false
                                    showVaultDialog = true
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = ThrynxCardDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ThrynxCyan.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(ThrynxCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = ThrynxCyan,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(id = R.string.folders_private_vault),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (vaultPin.isBlank()) "Tap to setup secure 4-digit PIN"
                                        else "${privateVideos.size} hidden videos protected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ThrynxCyan
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = ThrynxCyan
                                )
                            }
                        }
                    }

                    // 2. Folder List
                    if (folders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.folders_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(folders) { folder ->
                            FolderRowCard(
                                folder = folder,
                                onClick = { selectedFolder = folder }
                            )
                        }
                    }
                }
            }
        }
    }

    // Vault PIN Dialog (Unlock or Setup)
    if (showVaultDialog) {
        AlertDialog(
            onDismissRequest = { showVaultDialog = false },
            title = {
                Text(
                    text = if (isSettingNewPin) stringResource(id = R.string.folders_vault_set_pin)
                    else stringResource(id = R.string.folders_vault_unlock)
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isSettingNewPin) "Create a 4-digit security PIN to protect your private vault."
                        else "Enter your 4-digit PIN to access private hidden videos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pinInput = it
                                pinError = false
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError,
                        placeholder = { Text("••••") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(id = R.string.folders_vault_wrong_pin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSettingNewPin) {
                            if (pinInput.length == 4) {
                                onSetVaultPin(pinInput)
                                showVaultDialog = false
                                isVaultUnlocked = true
                                Toast.makeText(context, "PIN configured successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                pinError = true
                            }
                        } else {
                            if (pinInput == vaultPin) {
                                showVaultDialog = false
                                isVaultUnlocked = true
                            } else {
                                pinError = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                ) {
                    Text(
                        text = if (isSettingNewPin) stringResource(id = R.string.action_save)
                        else stringResource(id = R.string.action_confirm),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showVaultDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun FolderRowCard(
    folder: FolderItemModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141C2B)),
                contentAlignment = Alignment.Center
            ) {
                if (folder.latestMedia != null) {
                    AsyncImage(
                        model = folder.latestMedia.uri ?: folder.latestMedia.path,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = ThrynxCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.folders_videos_count, folder.videoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folder.path.isNotBlank()) {
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FolderDetailView(
    folder: FolderItemModel,
    videos: List<MediaItemModel>,
    onBack: () -> Unit,
    onOpenMedia: (MediaItemModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${videos.size} videos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenMedia(video) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141C2B)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = video.uri ?: video.path,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${formatDuration(video.durationMs)} • ${video.resolutionLabel} • ${formatFileSize(video.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultContentView(
    privateVideos: List<PrivateVideoEntity>,
    onBack: () -> Unit,
    onOpenVideo: (PrivateVideoEntity) -> Unit,
    onUnhide: (PrivateVideoEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.folders_private_vault),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ThrynxCyan
                )
                Text(
                    text = "${privateVideos.size} hidden videos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(id = R.string.folders_vault_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (privateVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.folders_vault_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(privateVideos) { pv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpenVideo(pv) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141C2B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = ThrynxCyan, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatDuration(pv.durationMs)} • ${formatFileSize(pv.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onUnhide(pv) }) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = stringResource(id = R.string.folders_vault_unhide_video),
                                tint = ThrynxCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
