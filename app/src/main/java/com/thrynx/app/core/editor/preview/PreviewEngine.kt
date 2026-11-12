package com.thrynx.app.core.editor.preview

import com.thrynx.app.core.editor.composition.CompositionEngine
import com.thrynx.app.core.editor.composition.CompositionSnapshot
import com.thrynx.app.core.model.ProjectModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreviewEngine {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _snapshot = MutableStateFlow<CompositionSnapshot?>(null)
    val snapshot: StateFlow<CompositionSnapshot?> = _snapshot.asStateFlow()

    private var activeProject: ProjectModel? = null

    fun setProject(project: ProjectModel) {
        activeProject = project
        seekTo(_playheadMs.value.coerceAtMost(project.durationMs))
    }

    fun play() {
        val proj = activeProject ?: return
        if (proj.durationMs <= 0L) return

        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = scope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                val now = System.currentTimeMillis()
                val delta = now - lastTime
                lastTime = now

                val nextPos = _playheadMs.value + delta
                if (nextPos >= proj.durationMs) {
                    _playheadMs.value = 0L
                    updateSnapshot(0L)
                    pause()
                    break
                } else {
                    _playheadMs.value = nextPos
                    updateSnapshot(nextPos)
                }
                delay(16) // ~60fps evaluation loop
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val proj = activeProject ?: return
        val clamped = positionMs.coerceIn(0L, proj.durationMs.coerceAtLeast(0L))
        _playheadMs.value = clamped
        updateSnapshot(clamped)
    }

    private fun updateSnapshot(positionMs: Long) {
        val proj = activeProject ?: return
        _snapshot.value = CompositionEngine.evaluate(proj, positionMs)
    }

    fun release() {
        pause()
        scope.cancel()
    }
}
