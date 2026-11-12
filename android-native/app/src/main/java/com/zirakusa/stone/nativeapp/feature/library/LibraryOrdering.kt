package com.zirakusa.stone.nativeapp.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.feature.library.components.LibrarySort
import java.text.Collator

@Composable internal fun rememberLibrarySongs(state: LibraryState, query: String,
    sort: LibrarySort, descending: Boolean): List<Song> =
    remember(state.songs, state.playCounts, query, sort, descending) {
        val collator = Collator.getInstance()
        val filtered = state.songs.filter { it.matches(query) }
        val ordered = when (sort) {
            LibrarySort.Duration -> filtered.sortedBy { it.durationMs }
            LibrarySort.Size -> filtered.sortedBy { it.size }
            LibrarySort.MostPlayed -> filtered.sortedWith(
                compareByDescending<Song> { state.playCounts[it.id] ?: 0 }.thenBy { it.title.lowercase() })
            LibrarySort.Lyrics -> filtered.sortedWith { first, second ->
                fun Song.hasLyrics() = lyrics.any(String::isNotBlank) || syncedLyrics.any { it.text.isNotBlank() }
                val order = second.hasLyrics().compareTo(first.hasLyrics())
                if (order != 0) order else collator.compare(first.title, second.title)
            }
            else -> filtered.sortedWith { a, b ->
                fun key(song: Song) = when (sort) {
                    LibrarySort.Artist -> song.artist
                    LibrarySort.Album -> song.album
                    else -> song.title
                }
                collator.compare(key(a), key(b))
            }
        }
        if (descending) ordered.reversed() else ordered
    }
