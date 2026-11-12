package com.thrynx.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.thrynx.app.core.model.CanvasAspectRatio
import com.thrynx.app.core.model.DownloadState
import com.thrynx.app.core.model.ProjectModel
import com.thrynx.app.core.model.TimelineTrackModel

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailUri: String,
    val durationMs: Long,
    val canvasRatio: CanvasAspectRatio,
    val resolutionWidth: Int,
    val resolutionHeight: Int,
    val fps: Int,
    val bitrateMbps: Int,
    val tracks: List<TimelineTrackModel>
) {
    fun toDomain(): ProjectModel = ProjectModel(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        thumbnailUri = thumbnailUri,
        durationMs = durationMs,
        canvasRatio = canvasRatio,
        resolutionWidth = resolutionWidth,
        resolutionHeight = resolutionHeight,
        fps = fps,
        bitrateMbps = bitrateMbps,
        tracks = tracks
    )

    companion object {
        fun fromDomain(model: ProjectModel): ProjectEntity = ProjectEntity(
            id = model.id,
            name = model.name,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
            thumbnailUri = model.thumbnailUri,
            durationMs = model.durationMs,
            canvasRatio = model.canvasRatio,
            resolutionWidth = model.resolutionWidth,
            resolutionHeight = model.resolutionHeight,
            fps = model.fps,
            bitrateMbps = model.bitrateMbps,
            tracks = model.tracks
        )
    }
}

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val durationMs: Long,
    val lastPositionMs: Long,
    val isVideo: Boolean,
    val lastWatchedTimestamp: Long,
    val isCompleted: Boolean = false
)

@Entity(tableName = "favorite_media")
data class FavoriteMediaEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val isVideo: Boolean,
    val addedTimestamp: Long
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String,
    val destinationPath: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState,
    val mimeType: String,
    val timestamp: Long,
    val errorMessage: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isVideoPlaylist: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaUri: String,
    val title: String,
    val durationMs: Long,
    val orderIndex: Int
)
