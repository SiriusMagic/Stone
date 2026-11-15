package com.zirakusa.stone.nativeapp.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore

/** Accent colour that replaces Stone's gold everywhere (lines, highlights, active icons). */
enum class StoneAccent(val key: String, val color: Color, val label: Int) {
    GOLD("gold", Color(0xFFF5C518), R.string.color_gold),
    BLUE("blue", Color(0xFF4FB3FF), R.string.color_blue),
    WHITE("white", Color(0xFFF2F2F5), R.string.color_white),
    COFFEE("coffee", Color(0xFFC89B6D), R.string.color_coffee),
    GREEN("green", Color(0xFF4CD27A), R.string.color_green),
    RED("red", Color(0xFFFF5C6C), R.string.color_red),
    PURPLE("purple", Color(0xFFB388FF), R.string.color_purple),
    PINK("pink", Color(0xFFFF7EB6), R.string.color_pink);

    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } ?: GOLD }
}

/** Colours offered for text and icons. */
enum class StoneInk(val key: String, val color: Color, val label: Int) {
    WHITE("white", Color.White, R.string.color_white),
    SOFT("soft", Color(0xFFD8D6DE), R.string.color_soft),
    ACCENT("accent", Color.Unspecified, R.string.color_accent),
    BLUE("blue", Color(0xFF9ED3FF), R.string.color_blue),
    COFFEE("coffee", Color(0xFFE7CDB2), R.string.color_coffee),
    GREEN("green", Color(0xFFB9F0C9), R.string.color_green);

    fun resolve(accent: StoneAccent) = if (this == ACCENT) accent.color else color
    companion object { fun from(key: String, fallback: StoneInk) = entries.firstOrNull { it.key == key } ?: fallback }
}

/**
 * User customisation ("Ajustes > Personalizado"). Snapshot state so every screen updates the
 * moment a value changes; persisted in the settings database.
 */
object StoneLook {
    var accent by mutableStateOf(StoneAccent.GOLD)
    var textInk by mutableStateOf(StoneInk.WHITE)
    var iconInk by mutableStateOf(StoneInk.WHITE)
    var fontScale by mutableFloatStateOf(1f)
    var iconScale by mutableFloatStateOf(1f)
    /** App-private copy of the chosen background image, or null. */
    var backgroundImage by mutableStateOf<String?>(null)
    /** App-private copy of the image that replaces the penguin, or null. */
    var avatarImage by mutableStateOf<String?>(null)
    /** Name shown inside Stone (the launcher label itself can't be changed by an app). */
    var appTitle by mutableStateOf("")
    /** Songs view: "grid" (Grande), "list" or "compact". */
    var songLayout by mutableStateOf("list")
        private set
    private var store: StoneSettingsStore? = null

    fun chooseSongLayout(key: String) { songLayout = key; store?.setLook("songLayout", key) }

    /** Saves the current values with the store given at [load] (used by automation). */
    fun persist() { store?.let(::save) }

    fun load(store: StoneSettingsStore) {
        this.store = store
        songLayout = store.look("songLayout", "list")
        accent = StoneAccent.from(store.look("accent", "gold"))
        textInk = StoneInk.from(store.look("textInk", "white"), StoneInk.WHITE)
        iconInk = StoneInk.from(store.look("iconInk", "white"), StoneInk.WHITE)
        fontScale = store.look("fontScale", "1").toFloatOrNull()?.coerceIn(.8f, 1.4f) ?: 1f
        iconScale = store.look("iconScale", "1").toFloatOrNull()?.coerceIn(.8f, 1.5f) ?: 1f
        backgroundImage = store.look("backgroundImage", "").ifBlank { null }
        avatarImage = store.look("avatarImage", "").ifBlank { null }
        appTitle = store.look("appTitle", "")
    }

    fun save(store: StoneSettingsStore) {
        store.setLook("accent", accent.key)
        store.setLook("textInk", textInk.key)
        store.setLook("iconInk", iconInk.key)
        store.setLook("fontScale", fontScale.toString())
        store.setLook("iconScale", iconScale.toString())
        store.setLook("backgroundImage", backgroundImage.orEmpty())
        store.setLook("avatarImage", avatarImage.orEmpty())
        store.setLook("appTitle", appTitle)
    }

    fun reset(store: StoneSettingsStore) {
        accent = StoneAccent.GOLD; textInk = StoneInk.WHITE; iconInk = StoneInk.WHITE
        fontScale = 1f; iconScale = 1f; backgroundImage = null; avatarImage = null; appTitle = ""
        save(store)
    }
}
