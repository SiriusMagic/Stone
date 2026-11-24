package com.zirakusa.stone.nativeapp.playback

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.core.Song

/**
 * «Abrir con Stone» from another app (Luma, a file manager): the file is played at once in the full player, even when
 * it is not in the library. Only content:// links that the other app shared with Stone are accepted.
 *
 * Also what an assistant asks (FAMILIA.md §10): Android's «play from search» (an empty query = everything shuffled) and
 * the family's «recomendaciones del día» (only from apps signed by the same developer: see the manifest).
 */
object ExternalOpen {
    /** What an assistant asked to play. */
    sealed interface Request {
        data class Search(val query: String) : Request
        data object Daily : Request
    }

    /** The assistant's order waiting for the library (consumed by the screen). */
    var request by mutableStateOf<Request?>(null)
        private set

    const val PLAY_RECOMMENDED = "com.zirakusa.family.action.PLAY_RECOMMENDED"

    /** The song or video waiting to be played (consumed by the screen). */
    var pending by mutableStateOf<Song?>(null)
        private set

    fun handle(context: Context, intent: Intent?) {
        when (intent?.action) {
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> { request = Request.Search(intent.getStringExtra(SearchManager.QUERY).orEmpty().take(100)); return }
            PLAY_RECOMMENDED -> { request = Request.Daily; return }
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data?.takeIf { it.scheme == "content" } ?: return
        val type = intent.type ?: context.contentResolver.getType(uri).orEmpty()
        if (!type.startsWith("video/") && !type.startsWith("audio/")) return
        pending = Song(uri.toString(), name(context, uri), "", "", "", 0, 0, 0, isVideo = type.startsWith("video/"))
    }

    fun consume() { pending = null }
    fun consumeRequest() { request = null }

    private fun name(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.ifBlank { null } ?: uri.lastPathSegment.orEmpty()
}
