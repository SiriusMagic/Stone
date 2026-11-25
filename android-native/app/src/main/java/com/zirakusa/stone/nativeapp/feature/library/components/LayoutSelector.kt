package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.library.video.VideoLibraryLayout

/** Compact / list / "Grande" switch, icons only; the active one sits in a filled circle. */
@Composable internal fun LayoutSelector(current: VideoLibraryLayout, onChange: (VideoLibraryLayout) -> Unit,
    modifier: Modifier = Modifier) {
    val options = listOf(
        VideoLibraryLayout.COMPACT to Icons.Default.ViewHeadline,
        VideoLibraryLayout.LIST to Icons.Default.ViewList,
        VideoLibraryLayout.GRID to Icons.Default.GridView,
    )
    Row(modifier.testTag("layout-selector"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (layout, icon) ->
            val active = layout == current
            val description = stringResource(when (layout) {
                VideoLibraryLayout.GRID -> R.string.video_layout_grid
                VideoLibraryLayout.LIST -> R.string.video_layout_list
                VideoLibraryLayout.COMPACT -> R.string.layout_compact_action
            })
            // Icon only: the active option is a filled circle, the rest stay muted.
            Box(Modifier.size(36.dp).clip(CircleShape)
                .background(if (active) StonePalette.Blue else Color.Transparent)
                .clickable { onChange(layout) }.semantics { contentDescription = description },
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (active) Color.White else StonePalette.Muted, modifier = Modifier.size(22.dp))
            }
        }
    }
}
