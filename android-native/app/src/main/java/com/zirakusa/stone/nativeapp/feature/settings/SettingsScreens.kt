package com.zirakusa.stone.nativeapp.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.onClick
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.player.video.VideoScale
import com.zirakusa.stone.nativeapp.data.persistence.StoneLanguages
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.control.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.nativeapp.feature.settings.api.ApiModeSettings
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.api.icon
import com.zirakusa.stone.nativeapp.feature.settings.api.label
import com.zirakusa.stone.nativeapp.feature.settings.feedback.FeedbackDetail
import com.zirakusa.stone.nativeapp.feature.settings.look.LookSettings
import com.zirakusa.stone.nativeapp.feature.settings.look.ThemeGrid

private data class Language(val code: String, val label: String)

/** Languages Stone ships complete translations for (names in their own language). */
private val languages = StoneLanguages.all.map { Language(it.first, it.second) }

private data class SettingsMenuItem(val icon: ImageVector, val label: String, val value: String,
    val keywords: String, val action: () -> Unit)

private data class SettingsMenuSection(val title: Int, val items: List<SettingsMenuItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsPanel(theme: String, onTheme: (String) -> Unit, language: String,
    onLanguage: (String) -> Unit, store: StoneSettingsStore?, page: String?,
    onPage: (String?) -> Unit, onClose: () -> Unit,
    onLibrarySheet: (String) -> Unit, playback: PlaybackViewModel?,
    landscapeLayoutMode: LandscapeLayoutMode = LandscapeLayoutMode.ALWAYS,
    onLandscapeLayoutChange: (LandscapeLayoutMode) -> Unit = {},
    videoScale: VideoScale = VideoScale.AUTO,
    onVideoScaleChange: (VideoScale) -> Unit = {},
    onVideoControlChange: () -> Unit = {},
    immersiveEnabled: Boolean = true,
    onImmersiveEnabledChange: (Boolean) -> Unit = {},
    shakeToReport: Boolean = true,
    onShakeToReportChange: (Boolean) -> Unit = {},
    onShortcutChange: (String, Boolean) -> Unit = { _, _ -> }) {
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    // In a sub-page the back gesture returns to the settings menu; on the menu it closes the sheet.
    ModalBottomSheet(onDismissRequest = { if (page != null) onPage(if (page in legalPages) "about" else null) else onClose() },
        containerColor = StonePalette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || page == null }),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = page == null)) {
        BackHandler(enabled = page != null, onBack = { onPage(if (page in legalPages) "about" else null) })
        ImmersiveWindowEffect()
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            if (page == null) {
                Text(stringResource(R.string.settings), fontSize = 27.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(value = settingsQuery, onValueChange = { settingsQuery = it.take(60) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (settingsQuery.isNotEmpty()) IconButton(onClick = { settingsQuery = "" }) {
                            Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                        }
                    })
                val menuSections = listOf(
                    SettingsMenuSection(R.string.settings_section_library, listOf(
                        SettingsMenuItem(Icons.Default.TravelExplore, stringResource(R.string.device_scan), "",
                            "biblioteca escanear automáticamente dispositivo permiso archivos música buscar", { onPage("library-scan") }),
                        SettingsMenuItem(Icons.Default.Delete, stringResource(R.string.recently_deleted), "",
                            "biblioteca papelera eliminados restaurar", { onClose(); onLibrarySheet("deleted") })
                    )),
                    SettingsMenuSection(R.string.settings_section_audio, listOf(
                        SettingsMenuItem(Icons.Default.Headphones, stringResource(R.string.settings_playback), "",
                            "audio reproducción sonido modos salida altavoz bluetooth usb transiciones crossfade aceleración", { onPage("playback") }),
                        SettingsMenuItem(Icons.Default.Equalizer, stringResource(R.string.audio_levels_title), "",
                            "audio niveles ganancia volumen máximo dormilón cine balanceado altavoz sonido", { onPage("audio-levels") }),
                        SettingsMenuItem(Icons.Default.Timer, stringResource(R.string.sleep_timer), "",
                            "audio reproducción temporizador dormir", { onPage("sleep") })
                    )),
                    SettingsMenuSection(R.string.settings_section_video, listOf(
                        SettingsMenuItem(Icons.Default.Movie, stringResource(R.string.video_settings), "",
                            "vídeo video inmersivo pantalla horizontal encuadre escala controles iconos", { onPage("video") })
                    )),
                    SettingsMenuSection(R.string.settings_section_notifications, listOf(
                        SettingsMenuItem(Icons.Default.Notifications, stringResource(R.string.settings_section_notifications), "",
                            "notificaciones avisos archivos nuevos canciones vídeos hora horario resumen recordatorio", { onPage("notifications") })
                    )),
                    SettingsMenuSection(R.string.settings_section_appearance, listOf(
                        SettingsMenuItem(Icons.Default.Palette, stringResource(R.string.personalization_title), themeLabel(theme),
                            "tema temas personalizado personalización apariencia colores acento letras cursiva iconos tamaño fondo imagen portada nombre icono app",
                            { onPage("custom") }),
                        SettingsMenuItem(Icons.Default.Fullscreen, stringResource(R.string.immersive_screen), "",
                            "personalización apariencia pantalla inmersiva barras sistema reproductor biblioteca", { onPage("immersive") }),
                        SettingsMenuItem(Icons.Default.Widgets, stringResource(R.string.widgets_title), "",
                            "personalización widget pantalla principal escritorio inicio reproductor", { onPage("widgets") }),
                        SettingsMenuItem(Icons.Default.Apps, stringResource(R.string.settings_home_shortcuts), "",
                            "personalización inicio iconos accesos rápidos barra", { onPage("shortcuts") }),
                        SettingsMenuItem(Icons.Default.Language, stringResource(R.string.settings_language),
                            languages.find { it.code == language }?.label.orEmpty(), "personalización idioma lenguaje", { onPage("language") })
                    )),
                    SettingsMenuSection(R.string.settings_section_system, listOf(
                        SettingsMenuItem(Icons.Default.Hub, stringResource(R.string.api_title), "",
                            "sistema api mcp agente ia inteligencia artificial automatización control externo token",
                            { onPage("api-mode") }),
                        SettingsMenuItem(Icons.Default.Memory, stringResource(R.string.system_diagnostics), "",
                            "sistema diagnóstico avanzado cpu ram memoria batería temperatura", { onPage("system-diagnostics") })
                    )),
                    SettingsMenuSection(R.string.settings_section_about, listOf(
                        SettingsMenuItem(Icons.Default.ThumbUp, stringResource(R.string.feedback_title), "",
                            "ayuda opinión sugerencias", { onPage("feedback") }),
                        SettingsMenuItem(Icons.Default.Info, stringResource(R.string.about_stone), "",
                            "ayuda acerca versión información calificar privacidad términos licencias legal", { onPage("about") })
                    ))
                )
                val normalizedQuery = settingsQuery.trim()
                val filteredSections = menuSections.map { section ->
                    section.copy(items = if (normalizedQuery.isBlank()) section.items else
                        section.items.filter { item ->
                            item.label.contains(normalizedQuery, ignoreCase = true) ||
                                item.keywords.contains(normalizedQuery, ignoreCase = true)
                        })
                }.filter { it.items.isNotEmpty() }
                if (filteredSections.isEmpty()) {
                    Text(stringResource(R.string.settings_no_results), color = StonePalette.Muted,
                        modifier = Modifier.padding(top = 28.dp, bottom = 28.dp))
                } else filteredSections.forEach { section ->
                    SettingsGroup {
                        Text(stringResource(section.title), color = StonePalette.Yellow,
                            fontWeight = FontWeight.Black, fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp))
                        section.items.forEach { item -> SettingsRow(item) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onPage(if (page in legalPages) "about" else null) }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(pageTitle(page), fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                when (page) {
                    "language" -> languages.forEach { item ->
                        SettingsChoiceText(item.label, language == item.code) { onLanguage(item.code) }
                    }
                    "shortcuts" -> HomeShortcutDetail(store, onShortcutChange)
                    "library-scan" -> LibraryScanSettings(store)
                    // Temas y personalización in one page: the theme first, then every Personalizado option.
                    "custom", "theme" -> {
                        ThemeGrid(theme, onTheme)
                        LookSettings(store)
                    }
                    "notifications" -> NotificationSettings(store)
                    "audio-levels" -> AudioLevelSettings(store, playback)
                    "api-mode" -> ApiModeSettings()
                    "widgets" -> WidgetSettings()
                    "immersive" -> ImmersiveSettingsDetail(immersiveEnabled, onImmersiveEnabledChange)
                    "playback" -> {
                        PlaybackDetail(store, playback, { onPage("audio-mode") },
                            { onPage("hardware-acceleration") })
                        Text(stringResource(R.string.settings_output_and_alerts), color = StonePalette.Yellow,
                            fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                        AudioOutputDetail()
                    }
                    // Framing and the icon row live together in one Vídeo section.
                    "video", "video-controls" -> {
                        VideoSettingsDetail(videoScale, onVideoScaleChange)
                        Text(stringResource(R.string.video_controls), color = StonePalette.Yellow,
                            fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                        VideoControlSettingsDetail(store, onVideoControlChange)
                    }
                    "audio-mode" -> AudioModeDetail(playback)
                    "hardware-acceleration" -> HardwareAccelerationDetail(playback)
                    "feedback" -> FeedbackDetail(shakeToReport, onShakeToReportChange)
                    "about" -> AboutDetail { onPage(it) }
                    "privacy", "terms", "licenses" -> LegalDocument(page)
                    "sleep" -> SleepDetail(playback, onClose = { onPage(null) })
                    "system-diagnostics", "audio-diagnostics" -> SystemDiagnosticsDetail(playback)
                }
            }
        }
    }
}

/** Pages opened from "Sobre Stone"; back returns there instead of the settings root. */
private val legalPages = setOf("privacy", "terms", "licenses")

@Composable internal fun themeLabel(theme: String) = stringResource(when (theme) {
    "light" -> R.string.theme_light; "gray" -> R.string.theme_gray
    "midnight" -> R.string.theme_midnight; "amoled" -> R.string.theme_amoled
    "coffee" -> R.string.theme_coffee; "golden" -> R.string.theme_golden
    "ocean" -> R.string.theme_ocean; "forest" -> R.string.theme_forest; "wine" -> R.string.theme_wine
    "violet" -> R.string.theme_violet; "slate" -> R.string.theme_slate; else -> R.string.theme_dark
})

@Composable private fun pageTitle(page: String) = stringResource(when (page) {
    "theme", "custom" -> R.string.personalization_title; "language" -> R.string.settings_language
    "notifications" -> R.string.settings_section_notifications
    "shortcuts" -> R.string.settings_home_shortcuts
    "immersive" -> R.string.immersive_screen
    "library-scan" -> R.string.device_scan
    "audio-levels" -> R.string.audio_levels_title
    "api-mode" -> R.string.api_title
    "widgets" -> R.string.widgets_title
    "playback" -> R.string.settings_playback; "audio-mode" -> R.string.audio_mode
    "video", "video-controls" -> R.string.video_settings
    "hardware-acceleration" -> R.string.hardware_acceleration
    "feedback" -> R.string.feedback_title; "about" -> R.string.about_stone
    "privacy" -> R.string.privacy_policy; "terms" -> R.string.terms_service
    "licenses" -> R.string.open_source_licenses
    "sleep" -> R.string.sleep_timer
    "system-diagnostics", "audio-diagnostics" -> R.string.system_diagnostics; else -> R.string.settings
})

@Composable internal fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int,
    value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).clickable(onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
        Text(stringResource(label), Modifier.weight(1f).padding(horizontal = 18.dp), fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold)
        if (value.isNotEmpty()) Text(value, color = StonePalette.Muted)
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable private fun SettingsRow(item: SettingsMenuItem) {
    Row(Modifier.fillMaxWidth().height(66.dp).clickable(onClick = item.action),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(item.icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
        Text(item.label, Modifier.weight(1f).padding(horizontal = 18.dp), fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold)
        if (item.value.isNotEmpty()) Text(item.value, color = StonePalette.Muted)
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}
