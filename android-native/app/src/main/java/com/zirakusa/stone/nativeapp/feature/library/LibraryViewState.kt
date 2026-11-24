package com.zirakusa.stone.nativeapp.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.feature.library.components.LibrarySort

/** Library order shared by the sort sheet and automation (MCP mode), so both stay in sync. */
object LibraryViewState {
    var sort by mutableStateOf(LibrarySort.Title)
    var descending by mutableStateOf(false)
}
