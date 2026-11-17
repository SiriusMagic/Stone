package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.feature.settings.api.icon
import com.zirakusa.stone.nativeapp.feature.settings.api.label

enum class LibrarySort(val label: Int) { Title(R.string.sort_title), Artist(R.string.artists),
    Album(R.string.albums), Duration(R.string.sort_duration), Size(R.string.sort_size),
    MostPlayed(R.string.sort_most_played), Lyrics(R.string.sort_lyrics) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibrarySheets(sheet: String?, sort: LibrarySort, descending: Boolean,
    onSort: (LibrarySort) -> Unit, onReverse: () -> Unit, dismiss: () -> Unit,
    deleted: List<Song>, restoreDeleted: (Song) -> Unit,
    deletePermanently: (List<Song>, (List<Song>) -> Unit) -> Unit = { _, _ -> },
    markPurged: (List<Song>) -> Unit = {}) {
    if (sheet == null) return
    if (sheet == "deleted") {
        RecentlyDeletedSheet(deleted, restoreDeleted, deletePermanently, dismiss, markPurged)
        return
    }
    if (sheet != "sort") return
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = StonePalette.Chip) {
        ImmersiveWindowEffect()
        Column(Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.sort),
                style = MaterialTheme.typography.titleLarge)
            LibrarySort.entries.forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { onSort(item) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sort == item, { onSort(item) })
                    Text(stringResource(item.label))
                }
            }
            SheetAction(StoneGlyph.Sort, if (descending) R.string.descending else R.string.ascending, onReverse)
        }
    }
}

@Composable private fun SheetAction(icon: StoneGlyph, label: Int, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        StoneIcon(icon); Text(stringResource(label))
    }
}
