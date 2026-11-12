package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun LandscapeLibraryHeading(tab: LibraryTab, songCount: Int, scanning: Boolean,
    onFolder: () -> Unit, onOpenFolders: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(tab.label), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tab == LibraryTab.Songs) Text(pluralStringResource(R.plurals.song_count, songCount, songCount),
                color = StonePalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (tab != LibraryTab.Videos) {
            IconButton(onClick = onFolder, enabled = !scanning) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.choose_folder), tint = StonePalette.Secondary)
            }
            IconButton(onClick = onOpenFolders) {
                Icon(Icons.Default.Folder, stringResource(R.string.folders), tint = StonePalette.Secondary)
            }
        }
    }
}
