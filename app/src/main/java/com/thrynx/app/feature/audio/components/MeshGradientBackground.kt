package com.thrynx.app.feature.audio.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val DefaultMeshPalette = listOf(
    Color(0xFF00E5FF).copy(alpha = 0.35f), // Cyan
    Color(0xFF7C4DFF).copy(alpha = 0.30f), // Purple
    Color(0xFF00B0FF).copy(alpha = 0.25f), // Blue
    Color(0xFFFF4081).copy(alpha = 0.20f)  // Pink/Accent
)

/**
 * Apple Music style Now Playing backdrop with soft, organic luminous blobs
 * drifting and blurred together to create an ambient glowing background.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = DefaultMeshPalette
) {
    val angleAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        angleAnim.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        ) {
            val width = size.width
            val height = size.height
            val angleRad = Math.toRadians(angleAnim.value.toDouble()).toFloat()

            val c1 = colors.getOrElse(0) { Color(0xFF00E5FF).copy(alpha = 0.3f) }
            val c2 = colors.getOrElse(1) { Color(0xFF7C4DFF).copy(alpha = 0.3f) }
            val c3 = colors.getOrElse(2) { Color(0xFF00B0FF).copy(alpha = 0.25f) }
            val c4 = colors.getOrElse(3) { Color(0xFFFF4081).copy(alpha = 0.2f) }

            // Blob 1: Top-Left orbit
            val x1 = width * 0.3f + cos(angleRad) * (width * 0.15f)
            val y1 = height * 0.3f + sin(angleRad) * (height * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c1, Color.Transparent),
                    center = Offset(x1, y1),
                    radius = width * 0.6f
                ),
                radius = width * 0.6f,
                center = Offset(x1, y1)
            )

            // Blob 2: Top-Right counter orbit
            val x2 = width * 0.7f - sin(angleRad) * (width * 0.15f)
            val y2 = height * 0.35f + cos(angleRad) * (height * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c2, Color.Transparent),
                    center = Offset(x2, y2),
                    radius = width * 0.65f
                ),
                radius = width * 0.65f,
                center = Offset(x2, y2)
            )

            // Blob 3: Center-Bottom orbit
            val x3 = width * 0.5f + cos(angleRad * 0.8f) * (width * 0.12f)
            val y3 = height * 0.65f - sin(angleRad * 0.8f) * (height * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c3, Color.Transparent),
                    center = Offset(x3, y3),
                    radius = width * 0.7f
                ),
                radius = width * 0.7f,
                center = Offset(x3, y3)
            )

            // Blob 4: Bottom accent
            val x4 = width * 0.4f + sin(angleRad * 1.2f) * (width * 0.15f)
            val y4 = height * 0.85f + cos(angleRad * 1.2f) * (height * 0.1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c4, Color.Transparent),
                    center = Offset(x4, y4),
                    radius = width * 0.5f
                ),
                radius = width * 0.5f,
                center = Offset(x4, y4)
            )
        }

        // Deep vignette gradient overlay for contrast and readable typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
    }
}
