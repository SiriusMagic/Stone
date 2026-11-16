package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zirakusa.stone.nativeapp.MainActivity
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.LookImages
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.ui.components.StoneAvatar
import com.zirakusa.stone.nativeapp.ui.theme.LauncherIcon
import com.zirakusa.stone.nativeapp.ui.theme.StoneAccent
import com.zirakusa.stone.nativeapp.ui.theme.StoneInk
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Ajustes › Personalizado: colours, sizes, images, home icon and name/shortcut. Owns the state. */
@Composable internal fun LookSettings(store: StoneSettingsStore?) {
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

    LookSection(R.string.custom_accent)
    val accent = StoneLook.accentColor
    Swatches(StoneAccent.entries.map { (if (it == StoneAccent.CUSTOM) StoneLook.customAccent else it.color) to it.label },
        StoneLook.accent.ordinal) { StoneLook.accent = StoneAccent.entries[it]; persist() }
    if (StoneLook.accent == StoneAccent.CUSTOM) CustomAccentEditor(StoneLook.customAccent) {
        StoneLook.customAccent = it; persist()
    }
    LookSection(R.string.custom_text_color)
    Swatches(StoneInk.entries.map { it.resolve(accent) to it.label }, StoneLook.textInk.ordinal) {
        StoneLook.textInk = StoneInk.entries[it]; persist()
    }
    LookSection(R.string.custom_icon_color)
    Swatches(StoneInk.entries.map { it.resolve(accent) to it.label }, StoneLook.iconInk.ordinal) {
        StoneLook.iconInk = StoneInk.entries[it]; persist()
    }
    LookSection(R.string.custom_palette)
    LookColorPalette(StoneLook.accentColor) { StoneLook.accent = StoneAccent.CUSTOM; StoneLook.customAccent = it; persist() }
    Text(stringResource(R.string.custom_palette_hint), color = StonePalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

    LookSection(R.string.custom_font_size)
    ScaleSlider(StoneLook.fontScale, .8f..1.4f) { StoneLook.fontScale = it; persist() }
    LookSection(R.string.custom_font_style)
    LookFontStyle(::persist)
    LookSection(R.string.custom_icon_size)
    ScaleSlider(StoneLook.iconScale, .8f..1.5f) { StoneLook.iconScale = it; persist() }

    LookSection(R.string.artwork_shape)
    LookArtworkShape(::persist)

    LookSection(R.string.custom_background)
    ImageChoice(StoneLook.backgroundImage != null, { backgroundPicker.launch("image/*") }) {
        StoneLook.backgroundImage = null; persist()
    }
    if (StoneLook.backgroundImage != null) LookBackgroundEditor(::persist)
    LookSection(R.string.custom_avatar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StoneAvatar(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        ImageChoice(StoneLook.avatarImage != null, { avatarPicker.launch("image/*") }) {
            StoneLook.avatarImage = null; persist()
        }
    }

    LookSection(R.string.custom_launcher_icon)
    MascotPicker(LauncherIcon.current) { LauncherIcon.apply(context, it) }
    Text(stringResource(R.string.custom_launcher_icon_hint), color = StonePalette.Muted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp))

    LookSection(R.string.custom_app_name)
    OutlinedTextField(StoneLook.appTitle, { StoneLook.appTitle = it.take(30); persist() }, Modifier.fillMaxWidth(),
        singleLine = true, placeholder = { Text(stringResource(R.string.app_name)) },
        supportingText = { Text(stringResource(R.string.custom_app_name_hint)) })
    Button(onClick = { if (!pinShortcut(context)) Toast.makeText(context, R.string.custom_shortcut_unsupported,
        Toast.LENGTH_LONG).show() }, Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(stringResource(R.string.custom_shortcut))
    }
    Text(stringResource(R.string.custom_shortcut_hint), color = StonePalette.Muted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp))

    OutlinedButton(onClick = { store?.let(StoneLook::reset) }, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
        Text(stringResource(R.string.custom_reset))
    }
}

/** Home-screen shortcut with the custom name and image (launcher label/icon can't be changed). */
private fun pinShortcut(context: android.content.Context): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val label = StoneLook.appTitle.ifBlank { context.getString(R.string.app_name) }
    val bitmap = LookImages.load(StoneLook.avatarImage, 256)
    val icon = bitmap?.let(IconCompat::createWithBitmap) ?: IconCompat.createWithResource(context, LauncherIcon.current.drawable)
    val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
    val shortcut = ShortcutInfoCompat.Builder(context, "stone-custom-${System.currentTimeMillis()}")
        .setShortLabel(label).setLongLabel(label).setIcon(icon).setIntent(intent).build()
    return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
