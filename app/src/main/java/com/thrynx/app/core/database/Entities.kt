package com.thrynx.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists video playback progress and history.
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val durationMs: Long,
    val lastPositionMs: Long,
    val isVideo: Boolean = true,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)

/**
 * Stores user's starred/favorite videos.
 */
@Entity(tableName = "favorite_media")
data class FavoriteMediaEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val isVideo: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Tracks hidden/private folders for the private vault.
 */
@Entity(tableName = "private_folders")
data class PrivateFolderEntity(
    @PrimaryKey val folderPath: String,
    val folderName: String,
    val isHidden: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Tracks individually hidden private videos.
 */
@Entity(tableName = "private_videos")
data class PrivateVideoEntity(
    @PrimaryKey val mediaUri: String,
    val originalPath: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Stores custom directories added by user via SAF folder picker.
 */
@Entity(tableName = "custom_folders")
data class CustomFolderEntity(
    @PrimaryKey val folderUri: String,
    val folderName: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)
