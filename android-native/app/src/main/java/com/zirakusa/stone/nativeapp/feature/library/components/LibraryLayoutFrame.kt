package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.HomeLayout

/** One content call site keeps the library alive when its surrounding layout changes. */
@Composable fun LibraryLayoutFrame(layout: HomeLayout, modifier: Modifier = Modifier,
    sidebar: @Composable () -> Unit, player: @Composable (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Row(modifier.fillMaxSize().clipToBounds()) {
        if (layout.sidebar) {
            Box(Modifier.width(layout.sidebarWidthDp.dp).fillMaxHeight().clipToBounds()
                .testTag("home-sidebar")) { sidebar() }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight().clipToBounds().testTag("home-library"), content = content)
        Box(Modifier.width(layout.playerWidthDp.dp).fillMaxHeight().clipToBounds()) {
            player(layout.dockedPlayer)
        }
    }
}
