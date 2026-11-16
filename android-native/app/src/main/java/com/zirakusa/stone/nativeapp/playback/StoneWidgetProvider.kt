package com.zirakusa.stone.nativeapp.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.scale
import androidx.media3.common.Player
import com.zirakusa.stone.nativeapp.MainActivity
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.persistence.PlaybackDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home-screen player (4×2): cover, title, artist and previous / play-pause / next / favourite.
 * Buttons act on the running playback service; with nothing loaded, play opens Stone.
 */
class StoneWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = StonePlaybackService.currentPlayer()
        when (intent.action) {
            ACTION_PLAY -> if (player == null || player.mediaItemCount == 0) openApp(context)
                else if (player.isPlaying) player.pause() else { player.prepare(); player.play() }
            ACTION_NEXT -> player?.seekToNext()
            ACTION_PREVIOUS -> player?.seekToPrevious()
            ACTION_FAVORITE -> player?.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                ?.let { PlaybackDataStore(context).toggleFavorite(it) }
            else -> return
        }
        refresh(context)
    }

    companion object {
        private const val ACTION_PLAY = "com.zirakusa.stone.widget.PLAY"
        private const val ACTION_NEXT = "com.zirakusa.stone.widget.NEXT"
        private const val ACTION_PREVIOUS = "com.zirakusa.stone.widget.PREVIOUS"
        private const val ACTION_FAVORITE = "com.zirakusa.stone.widget.FAVORITE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun component(context: Context) = ComponentName(context, StoneWidgetProvider::class.java)

        /** Redraws every Stone widget with the current song; cheap when none is placed. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = runCatching { manager.getAppWidgetIds(component(app)) }.getOrNull()
            if (ids == null || ids.isEmpty()) return
            val player = StonePlaybackService.currentPlayer()
            val item = player?.currentMediaItem
            scope.launch {
                val cover = item?.let { media ->
                    val source = media.mediaMetadata.artworkUri?.toString() ?: media.localConfiguration?.uri?.toString()
                    source?.let { withContext(Dispatchers.IO) {
                        runCatching { ArtworkLoader.load(app, it, 0L)?.let { bitmap ->
                            val side = 256
                            if (bitmap.width > side || bitmap.height > side) bitmap.scale(side, side * bitmap.height / bitmap.width.coerceAtLeast(1))
                            else bitmap
                        } }.getOrNull()
                    } }
                }
                val favorite = item?.mediaId?.let { PlaybackDataStore(app).isFavorite(it) } == true
                val views = RemoteViews(app.packageName, R.layout.widget_player).apply {
                    setTextViewText(R.id.widget_title, item?.mediaMetadata?.title ?: app.getString(R.string.app_name))
                    setTextViewText(R.id.widget_artist, item?.mediaMetadata?.artist ?: app.getString(R.string.widget_idle))
                    if (cover != null) setImageViewBitmap(R.id.widget_cover, cover)
                    else setImageViewResource(R.id.widget_cover, R.drawable.ic_stat_new_media)
                    setImageViewResource(R.id.widget_play, if (player?.isPlaying == true) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                    setImageViewResource(R.id.widget_favorite, if (favorite) R.drawable.ic_widget_heart_on else R.drawable.ic_widget_heart)
                    setOnClickPendingIntent(R.id.widget_play, action(app, ACTION_PLAY, 1))
                    setOnClickPendingIntent(R.id.widget_next, action(app, ACTION_NEXT, 2))
                    setOnClickPendingIntent(R.id.widget_prev, action(app, ACTION_PREVIOUS, 3))
                    setOnClickPendingIntent(R.id.widget_favorite, action(app, ACTION_FAVORITE, 4))
                    setOnClickPendingIntent(R.id.widget_cover, openIntent(app))
                    setOnClickPendingIntent(R.id.widget_title, openIntent(app))
                }
                runCatching { manager.updateAppWidget(ids, views) }
            }
        }

        private fun action(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(context, code,
            Intent(context, StoneWidgetProvider::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun openIntent(context: Context) = PendingIntent.getActivity(context, 10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)

        private fun openApp(context: Context) = runCatching { openIntent(context).send() }

        /** Player listener that keeps placed widgets in sync. */
        fun listener(context: Context) = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_MEDIA_METADATA_CHANGED)) refresh(context)
            }
        }
    }
}
