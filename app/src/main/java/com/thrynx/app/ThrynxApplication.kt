package com.thrynx.app

import android.app.Application
import com.thrynx.app.core.database.ThrynxDatabase
import com.thrynx.app.core.datastore.PreferencesManager
import com.thrynx.app.core.download.DownloadManagerService
import com.thrynx.app.core.editor.audio.AudioStudioEngine
import com.thrynx.app.core.editor.export.VideoExportEngine
import com.thrynx.app.core.media.MediaStoreScanner
import com.thrynx.app.core.player.ThrynxPlayerManager

class ThrynxApplication : Application() {

    lateinit var database: ThrynxDatabase
        private set
    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var playerManager: ThrynxPlayerManager
        private set
    lateinit var mediaScanner: MediaStoreScanner
        private set
    lateinit var audioStudioEngine: AudioStudioEngine
        private set
    lateinit var exportEngine: VideoExportEngine
        private set
    lateinit var downloadService: DownloadManagerService
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = ThrynxDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        playerManager = ThrynxPlayerManager(this)
        mediaScanner = MediaStoreScanner(this)
        audioStudioEngine = AudioStudioEngine(this)
        exportEngine = VideoExportEngine(this)
        downloadService = DownloadManagerService(this, database.downloadDao())
    }

    companion object {
        lateinit var instance: ThrynxApplication
            private set
    }
}
