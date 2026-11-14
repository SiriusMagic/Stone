package com.zirakusa.stone.nativeapp.data.library

import android.content.Context
import com.zirakusa.stone.nativeapp.data.persistence.StoneDatabase
import org.json.JSONArray

class SongDispositionStore(context: Context) {
    private val database = StoneDatabase.get(context)
    fun deletedIds(): Set<String> = ids("deleted")
    fun delete(id: String) = add("deleted", id)
    fun restoreDeleted(id: String) = remove("deleted", id)

    private fun values(key: String): MutableList<String> = runCatching {
        JSONArray(database.getString("disposition.$key") ?: "[]").let { array ->
            MutableList(array.length()) { array.getString(it) } }
    }.getOrDefault(mutableListOf())
    private fun ids(key: String) = values(key).mapTo(mutableSetOf()) { it.substringAfter('|') }
    private fun save(key: String, values: List<String>) = database.putString("disposition.$key", JSONArray(values).toString())
    private fun add(key: String, id: String) = values(key).also { values ->
        values.removeAll { it.substringAfter('|') == id }; values += "${System.currentTimeMillis()}|$id"; save(key, values) }
    private fun remove(key: String, id: String) = values(key).also { values ->
        values.removeAll { it.substringAfter('|') == id }; save(key, values) }
}
