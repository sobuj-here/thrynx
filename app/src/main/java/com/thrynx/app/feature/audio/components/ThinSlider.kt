package com.thrynx.app.feature.audio.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple Music style scrubber: a sleek hairline capsule track with no thumb knob.
 * Expands smoothly under touch and returns to hairline on release.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    idleHeight: Dp = 6.dp,
    activeHeight: Dp = 10.dp,
    activeColor: Color = Color.White.copy(alpha = 0.95f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f)
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val animatedHeight by animateDpAsState(
        targetValue = if (isDragging) activeHeight else idleHeight,
        animationSpec = tween(durationMillis = 150),
        label = "ThinSliderHeight"
    )

    val currentFraction = if (isDragging) dragFraction else value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture

                    isDragging = true
                    val downFraction = (down.position.x / width).coerceIn(0f, 1f)
                    dragFraction = downFraction
                    currentOnValueChange(downFraction)

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        if (change.positionChanged()) {
                            change.consume()
                            val frac = (change.position.x / width).coerceIn(0f, 1f)
                            dragFraction = frac
                            currentOnValueChange(frac)
                        }
                    }

                    isDragging = false
                    currentOnFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
        ) {
            val trackHeight = size.height
            val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            // Inactive (background) track
            drawRoundRect(
                color = inactiveColor,
                size = Size(size.width, trackHeight),
                cornerRadius = trackRadius
            )

            // Active (played) track
            val activeWidth = (size.width * currentFraction).coerceIn(0f, size.width)
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = trackRadius
                )
            }
        }
    }
}
