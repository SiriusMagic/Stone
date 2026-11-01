package com.zirakusa.stone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.MediaDescription
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

data class StoneQueueEntry(val id: String, val title: String, val artist: String)

class StonePlaybackService : Service() {
  private lateinit var session: MediaSession
  private lateinit var notifications: NotificationManager

  override fun onCreate() {
    super.onCreate()
    started = true
    notifications = getSystemService(NotificationManager::class.java)
    createChannel()
    session = MediaSession(this, "StonePlayback").apply {
      setCallback(object : MediaSession.Callback() {
        override fun onPlay() = StonePlaybackEngine.play()
        override fun onPause() = StonePlaybackEngine.pause()
        override fun onSeekTo(pos: Long) = StonePlaybackEngine.seekTo(pos / 1000.0)
        override fun onSkipToNext() = StonePlaybackEngine.sendCommand("next")
        override fun onSkipToPrevious() = StonePlaybackEngine.sendCommand("previous")
        override fun onStop() = close()
      })
      isActive = true
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_PLAY -> StonePlaybackEngine.play()
      ACTION_PAUSE -> StonePlaybackEngine.pause()
      ACTION_NEXT -> StonePlaybackEngine.sendCommand("next")
      ACTION_PREVIOUS -> StonePlaybackEngine.sendCommand("previous")
      ACTION_CLOSE -> { close(); return START_NOT_STICKY }
    }
    publish()
    return START_STICKY
  }

  private fun publish() {
    val state = StonePlaybackEngine.notificationState()
    StoneMediaNotification.ensureArtwork(this, state) { if (started) publish() }
    session.setMetadata(MediaMetadata.Builder()
      .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
      .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist)
      .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
      .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, StoneMediaNotification.artworkFor(state))
      .build())
    session.setQueue(queue.mapIndexed { index, item ->
      MediaSession.QueueItem(MediaDescription.Builder()
        .setMediaId(item.id)
        .setTitle(item.title)
        .setSubtitle(item.artist)
        .build(), index.toLong())
    })
    session.setQueueTitle("A continuación")
    val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
      PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
      PlaybackState.ACTION_STOP
    session.setPlaybackState(PlaybackState.Builder()
      .setActions(actions)
      .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        state.positionMs, state.rate)
      .setActiveQueueItemId(queueIndex.toLong())
      .build())
    val notification = StoneMediaNotification.build(this, state, session.sessionToken)
    startForeground(NOTIFICATION_ID, notification)
  }

  private fun close() {
    StonePlaybackEngine.pause(false)
    session.isActive = false
    if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
    else @Suppress("DEPRECATION") stopForeground(true)
    notifications.cancel(NOTIFICATION_ID)
    stopSelf()
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < 26) return
    notifications.createNotificationChannel(NotificationChannel(
      StoneMediaNotification.CHANNEL_ID, "Reproducción de Stone", NotificationManager.IMPORTANCE_LOW
    ).apply { description = "Controles del reproductor"; setShowBadge(false) })
  }

  override fun onBind(intent: Intent?): IBinder? = null
  override fun onDestroy() { started = false; session.release(); super.onDestroy() }

  companion object {
    const val ACTION_PLAY = "stone.play"
    const val ACTION_PAUSE = "stone.pause"
    const val ACTION_NEXT = "stone.next"
    const val ACTION_PREVIOUS = "stone.previous"
    const val ACTION_CLOSE = "stone.close"
    private const val ACTION_REFRESH = "stone.refresh"
    private const val NOTIFICATION_ID = 130
    @Volatile private var started = false
    @Volatile private var queue: List<StoneQueueEntry> = emptyList()
    @Volatile private var queueIndex = -1

    fun setQueue(context: Context, items: List<StoneQueueEntry>, currentIndex: Int) {
      queue = items
      queueIndex = currentIndex
      if (started) refresh(context, StonePlaybackEngine.notificationState().playing)
    }

    fun refresh(context: Context, playing: Boolean) {
      if (!started && !playing) return
      val intent = Intent(context, StonePlaybackService::class.java).setAction(ACTION_REFRESH)
      if (!started && Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
      else context.startService(intent)
    }
  }
}
