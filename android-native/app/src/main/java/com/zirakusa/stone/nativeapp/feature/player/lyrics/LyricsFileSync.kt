package com.zirakusa.stone.nativeapp.feature.player.lyrics

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.tags.AudioTagEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.zirakusa.stone.nativeapp.feature.player.tags.rememberMediaWriteGate

/**
 * Copies saved lyrics into the song file itself (MP3 USLT, FLAC LYRICS, M4A ©lyr), so they
 * travel with the file when it is shared or opened in another player. Stone's own copy is
 * saved first by the caller; this only adds the file. Blank text removes the embedded lyrics.
 * Files indexed by Android ask for write permission first, like the tag editor.
 */
@Composable internal fun rememberLyricsFileWriter(): (song: Song, lyrics: String) -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext
    val gate = rememberMediaWriteGate()
    val scope = rememberCoroutineScope()
    return remember(app, gate, scope) {
        { song, lyrics ->
            val editor = AudioTagEditor(app)
            if (song.isVideo || !editor.supports(song.uri)) {
                Toast.makeText(app, R.string.lyrics_file_unsupported, Toast.LENGTH_LONG).show()
            } else gate(song.uri) {
                scope.launch {
                    val error = withContext(Dispatchers.IO) {
                        runCatching { editor.writeLyrics(song.uri, lyrics) }.exceptionOrNull()
                    }
                    val message = if (error == null) app.getString(R.string.lyrics_file_saved)
                        else app.getString(R.string.lyrics_file_failed, error.message.orEmpty())
                    Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
