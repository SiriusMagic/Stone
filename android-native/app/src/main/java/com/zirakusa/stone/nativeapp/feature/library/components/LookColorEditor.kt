package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * Mixer for the free accent colour: hue, saturation and brightness sliders plus a hex field.
 * The preview updates live; [onChange] receives the final colour on every move.
 */
@Composable internal fun CustomAccentEditor(color: Color, onChange: (Color) -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember(color) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsv[1]) }
    var value by remember(color) { mutableFloatStateOf(hsv[2]) }
    var hexText by remember(color) { mutableStateOf(StoneLook.hex(color)) }
    var hexError by remember { mutableStateOf(false) }
    fun emit() = onChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))))

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(color))
        OutlinedTextField(hexText, { text ->
            hexText = text.take(7).uppercase()
            val parsed = StoneLook.parseHex(hexText)
            hexError = parsed == null
            if (parsed != null) onChange(parsed)
        }, Modifier.weight(1f), singleLine = true, isError = hexError,
            label = { Text(stringResource(R.string.custom_hex)) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
    }
    HueSlider(R.string.custom_hue, hue, 0f..360f, { hue = it; emit() })
    HueSlider(R.string.custom_saturation, saturation, 0f..1f, { saturation = it; emit() })
    HueSlider(R.string.custom_brightness, value, .25f..1f, { value = it; emit() })
    Text(stringResource(R.string.custom_color_hint), color = StonePalette.Muted, fontSize = 12.sp)
}

@Composable private fun HueSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.width(84.dp), fontSize = 13.sp, color = StonePalette.Secondary)
        Slider(value, onChange, Modifier.weight(1f), valueRange = range)
    }
}
