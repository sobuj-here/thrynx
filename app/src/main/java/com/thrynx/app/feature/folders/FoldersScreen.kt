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
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.thrynx.app.core.database.CustomFolderEntity
import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.FolderItemModel
import com.thrynx.app.core.model.MediaItemModel

enum class FolderSortOption(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    COUNT_DESC("Videos (Most first)"),
    COUNT_ASC("Videos (Least first)")
}

@Composable
fun FoldersScreen(
    folders: List<FolderItemModel>,
    videos: List<MediaItemModel>,
    privateVideos: List<PrivateVideoEntity>,
    customFolders: List<CustomFolderEntity> = emptyList(),
    vaultPin: String,
    hasStoragePermission: Boolean = true,
    canUseBiometric: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onSetVaultPin: (String) -> Unit,
    onUnhidePrivateVideo: (PrivateVideoEntity) -> Unit,
    onAddCustomFolder: (Uri) -> Unit,
    onRemoveCustomFolder: (String) -> Unit = {},
    onOpenMedia: (MediaItemModel) -> Unit,
    onPlayAll: (List<MediaItemModel>) -> Unit,
    onLaunchBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFolder by remember { mutableStateOf<FolderItemModel?>(null) }
    var isVaultUnlocked by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isSettingNewPin by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(FolderSortOption.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // SAF Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddCustomFolder(uri)
            Toast.makeText(context, "Folder added to library", Toast.LENGTH_SHORT).show()
        }
    }

    // Process and sort folders
    val processedFolders = remember(folders, sortOption) {
        when (sortOption) {
            FolderSortOption.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            FolderSortOption.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            FolderSortOption.COUNT_DESC -> folders.sortedByDescending { it.videoCount }
            FolderSortOption.COUNT_ASC -> folders.sortedBy { it.videoCount }
        }
    }

    val customFolderUris = remember(customFolders) {
        customFolders.map { it.folderUri }.toSet()
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
                    onOpenMedia = onOpenMedia,
                    onPlayAll = { onPlayAll(folderVideos) }
                )
            } else {
                // Main Folders Overview Top Bar
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
                            text = "${processedFolders.size} video folders",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Sort Folders Menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Folders", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            FolderSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) ThrynxCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Storage Permission Missing Warning Banner
                if (!hasStoragePermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage Permission Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Allow access to discover all videos on your device",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark)
                            ) {
                                Text("Grant", fontWeight = FontWeight.Bold)
                            }
                        }
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
                                        pinInput = ""
                                        pinError = false
                                        showVaultDialog = true
                                    } else {
                                        if (canUseBiometric) {
                                            onLaunchBiometric(
                                                { isVaultUnlocked = true },
                                                {
                                                    // Fallback to PIN dialog on biometric error/cancel
                                                    isSettingNewPin = false
                                                    pinInput = ""
                                                    pinError = false
                                                    showVaultDialog = true
                                                }
                                            )
                                        } else {
                                            isSettingNewPin = false
                                            pinInput = ""
                                            pinError = false
                                            showVaultDialog = true
                                        }
                                    }
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
                    if (processedFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(id = R.string.folders_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(processedFolders) { folder ->
                            val isCustom = customFolderUris.contains(folder.path) || customFolderUris.any { folder.path.startsWith(it) }
                            FolderRowCard(
                                folder = folder,
                                isCustom = isCustom,
                                onRemoveCustom = {
                                    val customEntity = customFolders.firstOrNull { it.folderUri == folder.path || folder.path.startsWith(it.folderUri) }
                                    if (customEntity != null) {
                                        onRemoveCustomFolder(customEntity.folderUri)
                                        Toast.makeText(context, "Folder removed from app", Toast.LENGTH_SHORT).show()
                                    }
                                },
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

                    if (canUseBiometric && !isSettingNewPin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                showVaultDialog = false
                                onLaunchBiometric(
                                    { isVaultUnlocked = true },
                                    { pinError = true }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ThrynxCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Biometric Unlock", color = ThrynxCyan)
                        }
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
    isCustom: Boolean = false,
    onRemoveCustom: () -> Unit = {},
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
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ThrynxBgDark),
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ThrynxCyan.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "SAF",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThrynxCyan,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${folder.videoCount} videos • ${folder.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCustom) {
                IconButton(onClick = onRemoveCustom) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Remove folder",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FolderDetailView(
    folder: FolderItemModel,
    videos: List<MediaItemModel>,
    onBack: () -> Unit,
    onOpenMedia: (MediaItemModel) -> Unit,
    onPlayAll: () -> Unit
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

            if (videos.isNotEmpty()) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan, contentColor = ThrynxBgDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMedia(video) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ThrynxCardDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = video.uri ?: video.path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatDuration(video.durationMs)} • ${formatFileSize(video.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = ThrynxCyan
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
                    text = "${privateVideos.size} hidden videos protected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (privateVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = ThrynxCyan.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Vault is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Move sensitive videos here from the Videos tab action menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(privateVideos) { pv ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenVideo(pv) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ThrynxCardDark)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = pv.mediaUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pv.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatDuration(pv.durationMs)} • ${formatFileSize(pv.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { onUnhide(pv) }) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = "Unhide",
                                    tint = ThrynxCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
