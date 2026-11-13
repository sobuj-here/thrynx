package com.thrynx.app.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.thrynx.app.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThrynxPlayerManager(private val context: Context) {

    private val trackSelector = DefaultTrackSelector(context)
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setRenderersFactory(
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000, // min buffer 15s
                    60_000, // max buffer 60s
                    1_500,  // buffer for playback 1.5s
                    3_000   // buffer after rebuffer 3s
                )
                .build()
        )
        .build()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var currentPlaylist: List<MediaItemModel> = emptyList()
    private var currentPlaylistIndex: Int = 0

    init {
        setupPlayerListeners()
    }

    private fun setupPlayerListeners() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        _playbackState.value = _playbackState.value.copy(
                            durationMs = exoPlayer.duration.coerceAtLeast(0L),
                            bufferedPositionMs = exoPlayer.bufferedPosition
                        )
                    }
                    Player.STATE_ENDED -> {
                        handlePlaybackEnded()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractAvailableTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Retry automatically if recoverable
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
                    val pos = exoPlayer.currentPosition
                    exoPlayer.prepare()
                    exoPlayer.seekTo(pos)
                    exoPlayer.play()
                }
            }
        })
    }

    private fun handlePlaybackEnded() {
        when (_playbackState.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                exoPlayer.play()
            }
            RepeatMode.ALL -> {
                if (currentPlaylist.isNotEmpty()) {
                    playNext()
                } else {
                    seekTo(0L)
                    exoPlayer.play()
                }
            }
            RepeatMode.OFF -> {
                if (currentPlaylistIndex < currentPlaylist.size - 1) {
                    playNext()
                } else {
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                }
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackerJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = exoPlayer.currentPosition,
                        bufferedPositionMs = exoPlayer.bufferedPosition
                    )
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun resolveMediaUri(media: MediaItemModel): Uri {
        return media.uri ?: Uri.parse(media.path.ifBlank { "content://video/${media.id}" })
    }

    fun playMedia(media: MediaItemModel, startPositionMs: Long = 0L) {
        currentPlaylist = listOf(media)
        currentPlaylistIndex = 0

        val mediaItem = MediaItem.Builder()
            .setUri(resolveMediaUri(media))
            .setMediaId(media.id.toString())
            .build()

        exoPlayer.setMediaItem(mediaItem, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.play()

        _playbackState.value = _playbackState.value.copy(
            currentMedia = media,
            currentPositionMs = startPositionMs,
            isPlaying = true,
            queue = currentPlaylist,
            queueIndex = 0
        )
    }

    fun playQueue(mediaList: List<MediaItemModel>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (mediaList.isEmpty()) return
        currentPlaylist = mediaList
        currentPlaylistIndex = startIndex.coerceIn(0, mediaList.size - 1)

        val target = currentPlaylist[currentPlaylistIndex]
        val mediaItems = mediaList.map { media ->
            MediaItem.Builder()
                .setUri(resolveMediaUri(media))
                .setMediaId(media.id.toString())
                .build()
        }

        exoPlayer.setMediaItems(mediaItems, currentPlaylistIndex, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.play()

        _playbackState.value = _playbackState.value.copy(
            currentMedia = target,
            currentPositionMs = startPositionMs,
            isPlaying = true,
            queue = currentPlaylist,
            queueIndex = currentPlaylistIndex
        )
    }

    fun playNext() {
        if (currentPlaylistIndex < currentPlaylist.size - 1) {
            currentPlaylistIndex++
            val nextMedia = currentPlaylist[currentPlaylistIndex]
            val mediaItem = MediaItem.Builder()
                .setUri(resolveMediaUri(nextMedia))
                .setMediaId(nextMedia.id.toString())
                .build()
            exoPlayer.setMediaItem(mediaItem, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
            _playbackState.value = _playbackState.value.copy(
                currentMedia = nextMedia,
                currentPositionMs = 0L,
                queueIndex = currentPlaylistIndex,
                isPlaying = true
            )
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && currentPlaylist.isNotEmpty()) {
            currentPlaylistIndex = 0
            val firstMedia = currentPlaylist[0]
            val mediaItem = MediaItem.Builder()
                .setUri(resolveMediaUri(firstMedia))
                .setMediaId(firstMedia.id.toString())
                .build()
            exoPlayer.setMediaItem(mediaItem, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
            _playbackState.value = _playbackState.value.copy(
                currentMedia = firstMedia,
                currentPositionMs = 0L,
                queueIndex = 0,
                isPlaying = true
            )
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            seekTo(0L)
            return
        }
        if (currentPlaylistIndex > 0) {
            currentPlaylistIndex--
            val prevMedia = currentPlaylist[currentPlaylistIndex]
            val mediaItem = MediaItem.Builder()
                .setUri(resolveMediaUri(prevMedia))
                .setMediaId(prevMedia.id.toString())
                .build()
            exoPlayer.setMediaItem(mediaItem, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
            _playbackState.value = _playbackState.value.copy(
                currentMedia = prevMedia,
                currentPositionMs = 0L,
                queueIndex = currentPlaylistIndex,
                isPlaying = true
            )
        } else {
            seekTo(0L)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val bounded = positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(bounded)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = bounded)
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtMost(exoPlayer.duration)
        seekTo(target)
    }

    fun seekBackward(deltaMs: Long = 10_000L) {
        val target = (exoPlayer.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun setControlsLocked(locked: Boolean) {
        _playbackState.value = _playbackState.value.copy(isControlsLocked = locked)
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _playbackState.value = _playbackState.value.copy(aspectRatioMode = mode)
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = nextMode)
    }

    fun toggleShuffle() {
        val nextShuffle = !_playbackState.value.isShuffle
        _playbackState.value = _playbackState.value.copy(isShuffle = nextShuffle)
        if (nextShuffle && currentPlaylist.size > 1) {
            val current = _playbackState.value.currentMedia
            val shuffled = currentPlaylist.toMutableList().apply {
                remove(current)
                shuffle()
                if (current != null) add(0, current)
            }
            currentPlaylist = shuffled
            currentPlaylistIndex = 0
            _playbackState.value = _playbackState.value.copy(queue = shuffled, queueIndex = 0)
        }
    }

    // Audio Track Detection and Real Track Switching
    private fun extractAvailableTracks(tracks: Tracks) {
        val audioTracks = mutableListOf<AudioTrackInfo>()
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()

        var selectedAudio: AudioTrackInfo? = null
        var selectedSubtitle: SubtitleTrackInfo? = null

        val groups = tracks.groups
        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            val trackType = group.type

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)

                when (trackType) {
                    C.TRACK_TYPE_AUDIO -> {
                        val track = AudioTrackInfo(
                            id = format.id ?: "${groupIndex}_$trackIndex",
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language ?: "und",
                            label = format.label ?: "",
                            mimeType = format.sampleMimeType ?: "audio/mp4a-latm",
                            channelCount = format.channelCount,
                            sampleRate = format.sampleRate,
                            isSelected = isSelected
                        )
                        audioTracks.add(track)
                        if (isSelected) selectedAudio = track
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val track = SubtitleTrackInfo(
                            id = format.id ?: "${groupIndex}_$trackIndex",
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language ?: "und",
                            label = format.label ?: "",
                            mimeType = format.sampleMimeType ?: MimeTypes.APPLICATION_SUBRIP,
                            isSelected = isSelected
                        )
                        subtitleTracks.add(track)
                        if (isSelected) selectedSubtitle = track
                    }
                }
            }
        }

        _playbackState.value = _playbackState.value.copy(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            selectedAudioTrack = selectedAudio,
            selectedSubtitleTrack = selectedSubtitle
        )
    }

    fun selectAudioTrack(track: AudioTrackInfo) {
        val tracks = exoPlayer.currentTracks
        val group = tracks.groups.getOrNull(track.groupIndex) ?: return
        val currentPosition = exoPlayer.currentPosition

        val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
        val parameters = trackSelector.parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(override)
            .build()
        trackSelector.parameters = parameters

        // Audio track switched without restarting playback or resetting position
        exoPlayer.seekTo(currentPosition)
        _playbackState.value = _playbackState.value.copy(selectedAudioTrack = track)
    }

    fun selectSubtitleTrack(track: SubtitleTrackInfo?) {
        val builder = trackSelector.parameters.buildUpon()
        if (track == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val tracks = exoPlayer.currentTracks
            val group = tracks.groups.getOrNull(track.groupIndex)
            if (group != null) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                builder.addOverride(override)
            }
        }
        trackSelector.parameters = builder.build()
        _playbackState.value = _playbackState.value.copy(selectedSubtitleTrack = track)
    }

    fun loadExternalSubtitle(fileUri: Uri, mimeType: String = MimeTypes.APPLICATION_SUBRIP) {
        val current = _playbackState.value.currentMedia ?: return
        val currentPosition = exoPlayer.currentPosition
        val isPlaying = exoPlayer.isPlaying

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(fileUri)
            .setMimeType(mimeType)
            .setLanguage("ext")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(resolveMediaUri(current))
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        exoPlayer.setMediaItem(mediaItem, currentPosition)
        exoPlayer.prepare()
        if (isPlaying) exoPlayer.play()
    }

    fun setSubtitleDelay(delayMs: Long) {
        _playbackState.value = _playbackState.value.copy(subtitleDelayMs = delayMs)
    }

    fun updateSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle.value = style
    }

    // Sleep Timer Support
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        var remainingSeconds = minutes * 60
        _playbackState.value = _playbackState.value.copy(
            sleepTimerMinutes = minutes,
            sleepTimerRemainingSeconds = remainingSeconds
        )

        sleepTimerJob = scope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                _playbackState.value = _playbackState.value.copy(
                    sleepTimerRemainingSeconds = remainingSeconds
                )
            }
            if (remainingSeconds <= 0) {
                exoPlayer.pause()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    sleepTimerMinutes = 0,
                    sleepTimerRemainingSeconds = 0
                )
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playbackState.value = _playbackState.value.copy(
            sleepTimerMinutes = 0,
            sleepTimerRemainingSeconds = 0
        )
    }

    fun release() {
        stopProgressTracker()
        cancelSleepTimer()
        exoPlayer.release()
    }
}
