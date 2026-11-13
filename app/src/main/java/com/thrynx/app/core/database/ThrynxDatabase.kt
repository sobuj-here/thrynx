package com.thrynx.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlaybackHistoryEntity::class,
        FavoriteMediaEntity::class,
        PrivateFolderEntity::class,
        PrivateVideoEntity::class,
        CustomFolderEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ThrynxDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun privateVaultDao(): PrivateVaultDao
    abstract fun customFolderDao(): CustomFolderDao

    companion object {
        @Volatile
        private var INSTANCE: ThrynxDatabase? = null

        fun getInstance(context: Context): ThrynxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ThrynxDatabase::class.java,
                    "thrynx_master.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
