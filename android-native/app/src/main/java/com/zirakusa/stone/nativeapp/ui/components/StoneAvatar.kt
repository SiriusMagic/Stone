package com.zirakusa.stone.nativeapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook

/** The chosen mascot (same as the home-screen icon), or the image the user picked in Personalizado. */
@Composable fun StoneAvatar(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val custom = StoneLook.avatarImage
    if (custom != null) StoneArtwork(custom, custom.hashCode().toLong(), modifier)
    else Image(painterResource(LauncherIcon.current.drawable), contentDescription, modifier, contentScale = ContentScale.Fit)
}
