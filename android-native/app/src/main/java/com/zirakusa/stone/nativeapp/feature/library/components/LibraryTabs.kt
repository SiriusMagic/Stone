package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected as selectedSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

enum class LibraryTab(val label: Int) {
    Videos(R.string.videos), Songs(R.string.songs), Playlists(R.string.playlists),
    Folders(R.string.folders), Artists(R.string.artists), Albums(R.string.albums)
}

@Composable fun LibraryTabs(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
    Row(Modifier.padding(top = 6.dp).height(56.dp).fillMaxWidth()
        .horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LibraryTab.entries.forEach { tab ->
            val active = tab == selected
            val color = if (active) StonePalette.Blue else StonePalette.Chip
            Row(Modifier.height(36.dp).widthIn(min = 78.dp).clip(CircleShape).background(color)
                .border(1.dp, if (active) color else StonePalette.Border, CircleShape)
                .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                .semantics(mergeDescendants = true) { selectedSemantics = active }
                .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tab == LibraryTab.Videos) StoneIcon(StoneGlyph.Video,
                    color = if (active) Color.White else StonePalette.Muted, size = 16)
                Text(stringResource(tab.label), modifier = Modifier
                    .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                    .semantics { selectedSemantics = active },
                    color = if (active) Color.White else StonePalette.Secondary,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
            }
        }
    }
}
