package com.zirakusa.stone.nativeapp.data.persistence

import android.content.Context
import android.net.Uri
import org.json.JSONObject

object StoneBackup {
    fun export(context: Context, uri: Uri) {
        val bytes = StoneDatabase.get(context).exportJson().toString(2).toByteArray()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("No se pudo crear la copia")
    }
    fun import(context: Context, uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo abrir la copia")
        StoneDatabase.get(context).importJson(JSONObject(text))
    }
}
