package com.zirakusa.stone.nativeapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun StoneArtwork(uri: String?, modified: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val artwork by produceState<Bitmap?>(null, uri, modified) {
        value = null
        if (uri != null) value = ArtworkLoader.load(context, uri, modified)
    }
    Box(modifier.background(StonePalette.Cover), contentAlignment = Alignment.Center) {
        val bitmap = artwork
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else StoneIcon(StoneGlyph.Note, color = StonePalette.Muted, size = 24)
    }
}
