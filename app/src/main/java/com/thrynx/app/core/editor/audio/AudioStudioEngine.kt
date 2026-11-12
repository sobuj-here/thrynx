package com.thrynx.app.core.editor.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class AudioStudioEngine(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false

    fun startVoiceRecording(outputFile: File): Boolean {
        try {
            currentRecordingFile = outputFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            return false
        }
    }

    fun stopVoiceRecording(): File? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            currentRecordingFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateWaveform(audioFile: File, sampleCount: Int = 100): List<Float> = withContext(Dispatchers.IO) {
        val amplitudes = mutableListOf<Float>()
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext List(sampleCount) { 0.1f }
        }

        try {
            FileInputStream(audioFile).use { fis ->
                val fileSize = audioFile.length()
                val step = (fileSize / sampleCount).coerceAtLeast(1L)
                val buffer = ByteArray(256)

                for (i in 0 until sampleCount) {
                    fis.channel.position(i * step)
                    val read = fis.read(buffer)
                    if (read > 0) {
                        var maxSample = 0
                        for (b in buffer) {
                            val v = Math.abs(b.toInt())
                            if (v > maxSample) maxSample = v
                        }
                        val normalized = (maxSample / 128f).coerceIn(0.05f, 1.0f)
                        amplitudes.add(normalized)
                    } else {
                        amplitudes.add(0.05f)
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext List(sampleCount) { (Math.sin(it.toDouble() * 0.3).toFloat().coerceIn(0.1f, 1f)) }
        }

        if (amplitudes.size < sampleCount) {
            val pad = sampleCount - amplitudes.size
            repeat(pad) { amplitudes.add(0.05f) }
        }
        amplitudes
    }

    fun calculateVolumeWithFade(
        baseVolume: Float,
        clipTimeMs: Long,
        clipDurationMs: Long,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Float {
        var vol = baseVolume
        if (fadeInMs > 0 && clipTimeMs < fadeInMs) {
            vol *= (clipTimeMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
        }
        if (fadeOutMs > 0 && clipTimeMs > (clipDurationMs - fadeOutMs)) {
            val remain = (clipDurationMs - clipTimeMs).coerceAtLeast(0L)
            vol *= (remain.toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f)
        }
        return vol
    }
}
