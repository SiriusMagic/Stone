package com.zirakusa.stone.nativeapp.feature.settings.look

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.LookImages
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.theme.stoneBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Background photo options: Fondo de pantalla / Foco, a live preview and the opacity slider. */
@Composable internal fun LookBackgroundEditor(persist: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, StoneLook.backgroundImage) {
        value = withContext(Dispatchers.IO) { LookImages.load(StoneLook.backgroundImage, 800)?.asImageBitmap() }
    }
    val modes = listOf("wallpaper" to R.string.custom_background_wallpaper, "focus" to R.string.custom_background_focus)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        modes.forEachIndexed { index, (key, label) ->
            SegmentedButton(StoneLook.backgroundMode == key, { StoneLook.backgroundMode = key; persist() },
                SegmentedButtonDefaults.itemShape(index, modes.size)) { Text(stringResource(label)) }
        }
    }
    // Miniature of the home screen with the real backdrop, so the effect is visible before leaving.
    Box(Modifier.padding(top = 12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(170.dp).height(300.dp).clip(RoundedCornerShape(22.dp))
            .border(3.dp, StonePalette.Chip, RoundedCornerShape(22.dp)).stoneBackdrop(image).padding(14.dp)) {
            Text(StoneLook.appTitle.ifBlank { stringResource(R.string.app_name) }, fontSize = 15.sp, color = StonePalette.Text)
            repeat(6) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = .14f)))
                    Column(Modifier.padding(start = 8.dp)) {
                        Box(Modifier.width(90.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .28f)))
                        Box(Modifier.padding(top = 5.dp).width(54.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = .16f)))
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.custom_background_opacity, (StoneLook.backgroundOpacity * 100).roundToInt()),
        Modifier.padding(top = 12.dp), color = StonePalette.Secondary, fontSize = 14.sp)
    Slider(StoneLook.backgroundOpacity, { StoneLook.backgroundOpacity = it }, valueRange = 0.1f..0.9f, onValueChangeFinished = persist)
    Text(stringResource(R.string.custom_background_hint), color = StonePalette.Muted, fontSize = 12.sp)
}
