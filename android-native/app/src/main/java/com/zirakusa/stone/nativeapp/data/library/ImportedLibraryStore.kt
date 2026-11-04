package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import com.zirakusa.stone.core.Song
import org.json.JSONArray
import java.io.File

/** Songs imported from a .musp package. They live in app-private storage and never alter originals. */
class ImportedLibraryStore(context: Context) {
    private val index = File(context.filesDir, "imported-media/index.json")

    init { index.parentFile?.mkdirs() }

    @Synchronized fun songs(): List<Song> = runCatching {
        val values = JSONArray(index.takeIf(File::exists)?.readText() ?: "[]")
        List(values.length()) { SongJson.fromJson(values.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized fun merge(additions: List<Song>): List<Song> {
        val merged = (songs() + additions).distinctBy { it.uri }
        val values = JSONArray().apply { merged.forEach { put(SongJson.toJson(it)) } }
        val temporary = File(index.parentFile, "index.json.tmp")
        temporary.writeText(values.toString())
        check(temporary.renameTo(index) || temporary.copyTo(index, overwrite = true).let { temporary.delete(); true })
        return merged
    }
}
