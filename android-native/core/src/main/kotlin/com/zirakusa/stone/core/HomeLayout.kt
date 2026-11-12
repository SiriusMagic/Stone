package com.zirakusa.stone.core

enum class LandscapeLayoutMode(val key: String) {
    ALWAYS("always"), LIBRARY_ONLY("library_only");

    companion object {
        fun fromKey(value: String) = entries.firstOrNull { it.key == value } ?: ALWAYS
    }
}

data class HomeLayout(
    val fullPlayer: Boolean,
    val sidebarWidthDp: Float = 0f,
    val playerWidthDp: Float = 0f,
    val showMiniPlayer: Boolean = false,
) {
    val sidebar: Boolean get() = sidebarWidthDp > 0f
    val dockedPlayer: Boolean get() = playerWidthDp > 0f
}

/** Pure window policy: changing layouts never sends commands to the playback service. */
fun homeLayout(widthDp: Float, landscape: Boolean, playerOpen: Boolean,
    video: Boolean, pictureInPicture: Boolean, mode: LandscapeLayoutMode): HomeLayout {
    if (pictureInPicture || (playerOpen &&
        (!landscape || widthDp < 600f || video || mode == LandscapeLayoutMode.LIBRARY_ONLY))) {
        return HomeLayout(fullPlayer = true)
    }
    if (!landscape) return HomeLayout(fullPlayer = false, showMiniPlayer = true)
    val rail = when {
        widthDp >= 1200f -> 160f
        widthDp >= 720f -> 72f
        else -> 64f
    }
    if (widthDp < 600f) return HomeLayout(false, rail, showMiniPlayer = true)
    val available = widthDp - rail - 16f
    val player = (available * .4f).coerceIn(240f, available - 280f)
    return HomeLayout(false, rail, player)
}
