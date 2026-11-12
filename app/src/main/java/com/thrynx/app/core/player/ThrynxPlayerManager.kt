package com.thrynx.app.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.thrynx.app.core.model.AudioTrackInfo
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.PlaybackState
import com.thrynx.app.core.model.SubtitleStyle
import com.thrynx.app.core.model.SubtitleTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
                    15_000, // min buffer
                    50_000, // max buffer
                    1_500,  // buffer for playback
                    3_000   // buffer after rebuffer
                )
                .build()
        )
        .build()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackerJob: Job? = null

    private var currentMediaModel: MediaItemModel? = null

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
                if (state == Player.STATE_READY) {
                    _playbackState.value = _playbackState.value.copy(
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        bufferedPositionMs = exoPlayer.bufferedPosition
                    )
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractAvailableTracks(tracks)
            }
        })
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

    fun playMedia(media: MediaItemModel, startPositionMs: Long = 0L) {
        currentMediaModel = media
        val mediaItem = MediaItem.Builder()
            .setUri(media.uri)
            .setMediaId(media.id.toString())
            .build()

        exoPlayer.setMediaItem(mediaItem, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.play()

        _playbackState.value = _playbackState.value.copy(
            currentMedia = media,
            currentPositionMs = startPositionMs,
            isPlaying = true
        )
    }

    fun playQueue(mediaList: List<MediaItemModel>, startIndex: Int = 0) {
        if (mediaList.isEmpty()) return
        exoPlayer.clearMediaItems()
        val mediaItems = mediaList.map { media ->
            MediaItem.Builder()
                .setUri(media.uri)
                .setMediaId(media.id.toString())
                .build()
        }
        exoPlayer.setMediaItems(mediaItems, startIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()

        currentMediaModel = mediaList.getOrNull(startIndex)
        _playbackState.value = _playbackState.value.copy(
            currentMedia = currentMediaModel,
            isPlaying = true
        )
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
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

    fun setAudioOnlyMode(enabled: Boolean) {
        _playbackState.value = _playbackState.value.copy(isAudioOnly = enabled)
    }

    fun setControlsLocked(locked: Boolean) {
        _playbackState.value = _playbackState.value.copy(isControlsLocked = locked)
    }

    fun toggleRepeatMode() {
        val nextMode = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = nextMode
        _playbackState.value = _playbackState.value.copy(isRepeatOne = nextMode == Player.REPEAT_MODE_ONE)
    }

    fun toggleShuffle() {
        val nextShuffle = !exoPlayer.shuffleModeEnabled
        exoPlayer.shuffleModeEnabled = nextShuffle
        _playbackState.value = _playbackState.value.copy(isShuffle = nextShuffle)
    }

    // Audio Track Detection and Selection
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
        val current = currentMediaModel ?: return
        val currentPosition = exoPlayer.currentPosition
        val isPlaying = exoPlayer.isPlaying

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(fileUri)
            .setMimeType(mimeType)
            .setLanguage("ext")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(current.uri)
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        exoPlayer.setMediaItem(mediaItem, currentPosition)
        exoPlayer.prepare()
        if (isPlaying) exoPlayer.play()
    }

    fun updateSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle.value = style
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
    }
}
