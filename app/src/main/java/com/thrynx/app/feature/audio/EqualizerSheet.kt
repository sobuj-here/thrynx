package com.thrynx.app.feature.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrynx.app.core.designsystem.*
import com.thrynx.app.core.player.EqLayout
import com.thrynx.app.core.player.EqualizerPreset
import com.thrynx.app.core.player.ThrynxPlayerManager
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    playerManager: ThrynxPlayerManager,
    onDismiss: () -> Unit
) {
    val enabled by playerManager.equalizerEnabled.collectAsState()
    val currentPreset by playerManager.equalizerPreset.collectAsState()
    val bands by playerManager.equalizerBands.collectAsState()
    val toneX by playerManager.toneX.collectAsState()
    val toneY by playerManager.toneY.collectAsState()
    val audioBalance by playerManager.audioBalance.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Manual 7-Band, 1: Tone Pad

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ThrynxCardDark,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title & Master Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ThrynxCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = ThrynxCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Equalizer & DSP",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = ThrynxTextPrimaryDark
                            )
                        )
                        Text(
                            text = if (enabled) "10-Band Parametric Audio Filter Active" else "Equalizer Disabled (Flat)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (enabled) ThrynxCyan else ThrynxTextMutedDark
                            )
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { playerManager.setEqualizerEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ThrynxCyan,
                        uncheckedThumbColor = ThrynxTextMutedDark,
                        uncheckedTrackColor = ThrynxSurfaceDark
                    )
                )
            }

            HorizontalDivider(color = ThrynxCardStrokeDark, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Presets Carousel
            Text(
                text = "SOUND PRESETS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = ThrynxTextMutedDark,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }) { preset ->
                    val isSelected = currentPreset == preset
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) ThrynxCyan else ThrynxSurfaceDark,
                        modifier = Modifier.clickable(enabled = enabled) {
                            playerManager.setEqualizerPreset(preset)
                        }
                    ) {
                        Text(
                            text = preset.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                            color = if (isSelected) Color.Black else ThrynxTextPrimaryDark,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Selector: Manual vs Tone Pad
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ThrynxSurfaceDark,
                contentColor = ThrynxCyan,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("7-Band Graphic EQ") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Tone Pad (Bass/Treble)") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                // 7-Band Graphic EQ Sliders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EqLayout.MANUAL_BANDS_HZ.forEachIndexed { index, freqHz ->
                        val gainDb = bands.getOrElse(index) { 0f }
                        val freqLabel = if (freqHz >= 1000f) "${(freqHz / 1000f).toInt()}k" else "${freqHz.toInt()}"

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${if (gainDb > 0) "+" else ""}${gainDb.roundToInt()}dB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = if (gainDb != 0f) ThrynxCyan else ThrynxTextMutedDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Vertical Slider Emulation
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .width(28.dp)
                                    .pointerInput(enabled) {
                                        if (!enabled) return@pointerInput
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            val h = this.size.height.toFloat()
                                            val y = change.position.y.coerceIn(0f, h)
                                            val fraction = 1f - (y / h) // 0 at bottom (-12dB), 1 at top (+12dB)
                                            val newGain = (fraction * 24f - 12f)
                                            playerManager.setEqualizerBand(index, newGain)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Background track
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(ThrynxCardDark, CircleShape)
                                )

                                // Center 0dB indicator
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(1.dp)
                                        .background(ThrynxCardStrokeDark)
                                )

                                // Thumb knob
                                val thumbOffsetFraction = ((gainDb + 12f) / 24f).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = (thumbOffsetFraction * 140).dp)
                                            .size(18.dp)
                                            .background(if (enabled) ThrynxCyan else ThrynxTextMutedDark, CircleShape)
                                            .border(2.dp, Color.Black, CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ThrynxTextPrimaryDark
                                )
                            )
                        }
                    }
                }
            } else {
                // Tone Pad (Bass / Treble tilt and Body)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Drag the puck: Left = Warmer (Bass), Right = Brighter (Treble)",
                        style = MaterialTheme.typography.bodySmall.copy(color = ThrynxTextMutedDark),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ThrynxSurfaceDark)
                            .border(1.dp, ThrynxCardStrokeDark, RoundedCornerShape(16.dp))
                            .pointerInput(enabled) {
                                if (!enabled) return@pointerInput
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val boxWidth = this.size.width.toFloat()
                                    val boxHeight = this.size.height.toFloat()
                                    val nx = ((change.position.x / boxWidth) * 2f - 1f).coerceIn(-1f, 1f)
                                    val ny = (1f - (change.position.y / boxHeight) * 2f).coerceIn(-1f, 1f)
                                    playerManager.setTone(nx, ny)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Axis cross lines
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ThrynxCardStrokeDark)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(ThrynxCardStrokeDark)
                        )

                        // Draggable puck
                        val pxOffset = (toneX * 85).dp
                        val pyOffset = (-toneY * 85).dp

                        Box(
                            modifier = Modifier
                                .offset(x = pxOffset, y = pyOffset)
                                .size(28.dp)
                                .background(if (enabled) ThrynxCyan else ThrynxTextMutedDark, CircleShape)
                                .border(3.dp, Color.Black, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stereo Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stereo Balance",
                    style = MaterialTheme.typography.bodyMedium.copy(color = ThrynxTextPrimaryDark)
                )
                Text(
                    text = when {
                        audioBalance < -0.05f -> "Left ${(audioBalance * -100).toInt()}%"
                        audioBalance > 0.05f -> "Right ${(audioBalance * 100).toInt()}%"
                        else -> "Center"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = ThrynxCyan, fontWeight = FontWeight.Bold)
                )
            }

            Slider(
                value = audioBalance,
                onValueChange = { playerManager.setAudioBalance(it) },
                valueRange = -1f..1f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = ThrynxCyan,
                    activeTrackColor = ThrynxCyan,
                    inactiveTrackColor = ThrynxCardDark
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reset Button
            OutlinedButton(
                onClick = {
                    playerManager.setEqualizerPreset(EqualizerPreset.FLAT)
                    playerManager.setTone(0f, 0f)
                    playerManager.setAudioBalance(0f)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ThrynxCyan),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ThrynxCardStrokeDark))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Flat")
            }
        }
    }
}
