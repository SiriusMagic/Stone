package com.zirakusa.stone.nativeapp.feature.library.video

/** Presentation used by the portrait video library. */
enum class VideoLibraryLayout(val key: String) {
    /** "Grande": big cards. */
    GRID("grid"),
    LIST("list"),
    /** Denser list with small thumbnails. */
    COMPACT("compact");

    companion object {
        fun fromKey(value: String?): VideoLibraryLayout =
            entries.firstOrNull { it.key == value } ?: GRID
    }
}
