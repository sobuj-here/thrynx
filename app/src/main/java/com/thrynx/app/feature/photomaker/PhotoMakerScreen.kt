package com.thrynx.app.feature.photomaker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.thrynx.app.core.model.*
import java.util.UUID

@Composable
fun PhotoMakerScreen(
    availablePhotos: List<MediaItemModel>,
    onCreateVideoProject: (ProjectModel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPhotos by remember { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var slideDurationSec by remember { mutableFloatStateOf(3.0f) }
    var kenBurnsEnabled by remember { mutableStateOf(true) }
    var selectedRatio by remember { mutableStateOf(CanvasAspectRatio.RATIO_16_9) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThrynxHeader(
            title = stringResource(id = R.string.photo_maker_title),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Slides Carousel Selection
                Text(
                    text = "${stringResource(id = R.string.photo_select_images)} (${selectedPhotos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(availablePhotos.take(20)) { index, photo ->
                        val isSelected = selectedPhotos.any { it.id == photo.id }
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) ThrynxCyan else ThrynxCardDark)
                                .clickable {
                                    selectedPhotos = if (isSelected) {
                                        selectedPhotos.filter { it.id != photo.id }
                                    } else {
                                        selectedPhotos + photo
                                    }
                                }
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ThrynxSurfaceDark),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = if (isSelected) ThrynxCyan else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Photo #${index + 1}",
                                    fontSize = 10.sp,
                                    color = if (isSelected) ThrynxCyan else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Slide Duration Controls
                Text(
                    text = stringResource(id = R.string.photo_slide_duration, String.format("%.1f", slideDurationSec)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = slideDurationSec,
                    onValueChange = { slideDurationSec = it },
                    valueRange = 1.0f..8.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(thumbColor = ThrynxCyan, activeTrackColor = ThrynxCyan)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Ken Burns Effect Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.photo_ken_burns),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Smooth cinematic pan and zoom on images",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = kenBurnsEnabled,
                        onCheckedChange = { kenBurnsEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ThrynxCyan)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Aspect Ratio Selector
                Text(
                    text = stringResource(id = R.string.editor_canvas_ratio),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CanvasAspectRatio.RATIO_16_9,
                        CanvasAspectRatio.RATIO_9_16,
                        CanvasAspectRatio.RATIO_1_1,
                        CanvasAspectRatio.RATIO_4_5
                    ).forEach { ratio ->
                        ThrynxPill(
                            label = ratio.label.take(4),
                            isSelected = selectedRatio == ratio,
                            onClick = { selectedRatio = ratio }
                        )
                    }
                }
            }

            // Generate Video Project Button
            Button(
                onClick = {
                    if (selectedPhotos.size >= 2) {
                        val durationPerSlideMs = (slideDurationSec * 1000).toLong()
                        var cursor = 0L

                        val clips = selectedPhotos.mapIndexed { i, photo ->
                            val clip = TimelineClipModel(
                                id = UUID.randomUUID().toString(),
                                mediaUri = photo.uri.toString(),
                                name = photo.title,
                                timelineStartMs = cursor,
                                durationMs = durationPerSlideMs,
                                sourceStartMs = 0L,
                                sourceEndMs = durationPerSlideMs,
                                transitionIn = if (i > 0) TransitionModel(type = TransitionType.DISSOLVE, durationMs = 600L) else null,
                                transform = if (kenBurnsEnabled) {
                                    ClipTransform(scaleX = 1.0f, scaleY = 1.0f)
                                } else {
                                    ClipTransform()
                                },
                                keyframes = if (kenBurnsEnabled) {
                                    listOf(
                                        KeyframeModel(timestampMs = 0L, scaleX = 1.0f, scaleY = 1.0f),
                                        KeyframeModel(timestampMs = durationPerSlideMs, scaleX = 1.15f, scaleY = 1.15f)
                                    )
                                } else emptyList()
                            )
                            cursor += durationPerSlideMs
                            clip
                        }

                        val project = ProjectModel(
                            name = "Slideshow_${System.currentTimeMillis() / 1000}",
                            canvasRatio = selectedRatio,
                            durationMs = cursor,
                            tracks = listOf(
                                TimelineTrackModel(id = "track_main_video", type = TrackType.VIDEO, index = 0, clips = clips),
                                TimelineTrackModel(id = "track_bgm", type = TrackType.AUDIO, index = 1),
                                TimelineTrackModel(id = "track_text", type = TrackType.TEXT, index = 2)
                            )
                        )
                        onCreateVideoProject(project)
                    }
                },
                enabled = selectedPhotos.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ThrynxCyan)
            ) {
                Icon(Icons.Default.MovieCreation, contentDescription = null, tint = ThrynxBgDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.photo_generate_video),
                    color = ThrynxBgDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
