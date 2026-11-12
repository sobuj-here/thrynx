package com.thrynx.app.feature.editor.export

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.R
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.editor.export.ExportConfiguration
import com.thrynx.app.core.editor.export.ExportQualityPreset
import com.thrynx.app.core.editor.export.ExportResolutionPreset
import com.thrynx.app.core.model.ProjectModel
import java.util.Locale

@Composable
fun ExportSettingsDialog(
    project: ProjectModel,
    onDismissRequest: () -> Unit,
    onStartExport: (ExportConfiguration) -> Unit
) {
    var selectedResolution by remember { mutableStateOf(ExportResolutionPreset.RES_1080P) }
    var selectedFps by remember { mutableIntStateOf(30) }
    var selectedQuality by remember { mutableStateOf(ExportQualityPreset.BALANCED) }
    var isAudioEnabled by remember { mutableStateOf(true) }

    val currentConfig = remember(selectedResolution, selectedFps, selectedQuality, isAudioEnabled) {
        ExportConfiguration(
            resolutionWidth = selectedResolution.width,
            resolutionHeight = selectedResolution.height,
            fps = selectedFps,
            bitrateMbps = when (selectedResolution) {
                ExportResolutionPreset.RES_480P -> 5
                ExportResolutionPreset.RES_720P -> 8
                ExportResolutionPreset.RES_1080P -> 12
                ExportResolutionPreset.RES_1440P -> 20
                ExportResolutionPreset.RES_2160P -> 35
            },
            qualityPreset = selectedQuality,
            isAudioEnabled = isAudioEnabled,
            aspectRatio = project.canvasRatio
        )
    }

    val availableFreeBytes = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            10L * 1024 * 1024 * 1024 // fallback 10GB
        }
    }

    val estimatedSizeBytes = currentConfig.calculateEstimatedSizeBytes(project.durationMs)
    val hasEnoughStorage = availableFreeBytes >= (estimatedSizeBytes * 1.5)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VideoSettings, contentDescription = null, tint = ThrynxCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.export_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Resolution Selector
                Text(stringResource(id = R.string.export_resolution), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ExportResolutionPreset.RES_720P,
                        ExportResolutionPreset.RES_1080P,
                        ExportResolutionPreset.RES_2160P
                    ).forEach { preset ->
                        val isSelected = selectedResolution == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ThrynxCyan else ThrynxCardDark)
                                .border(1.dp, if (isSelected) ThrynxCyan else ThrynxCardStrokeDark, RoundedCornerShape(8.dp))
                                .clickable { selectedResolution = preset }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.label.takeWhile { it != ' ' },
                                color = if (isSelected) ThrynxBgDark else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Frame Rate Selector
                Text(stringResource(id = R.string.export_fps), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(24, 30, 60).forEach { fps ->
                        val isSelected = selectedFps == fps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ThrynxCyan else ThrynxCardDark)
                                .border(1.dp, if (isSelected) ThrynxCyan else ThrynxCardStrokeDark, RoundedCornerShape(8.dp))
                                .clickable { selectedFps = fps }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$fps FPS",
                                color = if (isSelected) ThrynxBgDark else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Quality Selector
                Text("Encoding Quality", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ExportQualityPreset.LOW, ExportQualityPreset.BALANCED, ExportQualityPreset.HIGH).forEach { q ->
                        val isSelected = selectedQuality == q
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ThrynxCyan else ThrynxCardDark)
                                .border(1.dp, if (isSelected) ThrynxCyan else ThrynxCardStrokeDark, RoundedCornerShape(8.dp))
                                .clickable { selectedQuality = q }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = q.label,
                                color = if (isSelected) ThrynxBgDark else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Audio Track Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include Audio Tracks", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isAudioEnabled,
                        onCheckedChange = { isAudioEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ThrynxCyan)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Estimated File Size & Available Storage Preflight
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Estimated File Size: ${currentConfig.formatEstimatedSize(project.durationMs)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ThrynxCyan
                    )
                    Text(
                        text = "Available Storage: ${String.format(Locale.US, "%.1f GB", availableFreeBytes / (1024.0 * 1024.0 * 1024.0))}",
                        fontSize = 11.sp,
                        color = if (hasEnoughStorage) ThrynxEmerald else ThrynxCoral
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStartExport(currentConfig) },
                enabled = hasEnoughStorage,
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan)
            ) {
                Text(
                    text = "Start Render",
                    color = ThrynxBgDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.action_cancel))
            }
        }
    )
}
