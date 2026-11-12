package com.thrynx.app.core.editor.effects

import androidx.compose.ui.graphics.ColorMatrix
import com.thrynx.app.core.model.EffectModel
import com.thrynx.app.core.model.EffectType

object EffectsEngine {

    fun buildColorMatrix(effects: List<EffectModel>): ColorMatrix {
        val finalMatrix = ColorMatrix()
        if (effects.isEmpty()) return finalMatrix

        for (effect in effects) {
            val intensity = effect.intensity

            when (effect.type) {
                EffectType.BRIGHTNESS -> {
                    val v = (intensity - 0.5f) * 255f
                    val cm = ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, v,
                        0f, 1f, 0f, 0f, v,
                        0f, 0f, 1f, 0f, v,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    finalMatrix.timesAssign(cm)
                }
                EffectType.CONTRAST -> {
                    val scale = (intensity * 2f).coerceIn(0.1f, 3.0f)
                    val translate = (-0.5f * scale + 0.5f) * 255f
                    val cm = ColorMatrix(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    finalMatrix.timesAssign(cm)
                }
                EffectType.SATURATION -> {
                    val sat = intensity * 2f
                    val cm = ColorMatrix()
                    cm.setToSaturation(sat)
                    finalMatrix.timesAssign(cm)
                }
                EffectType.GRAYSCALE -> {
                    val sat = 1f - intensity
                    val cm = ColorMatrix()
                    cm.setToSaturation(sat)
                    finalMatrix.timesAssign(cm)
                }
                EffectType.TEMPERATURE -> {
                    val shift = (intensity - 0.5f) * 60f
                    val cm = ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, shift,
                        0f, 1f, 0f, 0f, shift * 0.4f,
                        0f, 0f, 1f, 0f, -shift,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    finalMatrix.timesAssign(cm)
                }
                EffectType.VINTAGE -> {
                    val cm = ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 0.95f, 0f, 0f, 0f,
                        0f, 0f, 0.82f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    finalMatrix.timesAssign(cm)
                }
                EffectType.CINEMATIC_TEAL -> {
                    val cm = ColorMatrix(floatArrayOf(
                        1.15f, 0f, 0f, 0f, 10f,
                        0f, 1.05f, 0f, 0f, 5f,
                        0f, 0f, 1.25f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    finalMatrix.timesAssign(cm)
                }
                EffectType.BLUR, EffectType.VIGNETTE -> {}
            }
        }
        return finalMatrix
    }

    fun getOpenGLFragmentShader(effects: List<EffectModel>): String {
        return """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            
            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                
                // Brightness
                color.rgb += uBrightness;
                
                // Contrast
                color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
                
                // Saturation
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                color.rgb = mix(vec3(gray), color.rgb, uSaturation);
                
                gl_FragColor = color;
            }
        """.trimIndent()
    }
}
