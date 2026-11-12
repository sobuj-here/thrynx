package com.thrynx.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.feature.home.MediaItemRow

@Composable
fun LibraryScreen(
    videos: List<MediaItemModel>,
    audios: List<MediaItemModel>,
    onOpenMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Videos", "Audio", "Folders", "Favorites")

    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val folders = remember(videos, audios) {
        (videos + audios).groupBy { it.folderName }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(title = stringResource(id = R.string.nav_library))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ThrynxCyan
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        selectedFolder = null
                    },
                    text = { Text(title) }
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search files in library…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp)
        )

        when (selectedTab) {
            0 -> { // Videos
                val filtered = videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(filtered) { video ->
                        MediaItemRow(media = video, onClick = { onOpenMedia(video) })
                    }
                }
            }
            1 -> { // Audios
                val filtered = audios.filter { it.title.contains(searchQuery, ignoreCase = true) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(filtered) { audio ->
                        MediaItemRow(media = audio, onClick = { onOpenMedia(audio) })
                    }
                }
            }
            2 -> { // Folders
                if (selectedFolder != null) {
                    val folderItems = folders[selectedFolder] ?: emptyList()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFolder = null }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Folder: $selectedFolder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(folderItems) { item ->
                                MediaItemRow(media = item, onClick = { onOpenMedia(item) })
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(folders.keys.toList()) { folderName ->
                            val count = folders[folderName]?.size ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolder = folderName }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ThrynxCardDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = ThrynxCyan)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folderName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "$count media files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            3 -> { // Favorites
                val favs = (videos + audios).filter { it.isFavorite }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    if (favs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No favorites added yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(favs) { item ->
                            MediaItemRow(media = item, onClick = { onOpenMedia(item) })
                        }
                    }
                }
            }
        }
    }
}
