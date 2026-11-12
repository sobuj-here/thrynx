package com.thrynx.app.core.editor.timeline

import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.model.TimelineClipModel
import com.thrynx.app.core.model.TimelineTrackModel
import com.thrynx.app.core.model.TrackType
import java.util.Stack
import java.util.UUID

class TimelineEngine(initialProject: ProjectModel) {

    private var currentProject: ProjectModel = initialProject
    private val undoStack = Stack<ProjectModel>()
    private val redoStack = Stack<ProjectModel>()

    fun getProject(): ProjectModel = currentProject

    private fun pushState() {
        undoStack.push(currentProject.copy())
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): ProjectModel? {
        if (!canUndo()) return null
        redoStack.push(currentProject.copy())
        currentProject = undoStack.pop()
        return currentProject
    }

    fun redo(): ProjectModel? {
        if (!canRedo()) return null
        undoStack.push(currentProject.copy())
        currentProject = redoStack.pop()
        return currentProject
    }

    fun addClipToTrack(trackId: String, clip: TimelineClipModel): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                // Place clip at the end of this track's current sequence
                val lastClipEnd = track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
                val positionedClip = clip.copy(timelineStartMs = lastClipEnd)
                track.copy(clips = track.clips + positionedClip)
            } else {
                track
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun splitClipAtPlayhead(trackId: String, clipId: String, playheadMs: Long): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = mutableListOf<TimelineClipModel>()
                for (clip in track.clips) {
                    if (clip.id == clipId && playheadMs > clip.timelineStartMs && playheadMs < clip.timelineEndMs) {
                        val firstDuration = playheadMs - clip.timelineStartMs
                        val secondDuration = clip.timelineEndMs - playheadMs

                        val clip1 = clip.copy(
                            id = UUID.randomUUID().toString(),
                            durationMs = firstDuration,
                            sourceEndMs = clip.sourceStartMs + (firstDuration * clip.speedFactor).toLong()
                        )
                        val clip2 = clip.copy(
                            id = UUID.randomUUID().toString(),
                            timelineStartMs = playheadMs,
                            durationMs = secondDuration,
                            sourceStartMs = clip.sourceStartMs + (firstDuration * clip.speedFactor).toLong(),
                            sourceEndMs = clip.sourceEndMs
                        )
                        updatedClips.add(clip1)
                        updatedClips.add(clip2)
                    } else {
                        updatedClips.add(clip)
                    }
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun trimClip(trackId: String, clipId: String, newSourceStartMs: Long, newSourceEndMs: Long): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                var cursor = 0L
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val safeStart = newSourceStartMs.coerceAtLeast(0L)
                        val safeEnd = newSourceEndMs.coerceAtLeast(safeStart + 100L)
                        val newDuration = ((safeEnd - safeStart) / clip.speedFactor).toLong()
                        val modified = clip.copy(
                            sourceStartMs = safeStart,
                            sourceEndMs = safeEnd,
                            timelineStartMs = cursor,
                            durationMs = newDuration
                        )
                        cursor += newDuration
                        modified
                    } else {
                        val modified = clip.copy(timelineStartMs = cursor)
                        cursor += clip.durationMs
                        modified
                    }
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun deleteClip(trackId: String, clipId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                var cursor = 0L
                val remainingClips = track.clips.filter { it.id != clipId }.map { clip ->
                    val adjusted = clip.copy(timelineStartMs = cursor)
                    cursor += clip.durationMs
                    adjusted
                }
                track.copy(clips = remainingClips)
            } else {
                track
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun duplicateClip(trackId: String, clipId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val clipIndex = track.clips.indexOfFirst { it.id == clipId }
                if (clipIndex != -1) {
                    val original = track.clips[clipIndex]
                    val duplicate = original.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${original.name} (Copy)"
                    )
                    val newList = track.clips.toMutableList()
                    newList.add(clipIndex + 1, duplicate)

                    var cursor = 0L
                    val repositioned = newList.map { c ->
                        val r = c.copy(timelineStartMs = cursor)
                        cursor += c.durationMs
                        r
                    }
                    track.copy(clips = repositioned)
                } else {
                    track
                }
            } else {
                track
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun setClipSpeed(trackId: String, clipId: String, speed: Float): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                var cursor = 0L
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val newDuration = (((clip.sourceEndMs - clip.sourceStartMs) / speed)).toLong().coerceAtLeast(100L)
                        val adjusted = clip.copy(
                            speedFactor = speed,
                            timelineStartMs = cursor,
                            durationMs = newDuration
                        )
                        cursor += newDuration
                        adjusted
                    } else {
                        val adjusted = clip.copy(timelineStartMs = cursor)
                        cursor += clip.durationMs
                        adjusted
                    }
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun updateClipTransform(trackId: String, clipId: String, newTransform: com.thrynx.app.core.model.ClipTransform): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(transform = newTransform) else clip
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun addKeyframeToClip(trackId: String, clipId: String, keyframe: com.thrynx.app.core.model.KeyframeModel): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val existingWithoutCurrentTime = clip.keyframes.filter { Math.abs(it.timestampMs - keyframe.timestampMs) > 50L }
                        clip.copy(keyframes = (existingWithoutCurrentTime + keyframe).sortedBy { it.timestampMs })
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun updateClipVolume(trackId: String, clipId: String, volume: Float): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(volume = volume) else clip
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun toggleTrackMute(trackId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) track.copy(isMuted = !track.isMuted) else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun toggleTrackLock(trackId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) track.copy(isLocked = !track.isLocked) else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    private fun recalculateProjectDuration(project: ProjectModel): ProjectModel {
        val maxDuration = project.tracks.maxOfOrNull { track ->
            track.clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } ?: 0L
        return project.copy(durationMs = maxDuration)
    }
}
