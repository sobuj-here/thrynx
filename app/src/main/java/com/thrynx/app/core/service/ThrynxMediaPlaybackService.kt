package com.thrynx.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.thrynx.app.MainActivity
import com.thrynx.app.R
import com.thrynx.app.ThrynxApplication

class ThrynxMediaPlaybackService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "thrynx_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.thrynx.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.thrynx.app.ACTION_PAUSE"
        const val ACTION_NEXT = "com.thrynx.app.ACTION_NEXT"
        const val ACTION_PREV = "com.thrynx.app.ACTION_PREV"
        const val ACTION_STOP = "com.thrynx.app.ACTION_STOP"
    }

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateNotification()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            ThrynxApplication.instance.playerManager.exoPlayer.addListener(playerListener)
        } catch (ignored: Exception) {}
    }

    override fun onDestroy() {
        try {
            ThrynxApplication.instance.playerManager.exoPlayer.removeListener(playerListener)
        } catch (ignored: Exception) {}
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return try {
            ThrynxApplication.instance.playerManager.mediaSession
        } catch (e: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playerManager = ThrynxApplication.instance.playerManager
        when (intent?.action) {
            ACTION_PLAY -> playerManager.exoPlayer.play()
            ACTION_PAUSE -> playerManager.exoPlayer.pause()
            ACTION_NEXT -> playerManager.playNext()
            ACTION_PREV -> playerManager.playPrevious()
            ACTION_STOP -> {
                playerManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "THRYNX Playback Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback notification for background audio"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updateNotification() {
        val playerManager = ThrynxApplication.instance.playerManager
        val media = playerManager.playbackState.value.currentMedia
        val isPlaying = playerManager.playbackState.value.isPlaying

        if (media == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            getServicePendingIntent(ACTION_PREV)
        ).build()

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                getServicePendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                getServicePendingIntent(ACTION_PLAY)
            ).build()
        }

        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            getServicePendingIntent(ACTION_NEXT)
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            getServicePendingIntent(ACTION_STOP)
        ).build()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_thrynx_brand_mark)
            .setContentTitle(media.title)
            .setContentText(if (isPlaying) "Playing in background" else "Paused")
            .setContentIntent(contentIntent)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(playerManager.mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, ThrynxMediaPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
