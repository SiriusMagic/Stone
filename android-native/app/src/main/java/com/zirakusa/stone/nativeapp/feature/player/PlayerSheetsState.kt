package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

@Stable class PlayerSheetsState {
    var queueVisible by mutableStateOf(false)
    var lyricsVisible by mutableStateOf(false)
    var optionsVisible by mutableStateOf(false)
    var tool by mutableStateOf<PlayerTool?>(null)
    val visible get() = queueVisible || lyricsVisible || optionsVisible || tool != null
}

@Composable fun rememberPlayerSheetsState(): PlayerSheetsState = rememberSaveable(
    saver = listSaver<PlayerSheetsState, Any>(
        save = { listOf(it.queueVisible, it.lyricsVisible, it.optionsVisible, it.tool?.name.orEmpty()) },
        restore = { values -> PlayerSheetsState().apply {
            queueVisible = values[0] as Boolean
            lyricsVisible = values[1] as Boolean
            optionsVisible = values[2] as Boolean
            tool = PlayerTool.entries.firstOrNull { it.name == values[3] }
        } }
    )
) { PlayerSheetsState() }
