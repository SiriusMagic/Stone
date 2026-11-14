package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Renders the user-configurable secondary video actions in their saved order. */
@Composable internal fun VideoControlRow(
    controls: List<VideoControlId>,
    modifier: Modifier = Modifier,
    onAudioOnly: (() -> Unit)? = null,
    onOptions: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null,
    onScaleToggle: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        controls.forEach { id ->
            val definition = VideoControlRegistry.definition(id)
            val action = when (id) {
                VideoControlId.AudioOnly -> onAudioOnly
                VideoControlId.Options -> onOptions
                VideoControlId.Queue -> onQueue
                VideoControlId.Scale -> onScaleToggle
                VideoControlId.Lock -> onLock
            }
            action?.let {
                IconButton(onClick = it, modifier = Modifier.size(48.dp)) {
                    Icon(definition.icon, stringResource(definition.label))
                }
            }
        }
    }
}
