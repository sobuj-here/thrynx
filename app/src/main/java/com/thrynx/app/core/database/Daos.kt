package com.thrynx.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 30): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE uri = :uri LIMIT 1")
    suspend fun getHistoryForUri(uri: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_media ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteMediaEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_media WHERE uri = :uri)")
    fun isFavoriteFlow(uri: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteMediaEntity)

    @Query("DELETE FROM favorite_media WHERE uri = :uri")
    suspend fun removeFavorite(uri: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE isVideoPlaylist = :isVideo ORDER BY createdAt DESC")
    fun getPlaylists(isVideo: Boolean): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM export_history ORDER BY exportedAt DESC")
    fun getAllExports(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: ExportHistoryEntity)

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteExportById(id: String)

    @Query("DELETE FROM export_history")
    suspend fun clearExportHistory()
}
