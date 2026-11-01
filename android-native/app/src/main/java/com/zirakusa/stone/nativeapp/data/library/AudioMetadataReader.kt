package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.LyricsParser

/** Port of Stone's metadata reader, without its React Native bridge. */
class AudioMetadataReader(private val context: Context) {
    private val embeddedLyrics = EmbeddedLyricsReader(context)
    fun read(uri: Uri, name: String, folder: String, size: Long, modified: Long): Song {
        val fallback = Song(uri.toString(), name.substringBeforeLast('.'), "Stone", folder,
            folder, 0, size, modified)
        val reader = MediaMetadataRetriever()
        return try {
            reader.setDataSource(context, uri)
            fun text(key: Int) = reader.extractMetadata(key)?.takeIf(String::isNotBlank)
            val lyrics = LyricsParser.parse(embeddedLyrics.read(uri))
            fallback.copy(
                title = text(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallback.title,
                artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: fallback.artist,
                album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: fallback.album,
                durationMs = text(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                lyrics = lyrics.plain,
                syncedLyrics = lyrics.synced,
            )
        } catch (error: SecurityException) { throw error }
        catch (_: RuntimeException) { fallback }
        finally { reader.release() }
    }
}
