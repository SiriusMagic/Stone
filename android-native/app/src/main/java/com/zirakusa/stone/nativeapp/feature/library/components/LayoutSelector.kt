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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Compact / list / "Grande" switch; the active option is a pill with its name. */
@Composable internal fun LayoutSelector(current: VideoLibraryLayout, onChange: (VideoLibraryLayout) -> Unit,
    modifier: Modifier = Modifier) {
    val options = listOf(
        Triple(VideoLibraryLayout.COMPACT, Icons.Default.ViewHeadline, R.string.layout_compact),
        Triple(VideoLibraryLayout.LIST, Icons.Default.ViewList, R.string.layout_list),
        Triple(VideoLibraryLayout.GRID, Icons.Default.GridView, R.string.layout_large),
    )
    Row(modifier.testTag("layout-selector"), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { (layout, icon, label) ->
            val active = layout == current
            val description = stringResource(when (layout) {
                VideoLibraryLayout.GRID -> R.string.video_layout_grid
                VideoLibraryLayout.LIST -> R.string.video_layout_list
                VideoLibraryLayout.COMPACT -> R.string.layout_compact_action
            })
            Row(Modifier.height(36.dp).clip(CircleShape)
                .background(if (active) StonePalette.Blue else Color.Transparent)
                .clickable { onChange(layout) }.semantics { contentDescription = description }
                .padding(horizontal = if (active) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (active) Color.White else StonePalette.Muted, modifier = Modifier.size(22.dp))
                if (active) Text(stringResource(label), Modifier.padding(start = 6.dp), color = Color.White,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
