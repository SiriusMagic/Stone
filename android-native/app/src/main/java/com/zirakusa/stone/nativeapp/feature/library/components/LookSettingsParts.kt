package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlin.math.roundToInt

/** Small building blocks shared by the Personalizado screens. */
@Composable internal fun LookSection(title: Int) {
    Text(stringResource(title), color = StonePalette.Yellow, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

/** Horizontal row of colour circles; [content] draws something inside a swatch (icon preview). */
@Composable internal fun Swatches(colors: List<Pair<Color, Int>>, selected: Int,
    content: (@Composable (Int) -> Unit)? = null, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEachIndexed { index, (color, label) ->
            Column(Modifier.width(64.dp).clickable { onSelect(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color)
                    .border(2.dp, if (index == selected) Color.White else StonePalette.Border, CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (content != null) content(index)
                    else if (index == selected) Icon(Icons.Default.Check, null,
                        tint = if (color.luminanceIsLight()) Color.Black else Color.White)
                }
                Text(stringResource(label), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    color = if (index == selected) StonePalette.Text else StonePalette.Secondary)
            }
        }
    }
}

internal fun Color.luminanceIsLight() = (red * .299f + green * .587f + blue * .114f) > .6f

@Composable internal fun ScaleSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(value, { onChange((it * 20).roundToInt() / 20f) }, Modifier.weight(1f), valueRange = range)
        Text("${(value * 100).roundToInt()}%", Modifier.width(56.dp).padding(start = 8.dp))
    }
}

@Composable internal fun ImageChoice(hasImage: Boolean, pick: () -> Unit, remove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = pick) { Text(stringResource(R.string.custom_pick_image)) }
        if (hasImage) OutlinedButton(onClick = remove) { Text(stringResource(R.string.custom_remove_image)) }
    }
}
