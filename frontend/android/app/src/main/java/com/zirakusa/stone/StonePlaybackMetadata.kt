package com.zirakusa.stone

import android.media.MediaPlayer

object StonePlaybackMetadata {
  private var sourceUri = ""
  private var title = "Stone"
  private var artist = "Reproductor de música"
  private var artworkUri: String? = null

  fun update(source: String, nextTitle: String, nextArtist: String, artwork: String?) {
    sourceUri = source
    title = nextTitle.ifBlank { "Stone" }
    artist = nextArtist.ifBlank { "Artista desconocido" }
    artworkUri = artwork
  }

  fun snapshot(player: MediaPlayer?, rate: Float) = StoneNotificationState(
    sourceUri = sourceUri,
    title = title,
    artist = artist,
    artworkUri = artworkUri,
    playing = runCatching { player?.isPlaying == true }.getOrDefault(false),
    positionMs = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L),
    durationMs = runCatching { player?.duration?.coerceAtLeast(0)?.toLong() ?: 0L }.getOrDefault(0L),
    rate = rate,
  )
}
