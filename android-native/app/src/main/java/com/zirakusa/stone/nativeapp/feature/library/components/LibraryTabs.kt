package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
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
    // Movies and Shorts sit before Videos; the row opens scrolled to Videos, so they
    // appear only when the user swipes the tabs to the right.
    Movies(R.string.movies), Shorts(R.string.shorts), Videos(R.string.videos), Songs(R.string.songs),
    Playlists(R.string.playlists), Folders(R.string.folders), Artists(R.string.artists), Albums(R.string.albums);

    val isVideo: Boolean get() = this == Videos || this == Shorts || this == Movies

    /** Which videos belong to this tab: Shorts < 1:30, Movies > 1 h, Videos in between. */
    fun accepts(song: com.zirakusa.stone.core.Song): Boolean = when (this) {
        Videos -> song.isVideo && song.durationMs !in 1 until SHORT_MAX_MS && song.durationMs <= MOVIE_MIN_MS
        Shorts -> song.isVideo && song.durationMs in 1 until SHORT_MAX_MS
        Movies -> song.isVideo && song.durationMs > MOVIE_MIN_MS
        else -> !song.isVideo
    }

    companion object {
        const val SHORT_MAX_MS = 90_000L
        const val MOVIE_MIN_MS = 3_600_000L
    }
}

@Composable fun LibraryTabs(selected: LibraryTab, landscape: Boolean = false, onSelect: (LibraryTab) -> Unit) {
    val scroll = rememberScrollState()
    var videosOffset by remember { mutableIntStateOf(-1) }
    var opened by rememberSaveable { mutableStateOf(false) }
    val edge = with(LocalDensity.current) { (if (landscape) 8.dp else 16.dp).roundToPx() }
    LaunchedEffect(videosOffset) {
        // Only on first layout, and never while a hidden tab (Movies/Shorts) is selected.
        if (!opened && videosOffset >= 0) {
            if (selected != LibraryTab.Movies && selected != LibraryTab.Shorts) scroll.scrollTo(videosOffset - edge)
            opened = true
        }
    }
    Row(Modifier.padding(top = if (landscape) 0.dp else 6.dp).height(if (landscape) 48.dp else 56.dp).fillMaxWidth()
        .horizontalScroll(scroll).selectableGroup().padding(horizontal = if (landscape) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LibraryTab.entries.forEach { tab ->
            val active = tab == selected
            val color = if (active) { if (landscape) StonePalette.Yellow else StonePalette.Blue } else StonePalette.Chip
            val activeText = if (landscape) StonePalette.Background else Color.White
            Row(Modifier.then(if (tab == LibraryTab.Videos) Modifier.onGloballyPositioned {
                    if (videosOffset < 0) videosOffset = it.positionInParent().x.toInt()
                } else Modifier).height(36.dp).widthIn(min = 78.dp).clip(CircleShape).background(color)
                .border(1.dp, if (active) color else StonePalette.Border, CircleShape)
                .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                .semantics(mergeDescendants = true) { selectedSemantics = active }
                .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tab.isVideo) StoneIcon(StoneGlyph.Video,
                    color = if (active) activeText else StonePalette.Muted, size = 16)
                Text(stringResource(tab.label), modifier = Modifier
                    .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                    .semantics { selectedSemantics = active },
                    color = if (active) activeText else StonePalette.Secondary,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
            }
        }
    }
}
