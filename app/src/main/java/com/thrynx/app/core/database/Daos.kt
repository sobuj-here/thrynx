package com.thrynx.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history WHERE isVideo = 1 ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 30): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE uri = :uri LIMIT 1")
    suspend fun getHistoryForUri(uri: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE uri = :uri")
    suspend fun deleteHistory(uri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_media WHERE isVideo = 1 ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteMediaEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_media WHERE uri = :uri)")
    fun isFavoriteFlow(uri: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteMediaEntity)

    @Query("DELETE FROM favorite_media WHERE uri = :uri")
    suspend fun removeFavorite(uri: String)
}

@Dao
interface PrivateVaultDao {
    @Query("SELECT * FROM private_folders ORDER BY addedTimestamp DESC")
    fun getAllPrivateFoldersFlow(): Flow<List<PrivateFolderEntity>>

    @Query("SELECT * FROM private_videos ORDER BY addedTimestamp DESC")
    fun getAllPrivateVideosFlow(): Flow<List<PrivateVideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM private_folders WHERE folderPath = :path)")
    suspend fun isFolderPrivate(path: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM private_videos WHERE mediaUri = :uri)")
    suspend fun isMediaPrivate(uri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPrivateFolder(folder: PrivateFolderEntity)

    @Query("DELETE FROM private_folders WHERE folderPath = :path")
    suspend fun removePrivateFolder(path: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPrivateVideo(video: PrivateVideoEntity)

    @Query("DELETE FROM private_videos WHERE mediaUri = :uri")
    suspend fun removePrivateVideo(uri: String)

    @Query("DELETE FROM private_videos")
    suspend fun clearAllPrivateVideos()
}

@Dao
interface CustomFolderDao {
    @Query("SELECT * FROM custom_folders ORDER BY addedTimestamp DESC")
    fun getAllCustomFoldersFlow(): Flow<List<CustomFolderEntity>>

    @Query("SELECT * FROM custom_folders ORDER BY addedTimestamp DESC")
    suspend fun getAllCustomFolders(): List<CustomFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFolder(folder: CustomFolderEntity)

    @Query("DELETE FROM custom_folders WHERE folderUri = :uri")
    suspend fun deleteCustomFolder(uri: String)
}
