package com.zirakusa.stone.nativeapp.feature.settings.look

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.zirakusa.stone.nativeapp.ui.theme.StoneThemeSpec
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.themeLabel

/** Top of "Temas y personalización": every theme as a small screen preview; tap to apply. */
@Composable internal fun ThemeGrid(theme: String, onTheme: (String) -> Unit) {
    Text(stringResource(R.string.settings_theme), Modifier.padding(bottom = 10.dp), color = StonePalette.Yellow,
        fontWeight = FontWeight.Black)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StoneThemeSpec.entries.forEach { spec ->
            val selected = spec.key == theme
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable { onTheme(spec.key) }) {
                Box(Modifier.size(76.dp, 116.dp).clip(RoundedCornerShape(16.dp)).background(spec.background)
                    .border(if (selected) 3.dp else 1.dp, if (selected) StonePalette.Yellow else Color.White.copy(alpha = .15f),
                        RoundedCornerShape(16.dp)).padding(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(spec.chip)) }
                    }
                    if (selected) Icon(Icons.Default.Check, null, Modifier.align(Alignment.BottomEnd).size(20.dp), tint = StonePalette.Yellow)
                }
                Text(themeLabel(spec.key), Modifier.padding(top = 4.dp), fontSize = 12.sp, maxLines = 1,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
