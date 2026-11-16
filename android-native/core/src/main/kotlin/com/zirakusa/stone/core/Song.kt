package com.zirakusa.stone.core

data class Song(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val durationMs: Long,
    val size: Long,
    val modifiedMs: Long,
    val lyrics: List<String> = emptyList(),
    val syncedLyrics: List<LyricLine> = emptyList(),
    val isVideo: Boolean = false,
    /** Optional artwork extracted from a Stone package. Normal files use embedded artwork. */
    val artworkUri: String = "",
    /** Genre tag ("Electrónica", "Rock"…); empty when the file has none. */
    val genre: String = "",
) {
    val id: String get() = "local:$uri"
    val artworkSource: String get() = artworkUri.ifBlank { uri }
    fun matches(query: String) = listOf(title, artist, album, folder, genre).any {
        it.contains(query.trim(), ignoreCase = true)
    }
    fun canReuse(newSize: Long, newModifiedMs: Long) =
        newModifiedMs > 0 && newSize > 0 && size == newSize && modifiedMs == newModifiedMs
}
