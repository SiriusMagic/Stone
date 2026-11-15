package com.zirakusa.stone.nativeapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook

/** Stone's penguin, or the image the user chose in Personalizado. */
@Composable fun StoneAvatar(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val custom = StoneLook.avatarImage
    if (custom != null) StoneArtwork(custom, custom.hashCode().toLong(), modifier)
    else Image(painterResource(R.drawable.stone_penguin), contentDescription, modifier, contentScale = ContentScale.Fit)
}
