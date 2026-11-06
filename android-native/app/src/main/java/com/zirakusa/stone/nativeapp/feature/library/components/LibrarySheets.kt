package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.core.Song

enum class LibrarySort(val label: Int) { Title(R.string.sort_title), Artist(R.string.artists),
    Album(R.string.albums), Duration(R.string.sort_duration), Size(R.string.sort_size),
    Modified(R.string.sort_modified) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibrarySheets(sheet: String?, sort: LibrarySort, descending: Boolean,
    onSort: (LibrarySort) -> Unit, onReverse: () -> Unit, onFolder: () -> Unit,
    onScan: () -> Unit, dismiss: () -> Unit, hidden: List<Song> = emptyList(),
    deleted: List<Song> = emptyList(), openHidden: () -> Unit = {}, openDeleted: () -> Unit = {},
    restoreHidden: (Song) -> Unit = {}, restoreDeleted: (Song) -> Unit = {},
    onExport: () -> Unit = {}, onImport: () -> Unit = {}, onOpenSettings: () -> Unit = {},
    theme: String = "dark", onThemeChange: (String) -> Unit = {}, language: String = "es",
    onLanguageChange: (String) -> Unit = {}, settingsPage: String? = null,
    onSettingsPage: (String?) -> Unit = {}) {
    if (sheet == null) return
    if (sheet == "downloads") {
        AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.downloads)) },
            text = { Text(stringResource(R.string.downloads_pending)) },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss)) } })
        return
    }
    if (sheet == "hidden" || sheet == "deleted") {
        DispositionSheet(sheet == "deleted", if (sheet == "deleted") deleted else hidden,
            if (sheet == "deleted") restoreDeleted else restoreHidden, dismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = StonePalette.Chip) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(if (sheet == "sort") R.string.sort else R.string.settings),
                style = MaterialTheme.typography.titleLarge)
            if (sheet == "sort") {
                LibrarySort.entries.forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { onSort(item) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sort == item, { onSort(item) })
                        Text(stringResource(item.label))
                    }
                }
                SheetAction(StoneGlyph.Sort, if (descending) R.string.descending else R.string.ascending, onReverse)
            } else {
                Text(stringResource(R.string.settings_pending), Modifier.padding(top = 12.dp, bottom = 4.dp),
                    color = StonePalette.Muted, style = MaterialTheme.typography.bodySmall)
                SheetAction(StoneGlyph.Settings, R.string.settings) { dismiss(); onOpenSettings() }
                SheetAction(StoneGlyph.Folder, R.string.choose_stone_folder) { dismiss(); onFolder() }
                SheetAction(StoneGlyph.Scan, R.string.scan_folders) { dismiss(); onScan() }
                SheetAction(StoneGlyph.Close, R.string.hidden_files) { openHidden() }
                SheetAction(StoneGlyph.Download, R.string.recently_deleted) { openDeleted() }
                SheetAction(StoneGlyph.Download, R.string.export_backup) { dismiss(); onExport() }
                SheetAction(StoneGlyph.Folder, R.string.import_backup) { dismiss(); onImport() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DispositionSheet(deleted: Boolean, songs: List<Song>, restore: (Song) -> Unit,
    dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = StonePalette.Chip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.8f).padding(horizontal = 20.dp)) {
            Text(stringResource(if (deleted) R.string.recently_deleted else R.string.hidden_files),
                style = MaterialTheme.typography.headlineSmall)
            if (songs.isEmpty()) Text(stringResource(R.string.no_results), Modifier.padding(top = 32.dp), color = StonePalette.Muted)
            LazyColumn(Modifier.weight(1f)) { items(songs, key = { it.id }) { song ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(song.title); Text(song.artist, color = StonePalette.Muted) }
                    TextButton(onClick = { restore(song) }) { Text(stringResource(R.string.restore)) }
                }
            } }
        }
    }
}

@Composable private fun SheetAction(icon: StoneGlyph, label: Int, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        StoneIcon(icon); Text(stringResource(label))
    }
}
