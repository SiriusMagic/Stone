package com.zirakusa.stone.nativeapp.data.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import org.json.JSONArray
import org.json.JSONObject

/** Versioned source of truth for user-created Stone data. The media index remains disposable. */
class StoneDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "stone-user-data.db", null, 1) {

    companion object {
        @Volatile private var instance: StoneDatabase? = null
        fun get(context: Context): StoneDatabase = instance ?: synchronized(this) {
            instance ?: StoneDatabase(context.applicationContext).also {
                instance = it
                it.migratePreferences(context.applicationContext)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE data_key_value (data_key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun getString(key: String): String? = readableDatabase.query("data_key_value",
        arrayOf("value"), "data_key=?", arrayOf(key), null, null, null).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    @Synchronized fun forEachValueStartingWith(prefix: String, visitor: (String, String) -> Unit) {
        readableDatabase.query(
            "data_key_value", arrayOf("data_key", "value"), "data_key LIKE ?",
            arrayOf("$prefix%"), null, null, null).use { cursor ->
            while (cursor.moveToNext()) visitor(cursor.getString(0), cursor.getString(1))
        }
    }

    @Synchronized fun putString(key: String, value: String) {
        writableDatabase.insertWithOnConflict("data_key_value", null, ContentValues().apply {
            put("data_key", key); put("value", value); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun remove(key: String) { writableDatabase.delete("data_key_value", "data_key=?", arrayOf(key)) }
    @Synchronized fun keys(prefix: String = ""): List<String> = readableDatabase.query(
        "data_key_value", arrayOf("data_key"), "data_key LIKE ?", arrayOf("$prefix%"),
        null, null, "data_key").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    @Synchronized fun clearAll() { writableDatabase.delete("data_key_value", null, null) }
    @Synchronized fun setting(key: String, fallback: String): String = getString("setting.$key") ?: fallback
    @Synchronized fun setSetting(key: String, value: String) = putString("setting.$key", value)

    @Synchronized fun exportJson(): JSONObject {
        val values = JSONObject()
        readableDatabase.query("data_key_value", arrayOf("data_key", "value"), null, null,
            null, null, "data_key").use { cursor -> while (cursor.moveToNext()) values.put(cursor.getString(0), cursor.getString(1)) }
        return JSONObject().put("format", "stone-kotlin-backup").put("version", 1)
            .put("createdAt", System.currentTimeMillis()).put("values", values)
    }

    @Synchronized fun importJson(root: JSONObject) {
        require(root.getString("format") == "stone-kotlin-backup" && root.getInt("version") == 1)
        val values = root.getJSONObject("values")
        val db = writableDatabase
        db.transaction {
            values.keys().forEach { key -> db.insertWithOnConflict("data_key_value", null, ContentValues().apply {
                put("data_key", key); put("value", values.getString(key)); put("updated_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE) }
        }
    }

    private fun migratePreferences(context: Context) {
        if (getString("meta.preferences-migrated") == "1") return
        val db = writableDatabase
        db.transaction {
            val library = context.getSharedPreferences("stone-library-user-data", 0)
            library.all.forEach { (key, value) -> putDirect(db, "library.$key", encode(value)) }
            val favorites = context.getSharedPreferences("stone-favorites", 0).all
                .filterValues { it == true }.keys
            putDirect(db, "favorites", JSONArray(favorites).toString())
            val disposition = context.getSharedPreferences("stone-song-disposition", 0)
            putDirect(db, "disposition.deleted",
                JSONArray(disposition.getStringSet("deleted", emptySet()).orEmpty()).toString())
            val lyrics = JSONObject()
            context.getSharedPreferences("stone-lyrics", 0).all.forEach { (key, value) -> if (value is String) lyrics.put(key, value) }
            putDirect(db, "lyrics", lyrics.toString())
            context.getSharedPreferences("stone-playback-tools", 0).all.forEach { (key, value) ->
                putDirect(db, "tool.$key", encode(value)) }
            putDirect(db, "meta.preferences-migrated", "1")
        }
    }

    private fun putDirect(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("data_key_value", null, ContentValues().apply {
            put("data_key", key); put("value", value); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun encode(value: Any?): String = when (value) {
        is Set<*> -> JSONArray(value).toString()
        null -> ""
        else -> value.toString()
    }
}
