package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.LookImages
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.ui.theme.StoneAccent
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * Bottom of Ajustes › Tema: load a background photo (the same one as Personalizado) and pick the
 * accent colour from circles. Opacity and Fondo/Foco stay in Personalizado.
 */
@Composable internal fun ThemeExtras(store: StoneSettingsStore?) {
    val context = LocalContext.current
    fun persist() { store?.let(StoneLook::save) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { LookImages.save(context, uri, "background", 1600) }
            .onSuccess { StoneLook.backgroundImage = it; persist() }
    }
    Text(stringResource(R.string.theme_background_and_color), Modifier.padding(top = 20.dp, bottom = 10.dp),
        color = StonePalette.Yellow, fontWeight = FontWeight.Black)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(StonePalette.Chip)
            .clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AddPhotoAlternate, stringResource(R.string.theme_add_background), tint = StonePalette.Yellow)
        }
        if (StoneLook.backgroundImage != null) Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(StonePalette.Chip)
            .clickable { StoneLook.backgroundImage = null; persist() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.HideImage, stringResource(R.string.theme_remove_background), tint = StonePalette.Secondary)
        }
        Box(Modifier.width(1.5.dp).height(28.dp).background(StonePalette.Muted.copy(alpha = .6f)))
        StoneAccent.entries.filter { it != StoneAccent.CUSTOM }.forEach { accent ->
            val selected = StoneLook.accent == accent
            Box(Modifier.size(56.dp).clip(CircleShape).background(accent.color)
                .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                .clickable { StoneLook.accent = accent; persist() }, contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Default.Check, stringResource(accent.label), tint = Color.Black)
            }
        }
    }
    Text(stringResource(R.string.theme_more_in_custom), Modifier.padding(top = 10.dp), color = StonePalette.Muted)
}
