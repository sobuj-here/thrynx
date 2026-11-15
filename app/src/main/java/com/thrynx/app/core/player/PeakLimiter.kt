package com.thrynx.app.core.player

import kotlin.math.exp

/**
 * Peak limiter for the equalizer audio pipeline.
 *
 * Catches any audio peaks exceeding full scale headroom, preventing clipping
 * and distortion when boosting equalizer frequencies.
 */
class PeakLimiter {

    private var releaseCoef = 1f
    private var reduction = 1f

    fun configure(sampleRate: Int) {
        releaseCoef = if (sampleRate <= 0) {
            1f
        } else {
            (1.0 - exp(-1.0 / (RELEASE_SECONDS * sampleRate))).toFloat()
        }
        reduction = 1f
    }

    /** Reset state for seeks or track changes. */
    fun reset() {
        reduction = 1f
    }

    /** Computes gain multiplier for the current audio frame. */
    fun gainFor(peak: Float): Float {
        val wanted = if (peak > CEILING) CEILING / peak else 1f
        reduction = if (wanted < reduction) wanted else reduction + (wanted - reduction) * releaseCoef
        return reduction
    }

    companion object {
        const val CEILING = 0.985f
        private const val RELEASE_SECONDS = 0.1
    }
}
