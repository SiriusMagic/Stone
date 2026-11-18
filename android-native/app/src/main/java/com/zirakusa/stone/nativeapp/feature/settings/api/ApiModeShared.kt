package com.zirakusa.stone.nativeapp.feature.settings.api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.actions.RecordStatus
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

internal val ApiGreen = Color(0xFF4CD27A)
internal val ApiRed = Color(0xFFFF5C5C)

/** Category key (first part of an action name) → label and icon. */
internal val apiCategories: Map<String, Pair<Int, ImageVector>> = linkedMapOf(
    "player" to (R.string.api_cat_player to Icons.Default.PlayCircle),
    "library" to (R.string.api_cat_library to Icons.Default.LibraryMusic),
    "playlist" to (R.string.api_cat_playlist to Icons.AutoMirrored.Filled.QueueMusic),
    "metadata" to (R.string.api_cat_metadata to Icons.Default.Sell),
    "equalizer" to (R.string.api_cat_equalizer to Icons.Default.GraphicEq),
    "audio" to (R.string.api_cat_audio to Icons.AutoMirrored.Filled.VolumeUp),
    "video" to (R.string.api_cat_video to Icons.Default.Movie),
    "appearance" to (R.string.api_cat_appearance to Icons.Default.Palette),
    "settings" to (R.string.api_cat_settings to Icons.Default.Settings),
    "stone" to (R.string.api_cat_stone to Icons.AutoMirrored.Filled.HelpOutline))

internal fun AccessMode.label() = when (this) {
    AccessMode.READ -> R.string.api_mode_read; AccessMode.WRITE -> R.string.api_mode_write; AccessMode.FULL -> R.string.api_mode_full
}
internal fun AccessMode.hint() = when (this) {
    AccessMode.READ -> R.string.api_mode_read_hint; AccessMode.WRITE -> R.string.api_mode_write_hint
    AccessMode.FULL -> R.string.api_mode_full_hint
}
internal fun AccessMode.icon() = when (this) {
    AccessMode.READ -> Icons.AutoMirrored.Filled.MenuBook; AccessMode.WRITE -> Icons.Default.EditNote; AccessMode.FULL -> Icons.Default.Bolt
}
internal fun RecordStatus.color() = when (this) { RecordStatus.OK -> ApiGreen; RecordStatus.DENIED -> StonePalette.Yellow; RecordStatus.FAILED -> ApiRed }

/** Rounded card with an icon, a title, an optional subtitle and an optional trailing action. */
@Composable internal fun ApiSection(title: Int, icon: ImageVector, subtitle: Int? = null,
    action: Pair<Int, () -> Unit>? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(StonePalette.Chip)
        .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(26.dp), tint = StonePalette.Text)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Black, fontSize = 18.sp)
                subtitle?.let { Text(stringResource(it), color = StonePalette.Muted, fontSize = 13.sp) }
            }
            action?.let { (label, onClick) ->
                Text(stringResource(label) + " ›", Modifier.clickable(onClick = onClick).padding(6.dp),
                    color = StonePalette.Yellow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Card of the General tab: icon, title, explanation and a control on the right (switch or button). */
@Composable internal fun ApiActionCard(icon: ImageVector, title: Int, subtitle: Int, trailing: @Composable () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(StonePalette.Chip)
        .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(30.dp), tint = StonePalette.Text)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text(stringResource(subtitle), color = StonePalette.Secondary, fontSize = 13.sp)
            }
            trailing()
        }
        footer()
    }
}

/** Selectable card for an [AccessMode] (radio style, accent border when chosen). */
/** Small coloured label (status of a request, mode of a token). */
@Composable internal fun ApiPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(10.dp))
        .background(color.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 3.dp), color = color,
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}
