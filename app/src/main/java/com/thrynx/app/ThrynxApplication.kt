package com.thrynx.app

import android.app.Application
import com.thrynx.app.core.database.ThrynxDatabase
import com.thrynx.app.core.datastore.PreferencesManager
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

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = ThrynxDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        playerManager = ThrynxPlayerManager(this)
        mediaScanner = MediaStoreScanner(this)
    }

    companion object {
        lateinit var instance: ThrynxApplication
            private set
    }
}
