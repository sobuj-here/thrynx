package com.thrynx.app.core.player

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class FilterKind { BELL, LOW_SHELF, HIGH_SHELF }

class FilterSlot(val kind: FilterKind, val frequencyHz: Float)

object EqLayout {
    /** The 7 standard center frequencies for graphical equalizer sliders. */
    val MANUAL_BANDS_HZ = floatArrayOf(60f, 150f, 400f, 1_000f, 2_500f, 6_000f, 14_000f)

    const val MANUAL_Q = 1.0f
    const val MANUAL_FIRST = 0
    const val MANUAL_COUNT = 7

    const val TONE_LOW = 7
    const val TONE_MID = 8
    const val TONE_HIGH = 9

    const val SLOTS = 10

    val slots: List<FilterSlot> = buildList {
        MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
            val kind = when (index) {
                0 -> FilterKind.LOW_SHELF
                MANUAL_BANDS_HZ.lastIndex -> FilterKind.HIGH_SHELF
                else -> FilterKind.BELL
            }
            add(FilterSlot(kind, hz))
        }
        add(FilterSlot(FilterKind.LOW_SHELF, 250f))
        add(FilterSlot(FilterKind.BELL, 1_000f))
        add(FilterSlot(FilterKind.HIGH_SHELF, 4_000f))
    }

    const val MANUAL_RANGE_DB = 12f
    const val TONE_STEPS = 5
    const val TONE_DB_PER_STEP = 1.2f
    val TONE_TILT_HZ = floatArrayOf(250f, 4_000f)
}

class EqCurve(
    val gainsDb: FloatArray,
    val qs: FloatArray,
    val preampDb: Float = calculatePreampDb(gainsDb)
) {
    companion object {
        val FLAT = EqCurve(
            gainsDb = FloatArray(EqLayout.SLOTS) { 0f },
            qs = FloatArray(EqLayout.SLOTS) { EqLayout.MANUAL_Q },
            preampDb = 0f
        )

        private fun calculatePreampDb(gains: FloatArray): Float {
            val maxBoost = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            return -maxBoost * 0.5f
        }

        fun fromManualBands(bandsDb: List<Float>): EqCurve {
            val gains = FloatArray(EqLayout.SLOTS)
            val qs = FloatArray(EqLayout.SLOTS) { EqLayout.MANUAL_Q }
            for (i in 0 until minOf(bandsDb.size, EqLayout.MANUAL_COUNT)) {
                gains[i] = bandsDb[i].coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
            }
            return EqCurve(gains, qs)
        }

        fun fromTone(toneX: Float, toneY: Float): EqCurve {
            val gains = FloatArray(EqLayout.SLOTS)
            val qs = FloatArray(EqLayout.SLOTS) { EqLayout.MANUAL_Q }

            // ToneX tilts bass (-) vs treble (+)
            val bassTilt = -toneX * EqLayout.TONE_DB_PER_STEP * EqLayout.TONE_STEPS
            val trebleTilt = toneX * EqLayout.TONE_DB_PER_STEP * EqLayout.TONE_STEPS

            // ToneY controls midrange body
            val midGain = toneY * EqLayout.TONE_DB_PER_STEP * EqLayout.TONE_STEPS

            gains[EqLayout.TONE_LOW] = bassTilt.coerceIn(-12f, 12f)
            gains[EqLayout.TONE_MID] = midGain.coerceIn(-12f, 12f)
            gains[EqLayout.TONE_HIGH] = trebleTilt.coerceIn(-12f, 12f)

            return EqCurve(gains, qs)
        }
    }
}
