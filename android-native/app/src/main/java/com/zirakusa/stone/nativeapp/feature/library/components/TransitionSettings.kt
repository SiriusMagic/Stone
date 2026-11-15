package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlin.math.roundToInt

/** Reproducción sin pausas and Crossfade switches; saved and applied to the running player at once. */
@Composable internal fun TransitionSettings(store: StoneSettingsStore?) {
    val saved = remember(store) { store?.read() }
    var gapless by remember { mutableStateOf(saved?.gapless ?: true) }
    var crossfade by remember { mutableStateOf(saved?.crossfade ?: false) }
    var seconds by remember { mutableIntStateOf(saved?.crossfadeSeconds?.coerceIn(1, 12) ?: 5) }
    fun apply() = StonePlaybackService.configureTransitions(gapless, crossfade, seconds)

    ToggleSetting(R.string.gapless, R.string.gapless_hint, gapless) {
        gapless = it; store?.setBoolean("gapless", it); apply()
    }
    ToggleSetting(R.string.crossfade, R.string.crossfade_hint, crossfade) {
        crossfade = it; store?.setBoolean("crossfade", it); apply()
    }
    if (crossfade) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Slider(seconds.toFloat(), { seconds = it.roundToInt().coerceIn(1, 12) }, Modifier.weight(1f),
            valueRange = 1f..12f, steps = 10,
            onValueChangeFinished = { store?.setCrossfadeSeconds(seconds); apply() })
        Text(stringResource(R.string.crossfade_seconds, seconds), Modifier.width(56.dp).padding(start = 8.dp),
            color = StonePalette.Secondary, fontSize = 14.sp)
    }
}
