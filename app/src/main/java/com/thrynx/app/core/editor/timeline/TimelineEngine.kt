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

    fun toggleClipMute(trackId: String, clipId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(isMuted = !clip.isMuted) else clip
                }
                track.copy(clips = updatedClips)
            } else {
                track
            }
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun moveClip(trackId: String, clipId: String, newTimelineStartMs: Long, snapToleranceMs: Long = 150L): ProjectModel {
        pushState()
        var targetStart = newTimelineStartMs.coerceAtLeast(0L)

        // Snapping calculation: snap to 0 or nearby clip edges
        val track = currentProject.tracks.firstOrNull { it.id == trackId }
        if (track != null) {
            val otherClips = track.clips.filter { it.id != clipId }
            for (other in otherClips) {
                if (Math.abs(targetStart - other.timelineEndMs) <= snapToleranceMs) {
                    targetStart = other.timelineEndMs
                    break
                }
                if (Math.abs(targetStart - other.timelineStartMs) <= snapToleranceMs) {
                    targetStart = other.timelineStartMs
                    break
                }
            }
            if (Math.abs(targetStart) <= snapToleranceMs) {
                targetStart = 0L
            }
        }

        val updatedTracks = currentProject.tracks.map { t ->
            if (t.id == trackId) {
                val updatedClips = t.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(timelineStartMs = targetStart) else clip
                }.sortedBy { it.timelineStartMs }
                t.copy(clips = updatedClips)
            } else {
                t
            }
        }
        currentProject = recalculateProjectDuration(currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun moveClipBetweenTracks(fromTrackId: String, toTrackId: String, clipId: String, targetStartMs: Long): ProjectModel {
        pushState()
        var movedClip: TimelineClipModel? = null

        val tracksWithoutClip = currentProject.tracks.map { t ->
            if (t.id == fromTrackId) {
                val remaining = t.clips.filter {
                    if (it.id == clipId) {
                        movedClip = it
                        false
                    } else true
                }
                t.copy(clips = remaining)
            } else t
        }

        val finalTracks = if (movedClip != null) {
            val clipToInsert = movedClip!!.copy(timelineStartMs = targetStartMs.coerceAtLeast(0L))
            tracksWithoutClip.map { t ->
                if (t.id == toTrackId) {
                    t.copy(clips = (t.clips + clipToInsert).sortedBy { it.timelineStartMs })
                } else t
            }
        } else tracksWithoutClip

        currentProject = recalculateProjectDuration(currentProject.copy(tracks = finalTracks, updatedAt = System.currentTimeMillis()))
        return currentProject
    }

    fun rotateClip90(trackId: String, clipId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val newRot = (clip.transform.rotation + 90f) % 360f
                        clip.copy(transform = clip.transform.copy(rotation = newRot))
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun flipClipHorizontal(trackId: String, clipId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(transform = clip.transform.copy(scaleX = -clip.transform.scaleX))
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun flipClipVertical(trackId: String, clipId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(transform = clip.transform.copy(scaleY = -clip.transform.scaleY))
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun updateClipOpacity(trackId: String, clipId: String, opacity: Float): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(transform = clip.transform.copy(opacity = opacity.coerceIn(0f, 1f)))
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun updateTextProperties(
        trackId: String,
        clipId: String,
        text: String,
        colorHex: String,
        bgHex: String,
        fontSizeSp: Float,
        isSubtitle: Boolean
    ): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(
                            textContent = text,
                            name = text.take(15),
                            textColorHex = colorHex,
                            textBackgroundColorHex = bgHex,
                            textFontSizeSp = fontSizeSp,
                            isSubtitle = isSubtitle
                        )
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun updateAudioFade(trackId: String, clipId: String, fadeInMs: Long, fadeOutMs: Long): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(fadeInMs = fadeInMs.coerceAtLeast(0L), fadeOutMs = fadeOutMs.coerceAtLeast(0L))
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun applyEffectToClip(trackId: String, clipId: String, effect: com.thrynx.app.core.model.EffectModel): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        val withoutCurrentType = clip.effects.filter { it.type != effect.type }
                        clip.copy(effects = withoutCurrentType + effect)
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun removeEffectFromClip(trackId: String, clipId: String, effectId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(effects = clip.effects.filter { it.id != effectId })
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun removeKeyframeFromClip(trackId: String, clipId: String, keyframeId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(keyframes = clip.keyframes.filter { it.id != keyframeId })
                    } else clip
                }
                track.copy(clips = updatedClips)
            } else track
        }
        currentProject = currentProject.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        return currentProject
    }

    fun toggleTrackVisibility(trackId: String): ProjectModel {
        pushState()
        val updatedTracks = currentProject.tracks.map { track ->
            if (track.id == trackId) track.copy(isHidden = !track.isHidden) else track
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
