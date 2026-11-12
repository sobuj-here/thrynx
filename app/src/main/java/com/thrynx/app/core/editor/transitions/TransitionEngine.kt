package com.thrynx.app.core.editor.transitions

import com.thrynx.app.core.model.TransitionType

data class TransitionFrameState(
    val outgoingAlpha: Float = 1f,
    val incomingAlpha: Float = 0f,
    val outgoingTranslationX: Float = 0f,
    val incomingTranslationX: Float = 0f,
    val outgoingScale: Float = 1f,
    val incomingScale: Float = 1f
)

object TransitionEngine {

    fun calculateTransition(
        type: TransitionType,
        progress: Float // 0.0f (start of transition) to 1.0f (end of transition)
    ): TransitionFrameState {
        val t = progress.coerceIn(0f, 1f)

        return when (type) {
            TransitionType.NONE -> TransitionFrameState(
                outgoingAlpha = if (t < 0.5f) 1f else 0f,
                incomingAlpha = if (t >= 0.5f) 1f else 0f
            )
            TransitionType.DISSOLVE -> TransitionFrameState(
                outgoingAlpha = 1f - t,
                incomingAlpha = t
            )
            TransitionType.FADE -> {
                // Fade out to black in first half, fade in from black in second half
                val outgoingA = if (t < 0.5f) 1f - (t * 2f) else 0f
                val incomingA = if (t >= 0.5f) (t - 0.5f) * 2f else 0f
                TransitionFrameState(outgoingAlpha = outgoingA, incomingAlpha = incomingA)
            }
            TransitionType.SLIDE_LEFT -> TransitionFrameState(
                outgoingAlpha = 1f,
                incomingAlpha = 1f,
                outgoingTranslationX = -t,
                incomingTranslationX = 1f - t
            )
            TransitionType.SLIDE_RIGHT -> TransitionFrameState(
                outgoingAlpha = 1f,
                incomingAlpha = 1f,
                outgoingTranslationX = t,
                incomingTranslationX = -(1f - t)
            )
            TransitionType.ZOOM_IN -> TransitionFrameState(
                outgoingAlpha = 1f - t,
                incomingAlpha = t,
                outgoingScale = 1f + (t * 0.5f),
                incomingScale = 0.5f + (t * 0.5f)
            )
            TransitionType.WIPE_LEFT, TransitionType.BLUR_PUSH -> TransitionFrameState(
                outgoingAlpha = (1f - t),
                incomingAlpha = t,
                outgoingTranslationX = -t * 0.5f,
                incomingTranslationX = (1f - t) * 0.5f
            )
        }
    }
}
