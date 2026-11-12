package com.thrynx.app.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.feature.home.formatDuration
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProjectsScreen(
    projects: List<ProjectModel>,
    onOpenProject: (ProjectModel) -> Unit,
    onDeleteProject: (ProjectModel) -> Unit,
    onDuplicateProject: (ProjectModel) -> Unit,
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var projectToDelete by remember { mutableStateOf<ProjectModel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(
            title = stringResource(id = R.string.projects_title),
            actions = {
                IconButton(onClick = onNewProject) {
                    Icon(Icons.Default.Add, contentDescription = "New Project", tint = ThrynxCyan)
                }
            }
        )

        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.projects_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(projects) { project ->
                    ProjectGridItem(
                        project = project,
                        onClick = { onOpenProject(project) },
                        onDuplicate = { onDuplicateProject(project) },
                        onDelete = { projectToDelete = project }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        projectToDelete?.let { proj ->
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = { Text(stringResource(id = R.string.projects_delete)) },
                text = { Text(stringResource(id = R.string.projects_delete_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteProject(proj)
                            projectToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ThrynxCoral)
                    ) {
                        Text(stringResource(id = R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun ProjectGridItem(
    project: ProjectModel,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    ThrynxCard(onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(ThrynxCardDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideoCameraBack,
                    contentDescription = null,
                    tint = ThrynxCyan,
                    modifier = Modifier.size(40.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ThrynxBgDark.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(project.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(project.updatedAt))
                Text(
                    text = stringResource(id = R.string.projects_last_modified, dateStr),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = ThrynxCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ThrynxCoral, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
