package com.zirakusa.stone.nativeapp.data.library.scan

import com.zirakusa.stone.core.Song

/**
 * Files other apps leave in Android's media index that are not music, such as their automatic backups
 * ("AutoBackup_20261115_205503821", album "All data backup"; "AutoPlaylist_…", "Playlist backup").
 * Stone never shows them. The files themselves are not touched: they belong to the app that made them.
 */
object NotMusic {
    private val BACKUP_NAME = Regex("^auto[ _-]?(backup|playlist)[ _-]?\\d{8}[ _-]?\\d{4,9}$", RegexOption.IGNORE_CASE)
    private val BACKUP_ALBUM = Regex("^(all data|playlist|app data|data)? ?backup$", RegexOption.IGNORE_CASE)

    fun matches(song: Song): Boolean = BACKUP_NAME.matches(song.title.trim()) || BACKUP_ALBUM.matches(song.album.trim())
}
