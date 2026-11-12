package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

@Stable class PlayerSheetsState {
    var queueVisible by mutableStateOf(false)
    var lyricsVisible by mutableStateOf(false)
    var optionsVisible by mutableStateOf(false)
    var optionsSidePanel by mutableStateOf(false)
    var tool by mutableStateOf<PlayerTool?>(null)
    val visible get() = queueVisible || lyricsVisible || optionsVisible || tool != null
}

@Composable fun rememberPlayerSheetsState(): PlayerSheetsState = rememberSaveable(
    saver = listSaver<PlayerSheetsState, Any>(
        save = {
            listOf(it.queueVisible, it.lyricsVisible, it.optionsVisible,
                it.optionsSidePanel, it.tool?.name.orEmpty())
        },
        restore = { values -> PlayerSheetsState().apply {
            queueVisible = values[0] as Boolean
            lyricsVisible = values[1] as Boolean
            optionsVisible = values[2] as Boolean
            optionsSidePanel = values.getOrNull(3) as? Boolean ?: false
            val toolName = values.getOrNull(4) as? String ?: values.getOrNull(3) as? String
            tool = PlayerTool.entries.firstOrNull { it.name == toolName }
        } }
    )
) { PlayerSheetsState() }
