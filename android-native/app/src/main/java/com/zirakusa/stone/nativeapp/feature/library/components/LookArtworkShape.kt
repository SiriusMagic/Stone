package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Title and hint of each player artwork shape, in the order shown in Ajustes. */
internal val artworkShapeLabels = mapOf(
    "round_spin" to (R.string.artwork_round to R.string.artwork_round_spin_hint),
    "square" to (R.string.artwork_square to null),
    "round" to (R.string.artwork_round to R.string.artwork_round_still_hint),
    "fullscreen" to (R.string.artwork_fullscreen to R.string.artwork_fullscreen_hint))

/** "Forma de portada" in Personalizado: how the cover looks in the player. */
@Composable internal fun LookArtworkShape(persist: () -> Unit) {
    StoneLook.ARTWORK_SHAPES.forEach { key ->
        val (title, hint) = artworkShapeLabels.getValue(key)
        Row(Modifier.fillMaxWidth().clickable { StoneLook.artworkShape = key; persist() }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 16.sp)
                hint?.let { Text(stringResource(it), color = StonePalette.Muted, fontSize = 13.sp) }
            }
            RadioButton(StoneLook.artworkShape == key, { StoneLook.artworkShape = key; persist() })
        }
    }
}

/** "Tipo de letra" in Personalizado: Romana (upright, default) or Cursiva; each option shows its own style. */
@Composable internal fun LookFontStyle(persist: () -> Unit) {
    val options = listOf("roman" to R.string.custom_font_roman, "italic" to R.string.custom_font_italic)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (key, label) ->
            SegmentedButton(StoneLook.fontStyle == key, { StoneLook.fontStyle = key; persist() },
                SegmentedButtonDefaults.itemShape(index, options.size)) {
                Text(stringResource(label), fontStyle = if (key == "italic") FontStyle.Italic else FontStyle.Normal)
            }
        }
    }
}
