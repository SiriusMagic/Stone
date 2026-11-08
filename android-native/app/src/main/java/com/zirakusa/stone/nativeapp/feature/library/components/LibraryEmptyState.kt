package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun LibraryEmptyState(videos: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StoneIcon(StoneGlyph.Music, color = StonePalette.EmptyIcon, size = 40)
        Text(stringResource(if (videos) R.string.no_videos else R.string.no_songs),
            color = StonePalette.Muted, fontSize = 14.sp, letterSpacing = 0.sp)
    }
}

@Composable fun LibraryFolderAction(scanning: Boolean, showHint: Boolean,
    onFolder: () -> Unit, videos: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = StonePalette.Chip)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(if (videos) R.string.videos_not_found else R.string.songs_not_found),
            color = StonePalette.Secondary, fontSize = 15.sp,
            letterSpacing = 0.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Surface(onClick = onFolder, enabled = !scanning, shape = CircleShape, color = StonePalette.Purple) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                StoneIcon(StoneGlyph.Folder, size = 18)
                Text(stringResource(if (videos) R.string.choose_video_folder else R.string.choose_stone_folder),
                    color = StonePalette.PurpleText,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)
            }
        }
        if (showHint) Text(stringResource(R.string.scan_hint),
            Modifier.padding(top = 12.dp).padding(horizontal = 24.dp), color = StonePalette.Hint,
            fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, letterSpacing = 0.sp)
    }
}
