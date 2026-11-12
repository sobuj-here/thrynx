package com.thrynx.app.core.editor.keyframe

import com.thrynx.app.core.model.ClipTransform
import com.thrynx.app.core.model.EasingType
import com.thrynx.app.core.model.KeyframeModel
import kotlin.math.cos
import kotlin.math.sin

object KeyframeInterpolator {

    fun interpolateTransform(
        keyframes: List<KeyframeModel>,
        defaultTransform: ClipTransform,
        timestampMs: Long
    ): ClipTransform {
        if (keyframes.isEmpty()) return defaultTransform
        if (keyframes.size == 1) {
            val k = keyframes[0]
            return ClipTransform(
                translationX = k.translationX,
                translationY = k.translationY,
                scaleX = k.scaleX,
                scaleY = k.scaleY,
                rotation = k.rotation,
                opacity = k.opacity
            )
        }

        val sorted = keyframes.sortedBy { it.timestampMs }
        if (timestampMs <= sorted.first().timestampMs) {
            val k = sorted.first()
            return ClipTransform(k.translationX, k.translationY, k.scaleX, k.scaleY, k.rotation, k.opacity)
        }
        if (timestampMs >= sorted.last().timestampMs) {
            val k = sorted.last()
            return ClipTransform(k.translationX, k.translationY, k.scaleX, k.scaleY, k.rotation, k.opacity)
        }

        // Find boundary keyframes
        var startKf = sorted.first()
        var endKf = sorted.last()
        for (i in 0 until sorted.size - 1) {
            if (timestampMs >= sorted[i].timestampMs && timestampMs <= sorted[i + 1].timestampMs) {
                startKf = sorted[i]
                endKf = sorted[i + 1]
                break
            }
        }

        val range = (endKf.timestampMs - startKf.timestampMs).toFloat().coerceAtLeast(1f)
        val linearT = ((timestampMs - startKf.timestampMs).toFloat() / range).coerceIn(0f, 1f)
        val t = applyEasing(linearT, endKf.easing)

        return ClipTransform(
            translationX = lerp(startKf.translationX, endKf.translationX, t),
            translationY = lerp(startKf.translationY, endKf.translationY, t),
            scaleX = lerp(startKf.scaleX, endKf.scaleX, t),
            scaleY = lerp(startKf.scaleY, endKf.scaleY, t),
            rotation = lerp(startKf.rotation, endKf.rotation, t),
            opacity = lerp(startKf.opacity, endKf.opacity, t)
        )
    }

    fun interpolateVolume(
        keyframes: List<KeyframeModel>,
        defaultVolume: Float,
        timestampMs: Long
    ): Float {
        if (keyframes.isEmpty()) return defaultVolume
        val sorted = keyframes.sortedBy { it.timestampMs }
        if (timestampMs <= sorted.first().timestampMs) return sorted.first().volume
        if (timestampMs >= sorted.last().timestampMs) return sorted.last().volume

        for (i in 0 until sorted.size - 1) {
            if (timestampMs >= sorted[i].timestampMs && timestampMs <= sorted[i + 1].timestampMs) {
                val start = sorted[i]
                val end = sorted[i + 1]
                val range = (end.timestampMs - start.timestampMs).toFloat().coerceAtLeast(1f)
                val t = applyEasing(((timestampMs - start.timestampMs).toFloat() / range).coerceIn(0f, 1f), end.easing)
                return lerp(start.volume, end.volume, t)
            }
        }
        return defaultVolume
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + fraction * (end - start)
    }

    private fun applyEasing(t: Float, easing: EasingType): Float {
        return when (easing) {
            EasingType.LINEAR -> t
            EasingType.EASE_IN -> t * t
            EasingType.EASE_OUT -> t * (2f - t)
            EasingType.EASE_IN_OUT -> if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
        }
    }
}
