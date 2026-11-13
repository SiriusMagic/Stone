package com.zirakusa.stone.nativeapp.feature.library.components

/** Presentation used by the portrait video library. */
enum class VideoLibraryLayout(val key: String) {
    GRID("grid"),
    LIST("list");

    companion object {
        fun fromKey(value: String?): VideoLibraryLayout =
            entries.firstOrNull { it.key == value } ?: GRID
    }
}
