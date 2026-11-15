package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zirakusa.stone.nativeapp.MainActivity
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.LookImages
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.CustomAudioLevel
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneAvatar
import com.zirakusa.stone.nativeapp.ui.theme.StoneAccent
import com.zirakusa.stone.nativeapp.ui.theme.StoneInk
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlin.math.roundToInt

/** Ajustes › Personalizado: colours, sizes, images, name/shortcut and custom audio level. */
@Composable internal fun LookSettings(store: StoneSettingsStore?, playback: PlaybackViewModel?) {
    val context = LocalContext.current
    fun persist() { store?.let(StoneLook::save) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { LookImages.save(context, uri, "background", 1600) }
            .onSuccess { StoneLook.backgroundImage = it; persist() }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { LookImages.save(context, uri, "avatar", 512) }
            .onSuccess { StoneLook.avatarImage = it; persist() }
    }
    Text(stringResource(R.string.custom_intro), color = StonePalette.Muted, modifier = Modifier.padding(bottom = 8.dp))

    Section(R.string.custom_accent)
    Swatches(StoneAccent.entries.map { it.color to it.label }, StoneLook.accent.ordinal) {
        StoneLook.accent = StoneAccent.entries[it]; persist()
    }
    Section(R.string.custom_text_color)
    Swatches(StoneInk.entries.map { it.resolve(StoneLook.accent) to it.label }, StoneLook.textInk.ordinal) {
        StoneLook.textInk = StoneInk.entries[it]; persist()
    }
    Section(R.string.custom_icon_color)
    Swatches(StoneInk.entries.map { it.resolve(StoneLook.accent) to it.label }, StoneLook.iconInk.ordinal) {
        StoneLook.iconInk = StoneInk.entries[it]; persist()
    }

    Section(R.string.custom_font_size)
    ScaleSlider(StoneLook.fontScale, .8f..1.4f) { StoneLook.fontScale = it; persist() }
    Section(R.string.custom_icon_size)
    ScaleSlider(StoneLook.iconScale, .8f..1.5f) { StoneLook.iconScale = it; persist() }

    Section(R.string.custom_background)
    ImageChoice(StoneLook.backgroundImage != null, { backgroundPicker.launch("image/*") }) {
        StoneLook.backgroundImage = null; persist()
    }
    Section(R.string.custom_avatar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StoneAvatar(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        ImageChoice(StoneLook.avatarImage != null, { avatarPicker.launch("image/*") }) {
            StoneLook.avatarImage = null; persist()
        }
    }

    Section(R.string.custom_app_name)
    OutlinedTextField(StoneLook.appTitle, { StoneLook.appTitle = it.take(30); persist() }, Modifier.fillMaxWidth(),
        singleLine = true, placeholder = { Text(stringResource(R.string.app_name)) },
        supportingText = { Text(stringResource(R.string.custom_app_name_hint)) })
    Button(onClick = { if (!pinShortcut(context)) Toast.makeText(context, R.string.custom_shortcut_unsupported,
        Toast.LENGTH_LONG).show() }, Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(stringResource(R.string.custom_shortcut))
    }
    Text(stringResource(R.string.custom_shortcut_hint), color = StonePalette.Muted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp))

    CustomAudioSection(store, playback)

    OutlinedButton(onClick = { store?.let(StoneLook::reset) }, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
        Text(stringResource(R.string.custom_reset))
    }
}

@Composable private fun Section(title: Int) {
    Text(stringResource(title), color = StonePalette.Yellow, fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable private fun Swatches(colors: List<Pair<Color, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEachIndexed { index, (color, label) ->
            Column(Modifier.width(64.dp).clickable { onSelect(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color)
                    .border(2.dp, if (index == selected) Color.White else StonePalette.Border, CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (index == selected) Icon(Icons.Default.Check, null,
                        tint = if (color.luminanceIsLight()) Color.Black else Color.White)
                }
                Text(stringResource(label), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                    color = StonePalette.Secondary)
            }
        }
    }
}

private fun Color.luminanceIsLight() = (red * .299f + green * .587f + blue * .114f) > .6f

@Composable private fun ScaleSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(value, { onChange((it * 20).roundToInt() / 20f) }, Modifier.weight(1f), valueRange = range)
        Text("${(value * 100).roundToInt()}%", Modifier.width(56.dp).padding(start = 8.dp))
    }
}

@Composable private fun ImageChoice(hasImage: Boolean, pick: () -> Unit, remove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = pick) { Text(stringResource(R.string.custom_pick_image)) }
        if (hasImage) OutlinedButton(onClick = remove) { Text(stringResource(R.string.custom_remove_image)) }
    }
}

@Composable private fun CustomAudioSection(store: StoneSettingsStore?, playback: PlaybackViewModel?) {
    var max by remember { mutableIntStateOf(CustomAudioLevel.maxPercent) }
    var gain by remember { mutableIntStateOf((CustomAudioLevel.gain * 100).roundToInt()) }
    val mode = playback?.state?.collectAsState()?.value?.audioMode
    fun apply() {
        CustomAudioLevel.maxPercent = max; CustomAudioLevel.gain = gain / 100f
        store?.let(CustomAudioLevel::save)
        if (mode == AudioMode.CUSTOM) playback?.setAudioMode(AudioMode.CUSTOM)
    }
    Section(R.string.custom_audio)
    Text(stringResource(R.string.custom_audio_max, max))
    Slider(max.toFloat(), { max = (it / 10).roundToInt() * 10 }, valueRange = 10f..300f, onValueChangeFinished = ::apply)
    Text(stringResource(R.string.custom_audio_gain, gain))
    Slider(gain.toFloat(), { gain = (it / 5).roundToInt() * 5 }, valueRange = 5f..100f, onValueChangeFinished = ::apply)
    Text(stringResource(R.string.custom_audio_hint), color = StonePalette.Muted, fontSize = 12.sp)
    if (mode != AudioMode.CUSTOM) TextButton(onClick = { apply(); playback?.setAudioMode(AudioMode.CUSTOM) }) {
        Text(stringResource(R.string.custom_audio_use))
    }
}

/** Home-screen shortcut with the custom name and image (launcher label/icon can't be changed). */
private fun pinShortcut(context: android.content.Context): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val label = StoneLook.appTitle.ifBlank { context.getString(R.string.app_name) }
    val bitmap = LookImages.load(StoneLook.avatarImage, 256)
    val icon = bitmap?.let(IconCompat::createWithBitmap) ?: IconCompat.createWithResource(context, R.drawable.stone_penguin)
    val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
    val shortcut = ShortcutInfoCompat.Builder(context, "stone-custom-${System.currentTimeMillis()}")
        .setShortLabel(label).setLongLabel(label).setIcon(icon).setIntent(intent).build()
    return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
