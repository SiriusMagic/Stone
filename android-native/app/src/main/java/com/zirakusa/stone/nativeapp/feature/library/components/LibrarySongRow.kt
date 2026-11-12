package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable fun LibrarySongRow(song: Song, current: Boolean, enabled: Boolean,
    compact: Boolean = false, play: () -> Unit) {
    val hasLyrics = !song.isVideo && (song.lyrics.any { it.isNotBlank() } ||
        song.syncedLyrics.any { it.text.isNotBlank() })
    val rowStyle = if (compact) Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
        .background(if (current) StonePalette.Yellow.copy(alpha = .12f) else StonePalette.Surface)
        .then(if (current) Modifier.border(1.dp, StonePalette.Yellow.copy(alpha = .45f), RoundedCornerShape(16.dp)) else Modifier)
        else Modifier
    Row(Modifier.fillMaxWidth().then(rowStyle).clickable(enabled = enabled, onClick = play)
        .padding(horizontal = if (compact) 8.dp else 0.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
        if (song.isVideo) LibraryCover(glyph = StoneGlyph.Video)
        else LibraryCover(song = song, current = current, compact = compact)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasLyrics) Text(stringResource(R.string.lyrics_badge), color = Color.White,
                    fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp,
                    modifier = Modifier.border(1.dp, StonePalette.SongSubtitle, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp))
                Text(if (song.isVideo) "${song.folder} · ${stringResource(R.string.video_media)}"
                    else if (compact) song.artist else "${song.artist} - ${song.album}",
                    Modifier.weight(1f).padding(start = if (hasLyrics) 8.dp else 0.dp),
                    color = StonePalette.SongSubtitle, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
            }
        }
    }
}

@Composable fun LibraryCover(song: Song? = null, current: Boolean = false,
    glyph: StoneGlyph = StoneGlyph.Note, compact: Boolean = false) {
    Box(Modifier.size(if (compact) 48.dp else 72.dp).clip(RoundedCornerShape(if (compact) 12.dp else 22.dp))
        .background(if (current) { if (compact) StonePalette.Yellow else StonePalette.PlayingCover } else StonePalette.Cover)
        .padding(if (current) 4.dp else 0.dp), contentAlignment = Alignment.Center) {
        if (song != null) StoneArtwork(song.artworkSource, song.modifiedMs, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
        else StoneIcon(glyph, color = StonePalette.Muted, size = 24)
    }
}
