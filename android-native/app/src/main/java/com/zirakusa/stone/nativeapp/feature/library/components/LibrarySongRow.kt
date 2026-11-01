package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun LibrarySongRow(song: Song, current: Boolean, enabled: Boolean, play: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = play).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (song.isVideo) LibraryCover(glyph = StoneGlyph.Video)
        else LibraryCover(song = song, current = current)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
            Text(if (song.isVideo) "${song.folder} · ${stringResource(R.string.video_media)}"
                else "${song.artist} - ${song.album}",
                Modifier.padding(top = 4.dp), color = StonePalette.SongSubtitle,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
        }
    }
}

@Composable fun LibraryCover(song: Song? = null, current: Boolean = false, glyph: StoneGlyph = StoneGlyph.Note) {
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
        .background(if (current) StonePalette.PlayingCover else StonePalette.Cover)
        .padding(if (current) 4.dp else 0.dp), contentAlignment = Alignment.Center) {
        if (song != null) StoneArtwork(song.uri, song.modifiedMs, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
        else StoneIcon(glyph, color = StonePalette.Muted, size = 24)
    }
}
