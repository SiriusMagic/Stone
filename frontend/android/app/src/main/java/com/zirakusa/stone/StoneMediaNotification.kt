package com.zirakusa.stone

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.URL

data class StoneNotificationState(
  val sourceUri: String,
  val title: String,
  val artist: String,
  val artworkUri: String?,
  val playing: Boolean,
  val positionMs: Long,
  val durationMs: Long,
  val rate: Float,
)

object StoneMediaNotification {
  const val CHANNEL_ID = "stone_playback"
  private var artworkKey: String? = null
  private var artwork: Bitmap? = null
  private var loadingKey: String? = null
  private val main = Handler(Looper.getMainLooper())

  fun artworkFor(state: StoneNotificationState): Bitmap? =
    artwork.takeIf { artworkKey == key(state) }

  fun ensureArtwork(context: Context, state: StoneNotificationState, ready: () -> Unit) {
    val key = key(state)
    if (artworkKey == key || loadingKey == key) return
    loadingKey = key
    Thread {
      val loaded = loadUri(context, state.artworkUri)
        ?: loadEmbedded(context, state.sourceUri)
        ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
      val scaled = loaded?.let(::scaled)
      main.post {
        if (loadingKey == key) {
          artworkKey = key
          artwork = scaled
          loadingKey = null
          ready()
        }
      }
    }.start()
  }

  @Suppress("DEPRECATION")
  fun build(context: Context, state: StoneNotificationState, token: MediaSession.Token): Notification {
    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID)
      else Notification.Builder(context)
    val playAction = if (state.playing) StonePlaybackService.ACTION_PAUSE else StonePlaybackService.ACTION_PLAY
    val playIcon = if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    builder
      .setSmallIcon(R.drawable.ic_stat_stone)
      .setContentTitle(state.title)
      .setContentText(state.artist)
      .setLargeIcon(artworkFor(state))
      .setContentIntent(activityIntent(context))
      .setDeleteIntent(serviceIntent(context, StonePlaybackService.ACTION_CLOSE, 5))
      .setCategory(Notification.CATEGORY_TRANSPORT)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setOngoing(state.playing)
      .setColor(Color.parseColor("#5A3436"))
      .addAction(android.R.drawable.ic_media_previous, "Anterior", serviceIntent(context, StonePlaybackService.ACTION_PREVIOUS, 1))
      .addAction(playIcon, if (state.playing) "Pausar" else "Reproducir", serviceIntent(context, playAction, 2))
      .addAction(android.R.drawable.ic_media_next, "Siguiente", serviceIntent(context, StonePlaybackService.ACTION_NEXT, 3))
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar", serviceIntent(context, StonePlaybackService.ACTION_CLOSE, 4))
      .setStyle(Notification.MediaStyle().setMediaSession(token).setShowActionsInCompactView(0, 1, 2))
    if (Build.VERSION.SDK_INT >= 26) builder.setColorized(true)
    return builder.build()
  }

  private fun activityIntent(context: Context) = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  private fun serviceIntent(context: Context, action: String, code: Int) = PendingIntent.getService(
    context, code, Intent(context, StonePlaybackService::class.java).setAction(action),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )

  private fun key(state: StoneNotificationState) = state.artworkUri ?: state.sourceUri

  private fun loadUri(context: Context, value: String?): Bitmap? = runCatching {
    if (value.isNullOrBlank()) return@runCatching null
    val uri = Uri.parse(value)
    val stream = if (uri.scheme == "http" || uri.scheme == "https") URL(value).openStream()
      else context.contentResolver.openInputStream(uri)
    stream?.use(BitmapFactory::decodeStream)
  }.getOrNull()

  private fun loadEmbedded(context: Context, value: String): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(context, Uri.parse(value))
      retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } finally { retriever.release() }
  }.getOrNull()

  private fun scaled(source: Bitmap): Bitmap {
    val largest = maxOf(source.width, source.height)
    if (largest <= 512) return source
    val ratio = 512f / largest
    return Bitmap.createScaledBitmap(source, (source.width * ratio).toInt(), (source.height * ratio).toInt(), true)
  }
}
