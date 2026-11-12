package com.thrynx.app.feature.editor.export

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.ThrynxCyan
import com.thrynx.app.core.editor.export.ExportConfiguration
import com.thrynx.app.core.editor.export.ExportStage
import com.thrynx.app.core.model.ProjectModel

@Composable
fun ExportProgressDialog(
    project: ProjectModel,
    config: ExportConfiguration,
    stage: ExportStage,
    progress: Int,
    onCancelExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VideoFile, contentDescription = null, tint = ThrynxCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "${config.resolutionWidth}x${config.resolutionHeight} • ${config.fps} FPS • ${config.videoCodec.substringAfterLast('/')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "$progress%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThrynxCyan
                )

                if (stage == ExportStage.VALIDATING || stage == ExportStage.COMPOSITING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ThrynxCyan)
                } else {
                    LinearProgressIndicator(
                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = ThrynxCyan
                    )
                }

                Text(
                    text = stage.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancelExport) {
                Text(stringResource(id = R.string.export_cancel), color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
