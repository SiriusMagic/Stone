package com.zirakusa.stone.nativeapp.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.PlaybackState

/** Right-side options panel used only by the horizontal mini player. */
@Composable internal fun MiniPlayerOptionsPanel(state: PlaybackState, song: Song?, onClose: () -> Unit,
    onSelect: (PlayerTool) -> Unit, extraActions: List<PlayerOptionExtra> = emptyList(),
    immersive: Boolean = false) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().zIndex(10f)) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .58f))
            .clickable(onClick = onClose))
        AnimatedVisibility(
            visible = true,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn()
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight()
                    .fillMaxWidth(.82f)
                    .widthIn(max = 420.dp)
                    .then(if (immersive) Modifier else
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))),
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                color = Color(0xFF62393B),
                tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(top = 10.dp, bottom = 8.dp)
                        .width(44.dp).height(4.dp).background(Color(0x99FFFFFF), RoundedCornerShape(4.dp))
                        .align(Alignment.CenterHorizontally))
                    PlayerOptionsContent(
                        state = state,
                        song = song,
                        modifier = Modifier.weight(1f),
                        extraActions = extraActions,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}
