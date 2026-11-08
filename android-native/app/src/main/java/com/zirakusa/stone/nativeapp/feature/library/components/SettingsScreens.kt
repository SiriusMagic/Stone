package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import android.content.Intent
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.feature.player.SleepTimerContent
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.HardwareAcceleration
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.data.transfer.LocalTransferUiState
import com.zirakusa.stone.nativeapp.playback.PerformanceDiagnostics
import com.zirakusa.stone.nativeapp.feature.library.QuickActionDefinition
import com.zirakusa.stone.nativeapp.feature.library.QuickActionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private data class Language(val code: String, val label: String)
private val languages = listOf(Language("es", "Español"), Language("en", "English"))

private data class SettingsMenuItem(val icon: ImageVector, val label: String, val value: String,
    val keywords: String, val action: () -> Unit)
private data class SettingsMenuSection(val title: Int, val items: List<SettingsMenuItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsPanel(theme: String, onTheme: (String) -> Unit, language: String,
    onLanguage: (String) -> Unit, store: StoneSettingsStore?, page: String?,
    onPage: (String?) -> Unit, onClose: () -> Unit, onFolder: () -> Unit,
    onScan: () -> Unit, onLibrarySheet: (String) -> Unit, playback: PlaybackViewModel?,
    onExport: () -> Unit, onImport: () -> Unit, onShare: () -> Unit,
    onExportPackage: () -> Unit = {}, onImportPackage: () -> Unit = {}, onTransfer: () -> Unit = {},
    transferState: LocalTransferUiState = LocalTransferUiState(),
    onHostTransfer: () -> Unit = {}, onReceiveTransfer: (String, String) -> Unit = { _, _ -> },
    onStopTransfer: () -> Unit = {},
    onShortcutChange: (String, Boolean) -> Unit = { _, _ -> }) {
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = page != null, onBack = { onPage(null) })
    ModalBottomSheet(onDismissRequest = onClose, containerColor = StonePalette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.88f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
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
                        SettingsMenuItem(Icons.Default.Refresh, stringResource(R.string.settings_scan_library), "",
                            "biblioteca escanear actualizar archivos", { onClose(); onScan() }),
                        SettingsMenuItem(Icons.Default.Delete, stringResource(R.string.recently_deleted), "",
                            "biblioteca papelera eliminados restaurar", { onClose(); onLibrarySheet("deleted") })
                    )),
                    SettingsMenuSection(R.string.settings_section_playback, listOf(
                        SettingsMenuItem(Icons.Default.Headphones, stringResource(R.string.settings_playback), "",
                            "reproducción audio sonido modos salida diagnóstico", { onPage("playback") }),
                        SettingsMenuItem(Icons.Default.Speaker, stringResource(R.string.audio_output), "",
                            "reproducción audio salida altavoz bluetooth usb", { onPage("audio-output") }),
                        SettingsMenuItem(Icons.Default.Timer, stringResource(R.string.sleep_timer), "",
                            "reproducción temporizador dormir", { onPage("sleep") }),
                        SettingsMenuItem(Icons.Default.Notifications, stringResource(R.string.settings_notifications), "",
                            "reproducción notificaciones avisos", { onPage("notifications") })
                    )),
                    SettingsMenuSection(R.string.settings_section_system, listOf(
                        SettingsMenuItem(Icons.Default.Memory, stringResource(R.string.system_diagnostics), "",
                            "sistema diagnóstico avanzado cpu ram memoria batería temperatura", { onPage("system-diagnostics") })
                    )),
                    SettingsMenuSection(R.string.settings_section_appearance, listOf(
                        SettingsMenuItem(Icons.Default.Checkroom, stringResource(R.string.settings_theme), themeLabel(theme),
                            "personalización tema apariencia colores", { onPage("theme") }),
                        SettingsMenuItem(Icons.Default.Language, stringResource(R.string.settings_language),
                            languages.find { it.code == language }?.label.orEmpty(), "personalización idioma lenguaje", { onPage("language") }),
                        SettingsMenuItem(Icons.Default.Apps, stringResource(R.string.settings_home_shortcuts), "",
                            "personalización inicio iconos accesos rápidos barra", { onPage("shortcuts") })
                    )),
                    SettingsMenuSection(R.string.settings_section_about, listOf(
                        SettingsMenuItem(Icons.Default.ThumbUp, stringResource(R.string.feedback), "",
                            "ayuda opinión sugerencias", { onPage("feedback") }),
                        SettingsMenuItem(Icons.Default.Info, stringResource(R.string.about_stone), "",
                            "ayuda acerca versión información", { onPage("about") })
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
                    IconButton(onClick = { onPage(null) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                    Text(pageTitle(page), fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                when (page) {
                    "theme" -> listOf("gray" to R.string.theme_gray,
                        "dark" to R.string.theme_dark).forEach { (value, label) ->
                        SettingsChoice(label, theme == value) { onTheme(value) }
                    }
                    "language" -> languages.forEach { item ->
                        SettingsChoiceText(item.label, language == item.code) { onLanguage(item.code) }
                    }
                    "shortcuts" -> HomeShortcutDetail(store, onShortcutChange)
                    "playback" -> PlaybackDetail(store, playback, { onPage("audio-mode") },
                        { onPage("hardware-acceleration") })
                    "audio-mode" -> AudioModeDetail(playback)
                    "hardware-acceleration" -> HardwareAccelerationDetail(playback)
                    "audio-output" -> AudioOutputDetail()
                    "feedback" -> FeedbackDetail()
                    "about" -> AboutDetail()
                    "notifications" -> NotificationDetail(store)
                    "sleep" -> SleepDetail(playback, onClose = { onPage(null) })
                    "library-transfer" -> LibraryTransferDetail(onShare, transferState, onHostTransfer,
                        onReceiveTransfer, onStopTransfer, onExportPackage, onImportPackage)
                    "wifi-transfer" -> TransferDetail(transferState, onHostTransfer,
                        onReceiveTransfer, onStopTransfer)
                    "system-diagnostics", "audio-diagnostics" -> SystemDiagnosticsDetail(playback)
                }
            }
        }
    }
}

@Composable private fun themeLabel(theme: String) = stringResource(when (theme) {
    "light" -> R.string.theme_light; "gray" -> R.string.theme_gray; else -> R.string.theme_dark
})

@Composable private fun pageTitle(page: String) = stringResource(when (page) {
    "theme" -> R.string.settings_theme; "language" -> R.string.settings_language
    "shortcuts" -> R.string.settings_home_shortcuts
    "playback" -> R.string.settings_playback; "audio-mode" -> R.string.audio_mode
    "hardware-acceleration" -> R.string.hardware_acceleration
    "audio-output" -> R.string.audio_output
    "feedback" -> R.string.feedback; "about" -> R.string.about_stone
    "notifications" -> R.string.settings_notifications
    "sleep" -> R.string.sleep_timer; "wifi-transfer" -> R.string.wifi_transfer
    "library-transfer" -> R.string.transfer_library
    "system-diagnostics", "audio-diagnostics" -> R.string.system_diagnostics; else -> R.string.settings
})

@Composable private fun TransferDetail(state: LocalTransferUiState, host: () -> Unit,
    receive: (String, String) -> Unit, stop: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Text(stringResource(R.string.wifi_transfer_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 18.dp))
    Button(enabled = !state.busy, onClick = host, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start_transfer))
    }
    if (state.address.isNotBlank()) {
        Text(stringResource(R.string.transfer_share_address, state.address), fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.transfer_share_code, state.code), fontSize = 22.sp,
            fontWeight = FontWeight.Black, color = StonePalette.Yellow)
        Text(stringResource(R.string.transfer_share_url,
            "http://${state.address}/stone/${state.code}"), color = StonePalette.Yellow,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.transfer_waiting), color = StonePalette.Muted)
        TextButton(onClick = stop) { Text(stringResource(R.string.cancel)) }
    }
    HorizontalDivider(Modifier.padding(vertical = 18.dp))
    Text(stringResource(R.string.transfer_receive_title), fontWeight = FontWeight.Bold)
    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(stringResource(R.string.transfer_address)) }, singleLine = true,
        placeholder = { Text("192.168.1.20:43210") })
    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(stringResource(R.string.transfer_code)) }, singleLine = true)
    Button(enabled = !state.busy && address.isNotBlank() && code.length == 6,
        onClick = { receive(address, code) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.transfer_receive))
    }
    TransferProgress(state, stop)
}

@Composable private fun LibraryTransferDetail(bluetooth: () -> Unit, state: LocalTransferUiState,
    host: () -> Unit, receive: (String, String) -> Unit, stop: () -> Unit,
    exportPackage: () -> Unit, importPackage: () -> Unit) {
    var wifiSelected by remember { mutableStateOf(true) }
    SettingsGroup {
        SettingsRow(Icons.Default.Bluetooth, R.string.bluetooth_transfer, "") {
            wifiSelected = false
            bluetooth()
        }
        SettingsRow(Icons.Default.Wifi, R.string.wifi_transfer, "") {
            wifiSelected = true
        }
    }
    if (wifiSelected) TransferDetail(state, host, receive, stop)
    else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = exportPackage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Archive, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_musp))
            }
            OutlinedButton(onClick = importPackage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Unarchive, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.import_musp))
            }
        }
        TransferProgress(state, stop)
    }
}

@Composable private fun TransferProgress(state: LocalTransferUiState, cancel: (() -> Unit)? = null) {
    if (state.busy) {
        if (state.progress > 0f) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        Text(state.message.ifBlank { stringResource(R.string.transfer_working) },
            color = StonePalette.Muted)
        if (cancel != null) TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
    }
    if (!state.busy && state.message.isNotBlank()) {
        Text(state.message, color = StonePalette.Muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun HomeShortcutDetail(store: StoneSettingsStore?,
    onChange: (String, Boolean) -> Unit) {
    val legacy = listOf(
        QuickActionDefinition("search", "Buscar", "Buscar en la biblioteca", Icons.Default.Search, true),
        QuickActionDefinition("scan", "Escanear", "Actualizar biblioteca", Icons.Default.Refresh, true),
        QuickActionDefinition("sort", "Ordenar", "Ordenar biblioteca", Icons.Default.Sort, true),
        QuickActionDefinition("sleep", "Temporizador", "Temporizador para dormir", Icons.Default.Timer, true))
    val legacyIds = remember { legacy.map { it.id } }
    val defaultVisibleIds = legacyIds.toSet() + QuickActionRegistry.defaultVisibleIds
    val items = remember {
        legacy + QuickActionRegistry.preferredIds.mapNotNull { id ->
            QuickActionRegistry.definitions.find { it.id == id }
        } + QuickActionRegistry.definitions.filterNot { it.id in QuickActionRegistry.preferredIds }
    }
    var order by remember(store) { mutableStateOf(store?.shortcutOrder(items.map { it.id }) ?: items.map { it.id }) }
    var visible by remember(store) { mutableStateOf(items.associate { it.id to
        (store?.shortcutVisible(it.id, it.id in defaultVisibleIds) ?: (it.id in defaultVisibleIds)) }) }
    fun preserveCustomVisibility() {
        visible.forEach { (key, value) -> store?.setCustomShortcutVisible(key, value) }
    }
    fun enterCustomProfile() {
        if (store?.shortcutProfile() != "custom") {
            preserveCustomVisibility()
            store?.setShortcutProfile("custom")
        }
    }
    Text(stringResource(R.string.home_shortcut_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("minimal" to "Minimalista", "basic" to "Común",
            "audiophile" to "Audiófilo", "custom" to "Personalizado").forEach { (id, label) ->
            FilterChip(selected = store?.shortcutProfile() == id, onClick = {
                if (id != "custom" && store?.shortcutProfile() == "custom") preserveCustomVisibility()
                store?.setShortcutProfile(id)
                val enabled = when (id) {
                    "minimal" -> QuickActionRegistry.pinnedIds.toSet()
                    "basic" -> QuickActionRegistry.pinnedIds.toSet() + setOf("scan")
                    "audiophile" -> QuickActionRegistry.pinnedIds.toSet() + setOf(
                        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi",
                        "diagnostics")
                    "custom" -> items.filter { item ->
                        store?.customShortcutVisible(item.id, visible[item.id] == true)
                            ?: (visible[item.id] == true)
                    }.map { it.id }.toSet()
                    else -> emptySet()
                }
                visible = visible.mapValues { it.key in enabled }
                visible.forEach { (key, value) ->
                    store?.setShortcutVisible(key, value)
                    if (id == "custom") store?.setCustomShortcutVisible(key, value)
                    onChange(key, value)
                }
            }, label = { Text(label) })
        }
    }
    OutlinedButton(onClick = {
        store?.setShortcutProfile("custom")
        order = items.map { it.id }; store?.setShortcutOrder(order)
        visible = items.associate { it.id to (it.id in defaultVisibleIds) }
        visible.forEach { (key, value) ->
            store?.setShortcutVisible(key, value)
            store?.setCustomShortcutVisible(key, value)
            onChange(key, value)
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_defaults)) }
    order.forEachIndexed { index, key ->
        val item = items.find { it.id == key } ?: return@forEachIndexed
        fun moveShortcut(direction: Int) {
            enterCustomProfile()
            val currentIndex = order.indexOf(key)
            val targetIndex = currentIndex + direction
            if (currentIndex !in order.indices || targetIndex !in order.indices) return
            order = order.toMutableList().also {
                it.add(targetIndex, it.removeAt(currentIndex))
            }
            store?.setShortcutOrder(order)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, item.label, tint = StonePalette.Yellow)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.label, fontWeight = FontWeight.SemiBold)
                Text(if (item.available) item.description else "No disponible en esta versión", color = StonePalette.Muted, fontSize = 12.sp)
            }
            RepeatingReorderButton(index > 0, Icons.Default.KeyboardArrowUp, "Subir") {
                moveShortcut(-1)
            }
            RepeatingReorderButton(index < order.lastIndex, Icons.Default.KeyboardArrowDown, "Bajar") {
                moveShortcut(1)
            }
            Switch(checked = visible[key] == true, enabled = item.available, onCheckedChange = { enabled ->
                enterCustomProfile()
                store?.setCustomShortcutVisible(key, enabled)
                store?.setShortcutVisible(key, enabled)
                visible = visible + (key to enabled)
                onChange(key, enabled)
            })
        }
    }
}

/** A tap moves once; holding the control keeps moving the same shortcut. */
@Composable private fun RepeatingReorderButton(enabled: Boolean, icon: ImageVector,
    description: String, onMove: () -> Unit) {
    val latestMove by rememberUpdatedState(onMove)
    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else .35f)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    if (!enabled) return@onClick false
                    latestMove()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val gestureScope = CoroutineScope(coroutineContext)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var repeated = false
                    val repeatJob = gestureScope.launch {
                        delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
                        repeated = true
                        latestMove()
                        while (isActive) {
                            delay(120)
                            latestMove()
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeatJob.cancel()
                    if (!repeated && up != null) latestMove()
                    if (repeated) up?.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FeedbackDetail() {
    var selected by remember { mutableStateOf(R.string.feedback_other) }
    var details by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        attachments = it.take(4)
    }
    val categories = listOf(R.string.feedback_lyrics, R.string.feedback_ads,
        R.string.feedback_missing_file, R.string.feedback_unplayable,
        R.string.feedback_stops, R.string.feedback_slow, R.string.feedback_other)
    Text(stringResource(R.string.feedback_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            FilterChip(selected = selected == category, onClick = { selected = category },
                label = { Text(stringResource(category)) })
        }
    }
    Text(stringResource(R.string.feedback_details), fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    OutlinedTextField(value = details, onValueChange = { details = it.take(500) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5,
        supportingText = { Text("${details.length}/500") },
        placeholder = { Text(stringResource(R.string.feedback_details_hint)) })
    Text(stringResource(R.string.feedback_attachments), fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.size(82.dp)) {
        Text("+", fontSize = 28.sp)
    }
    if (attachments.isNotEmpty()) Text(stringResource(R.string.feedback_attachment_count, attachments.size),
        color = StonePalette.Muted, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = consent, onCheckedChange = { consent = it })
        Text(stringResource(R.string.feedback_consent), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
    Button(onClick = { }, enabled = details.isNotBlank() && consent,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.feedback_submit))
    }
}

@Composable private fun AboutDetail() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        androidx.compose.foundation.Image(painterResource(R.drawable.stone_penguin), stringResource(R.string.stone),
            Modifier.size(112.dp), contentScale = ContentScale.Fit)
        Text(stringResource(R.string.stone), fontSize = 24.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), color = StonePalette.Muted,
            modifier = Modifier.padding(top = 10.dp))
        SettingsGroup {
            SettingsRow(Icons.Default.ThumbUp, R.string.rate_stone, "") { }
        }
        SettingsGroup {
            SettingsRow(Icons.Default.Policy, R.string.privacy_policy, "") { }
            SettingsRow(Icons.Default.Description, R.string.terms_service, "") { }
            SettingsRow(Icons.Default.Verified, R.string.open_source_licenses, "") { }
        }
        Text(stringResource(R.string.copyright_stone), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
    }
}

@Composable private fun AudioOutputDetail() {
    val context = LocalContext.current
    val route = remember(context) {
        val manager = context.getSystemService(android.media.AudioManager::class.java)
        val device = manager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull()
        device?.productName?.toString()?.takeIf { it.isNotBlank() } ?: when (device?.type) {
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz del teléfono"
            else -> context.getString(R.string.not_available)
        }
    }
    Text(stringResource(R.string.audio_output_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    SettingsGroup {
        SettingsRow(Icons.Default.Speaker, R.string.audio_output_current, route) {}
    }
    Button(onClick = {
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
    }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Icon(Icons.Default.Settings, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.audio_output_open_settings))
    }
}

@Composable private fun SettingsChoice(label: Int, selected: Boolean, action: () -> Unit) =
    SettingsChoiceText(stringResource(label), selected, action)

@Composable private fun SettingsChoiceText(label: String, selected: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable(onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (selected) StonePalette.Yellow else StonePalette.Muted)
    }
}

@Composable private fun PlaybackDetail(store: StoneSettingsStore?, playback: PlaybackViewModel?,
    openAudioMode: () -> Unit, openHardwareAcceleration: () -> Unit) {
    var config by remember { mutableStateOf(store?.read()) }
    val playbackState = playback?.state?.collectAsState()?.value
    Text(stringResource(R.string.playback_section_mode), color = StonePalette.Yellow,
        fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    SettingsGroup {
        PlaybackModeRow(playbackState?.audioMode ?: AudioMode.BALANCED, openAudioMode)
        HardwareAccelerationRow(playbackState?.hardwareAcceleration ?: HardwareAcceleration.AUTOMATIC,
            openHardwareAcceleration)
    }
    Text(stringResource(R.string.playback_section_behavior), color = StonePalette.Yellow,
        fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    SettingsGroup {
        ToggleSetting(R.string.play_with_others, R.string.play_with_others_hint, config?.playWithOthers == true) {
            playback?.setPlayWithOthers(it); config = store?.read()
        }
        Text(stringResource(R.string.gapless), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.gapless_hint), color = StonePalette.Muted)
        Text(stringResource(R.string.crossfade), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.crossfade_hint), color = StonePalette.Muted)
    }
}

@Composable private fun SystemDiagnosticsDetail(playback: PlaybackViewModel?) {
    val context = LocalContext.current
    val mode = playback?.state?.collectAsState()?.value?.audioMode ?: AudioMode.BALANCED
    var snapshot by remember { mutableStateOf(PerformanceDiagnostics.read(context)) }
    var samples by remember { mutableStateOf<List<com.zirakusa.stone.nativeapp.playback.PerformanceSnapshot>>(emptyList()) }
    var summary by remember { mutableStateOf<com.zirakusa.stone.nativeapp.playback.DiagnosticSessionSummary?>(null) }
    var running by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(30_000L) }
    val scope = rememberCoroutineScope()
    val status = diagnosticStatus(snapshot)
    val statusColor = when (status) {
        "Crítico" -> Color(0xFFFF6B6B)
        "Atención" -> StonePalette.Yellow
        else -> Color(0xFF55D6BE)
    }
    val modeLabel = when (mode) {
        AudioMode.BATTERY -> stringResource(R.string.audio_mode_battery)
        AudioMode.BALANCED -> stringResource(R.string.audio_mode_balanced)
        AudioMode.HIFI -> stringResource(R.string.audio_mode_hifi)
    }
    Text(stringResource(R.string.system_diagnostics_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 14.dp))
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = StonePalette.Chip) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = StonePalette.Yellow, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(stringResource(R.string.diagnostics_system_status), fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.audio_diagnostics_intro), color = StonePalette.Muted,
                        fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = .16f)) {
                    Text(status, color = statusColor, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiagnosticMetricCard(Icons.Default.Speed, stringResource(R.string.cpu_stone),
            snapshot.cpuPercent?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
        DiagnosticMetricCard(Icons.Default.Memory, stringResource(R.string.pss_total),
            snapshot.pssTotalMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "—", Modifier.weight(1f))
        DiagnosticMetricCard(Icons.Default.BatteryFull, stringResource(R.string.battery_level),
            snapshot.batteryPercent.takeIf { it >= 0 }?.let { "$it%" } ?: "—", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !running, onClick = { snapshot = PerformanceDiagnostics.read(context) },
            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.refresh_metrics)) }
        OutlinedButton(enabled = !running, onClick = {
            running = true; samples = emptyList()
            scope.launch {
                summary = PerformanceDiagnostics.session(context, duration) { sample ->
                    snapshot = sample; samples = samples + sample
                }
                running = false
            }
        }, modifier = Modifier.weight(1f)) {
            Text(if (running) stringResource(R.string.diagnostics_running) else stringResource(R.string.start_diagnostics))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30_000L to "30 s", 60_000L to "1 min", 300_000L to "5 min").forEach { (value, label) ->
            FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) })
        }
    }
    summary?.let { result ->
        OutlinedButton(onClick = { com.zirakusa.stone.nativeapp.playback.DiagnosticsReportExporter.share(context, result) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_diagnostics))
        }
    }
    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
    DiagnosticSection(stringResource(R.string.diagnostics_summary), initiallyOpen = true) {
        DiagnosticValue(stringResource(R.string.audio_mode), modeLabel)
        DiagnosticValue(stringResource(R.string.diagnostic_status), status)
        DiagnosticValue(stringResource(R.string.device_ram_used), "${snapshot.usedRamMb} MB")
        DiagnosticValue(stringResource(R.string.battery_level), if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "No disponible")
    }
    DiagnosticSection(stringResource(R.string.stone_consumption), initiallyOpen = true) {
        Text(stringResource(R.string.stone_consumption_hint), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        DiagnosticValue(stringResource(R.string.cpu_stone), snapshot.cpuPercent?.let { "%.1f%%".format(it) } ?: "Calculando")
        DiagnosticValue(stringResource(R.string.pss_total), snapshot.pssTotalMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.pss_native), snapshot.pssNativeMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.pss_dalvik), snapshot.pssDalvikMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.process_heap), "${snapshot.processHeapMb} MB")
        DiagnosticValue(stringResource(R.string.native_heap), snapshot.nativeHeapMb.takeIf { it >= 0 }?.let { "$it MB" } ?: "No disponible")
        DiagnosticValue(stringResource(R.string.cpu_threads), snapshot.threadCount.takeIf { it >= 0 }?.toString() ?: "No disponible")
    }
    DiagnosticSection(stringResource(R.string.diagnostics_device)) {
        val model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        DiagnosticValue(stringResource(R.string.diagnostics_device_model), model)
        DiagnosticValue(stringResource(R.string.diagnostics_android), android.os.Build.VERSION.RELEASE)
        DiagnosticValue(stringResource(R.string.diagnostics_api_level), android.os.Build.VERSION.SDK_INT.toString())
    }
    DiagnosticSection(stringResource(R.string.device_memory)) {
        DiagnosticValue(stringResource(R.string.device_ram_used), "${snapshot.usedRamMb} MB")
        DiagnosticValue(stringResource(R.string.ram_available), "${snapshot.availableRamMb} MB")
        DiagnosticValue(stringResource(R.string.low_memory), snapshot.lowMemory.toString())
    }
    DiagnosticSection(stringResource(R.string.diagnostics_audio)) {
        DiagnosticValue(stringResource(R.string.audio_route), snapshot.audioRoute)
        DiagnosticValue(stringResource(R.string.audio_errors), snapshot.errorCount.toString())
        DiagnosticValue(stringResource(R.string.audio_latency), stringResource(R.string.not_available))
    }
    DiagnosticSection(stringResource(R.string.diagnostics_energy)) {
        DiagnosticValue(stringResource(R.string.battery_temperature), snapshot.batteryTemperatureC?.let { "$it °C" } ?: stringResource(R.string.not_available))
        DiagnosticValue(stringResource(R.string.charging_state), if (snapshot.charging) stringResource(R.string.charging) else stringResource(R.string.not_charging))
        DiagnosticValue(stringResource(R.string.thermal_status), snapshot.thermalStatus)
    }
    DiagnosticSection(stringResource(R.string.diagnostics_advanced)) {
        DiagnosticValue(stringResource(R.string.low_memory), snapshot.lowMemory.toString())
        DiagnosticValue(stringResource(R.string.low_memory_threshold), "${snapshot.lowMemoryThresholdMb} MB")
        DiagnosticValue(stringResource(R.string.session_samples), samples.size.toString())
        Text(stringResource(R.string.diagnostics_limitations), color = StonePalette.Muted, fontSize = 12.sp)
    }
}

@Composable private fun DiagnosticMetricCard(icon: ImageVector, title: String, value: String,
    modifier: Modifier = Modifier) {
    Surface(modifier.height(92.dp), shape = RoundedCornerShape(18.dp), color = StonePalette.Chip) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(20.dp))
            Text(title, color = StonePalette.Muted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun DiagnosticSection(title: String, initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) Column(content = content)
    }
}

private fun diagnosticStatus(snapshot: com.zirakusa.stone.nativeapp.playback.PerformanceSnapshot): String = when {
    snapshot.thermalStatus in setOf("Grave", "Crítico", "Emergencia") -> "Crítico"
    snapshot.lowMemory || (snapshot.cpuPercent ?: 0.0) > 85 -> "Atención"
    else -> "Normal"
}

@Composable private fun DiagnosticValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = StonePalette.Muted)
    }
}

@Composable private fun HardwareAccelerationRow(mode: HardwareAcceleration, open: () -> Unit) {
    val label = stringResource(when (mode) {
        HardwareAcceleration.AUTOMATIC -> R.string.accel_automatic
        HardwareAcceleration.DISABLED -> R.string.accel_disabled
        HardwareAcceleration.DECODING -> R.string.accel_decoding
        HardwareAcceleration.FULL -> R.string.accel_full
        HardwareAcceleration.ADAPTIVE -> R.string.accel_adaptive
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.hardware_acceleration), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = StonePalette.Muted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable private fun PlaybackModeRow(mode: AudioMode, open: () -> Unit) {
    val label = stringResource(when (mode) {
        AudioMode.BATTERY -> R.string.audio_mode_battery
        AudioMode.BALANCED -> R.string.audio_mode_balanced
        AudioMode.HIFI -> R.string.audio_mode_hifi
    })
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = open),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (mode == AudioMode.BATTERY) Icons.Default.BatteryFull else Icons.Default.Tune,
            null, tint = StonePalette.Yellow, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.audio_mode), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = StonePalette.Muted, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
    }
}

@Composable private fun AudioModeDetail(playback: PlaybackViewModel?) {
    val current = playback?.state?.collectAsState()?.value?.audioMode ?: AudioMode.BALANCED
    Text(stringResource(R.string.audio_mode_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    AudioMode.entries.forEach { mode ->
        val (title, detail, icon) = when (mode) {
            AudioMode.BATTERY -> Triple(R.string.audio_mode_battery, R.string.audio_mode_battery_info,
                Icons.Default.BatteryFull)
            AudioMode.BALANCED -> Triple(R.string.audio_mode_balanced, R.string.audio_mode_balanced_info,
                Icons.Default.Balance)
            AudioMode.HIFI -> Triple(R.string.audio_mode_hifi, R.string.audio_mode_hifi_info,
                Icons.Default.GraphicEq)
        }
            Row(Modifier.fillMaxWidth().clickable {
                if (mode == AudioMode.BATTERY) playback?.setPlaybackProfile(
                    AudioMode.BATTERY, HardwareAcceleration.ADAPTIVE)
                else playback?.setAudioMode(mode)
            }
            .padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(detail), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Icon(if (current == mode) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (current == mode) StonePalette.Yellow else StonePalette.Muted)
        }
    }
}

@Composable private fun HardwareAccelerationDetail(playback: PlaybackViewModel?) {
    Text(stringResource(R.string.hardware_acceleration_intro), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 10.dp))
    val current = playback?.state?.collectAsState()?.value?.hardwareAcceleration
        ?: HardwareAcceleration.AUTOMATIC
    HardwareAcceleration.entries.forEach { mode ->
        val (title, detail, icon) = when (mode) {
            HardwareAcceleration.AUTOMATIC -> Triple(R.string.accel_automatic, R.string.accel_automatic_info, Icons.Default.Settings)
            HardwareAcceleration.DISABLED -> Triple(R.string.accel_disabled, R.string.accel_disabled_info, Icons.Default.Block)
            HardwareAcceleration.DECODING -> Triple(R.string.accel_decoding, R.string.accel_decoding_info, Icons.Default.Memory)
            HardwareAcceleration.FULL -> Triple(R.string.accel_full, R.string.accel_full_info, Icons.Default.Speed)
            HardwareAcceleration.ADAPTIVE -> Triple(R.string.accel_adaptive, R.string.accel_adaptive_info, Icons.Default.BatteryFull)
        }
        Row(Modifier.fillMaxWidth().clickable { playback?.setHardwareAcceleration(mode) }
            .padding(vertical = 13.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                Text(stringResource(detail), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Icon(if (current == mode) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null, tint = if (current == mode) StonePalette.Yellow else StonePalette.Muted)
        }
    }
    Text(stringResource(R.string.hardware_acceleration_note), color = StonePalette.Muted,
        fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
}

@Composable private fun NotificationDetail(store: StoneSettingsStore?) {
    var reminders by remember(store) { mutableStateOf(store?.read()?.musicReminders ?: false) }
    var newFiles by remember(store) { mutableStateOf(store?.read()?.notifyNewFiles ?: false) }
    var push by remember(store) { mutableStateOf(store?.read()?.pushNotifications ?: false) }
    ToggleSetting(R.string.music_reminders, R.string.music_reminders_hint, reminders) { enabled ->
        store?.setBoolean("musicReminders", enabled)
        reminders = enabled
    }
    if (reminders) {
        Text(stringResource(R.string.morning_reminder), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.morning_reminder_time), color = StonePalette.Muted)
        Text(stringResource(R.string.evening_reminder), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.evening_reminder_time), color = StonePalette.Muted)
        Text(stringResource(R.string.reminder_play_choice), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp))
        Text(stringResource(R.string.reminder_not_implemented), color = StonePalette.Muted,
            fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
    }
    ToggleSetting(R.string.notify_new_files, R.string.notify_new_files_hint, newFiles) { enabled ->
        store?.setBoolean("notifyNewFiles", enabled); newFiles = enabled
    }
    Text(stringResource(R.string.notifications_unavailable), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 18.dp))
    ToggleSetting(R.string.push_notifications, R.string.push_notifications_hint, push) { enabled ->
        store?.setBoolean("pushNotifications", enabled); push = enabled
    }
    Text(stringResource(R.string.push_notifications_hint), color = StonePalette.Muted)
}

@Composable private fun SleepDetail(playback: PlaybackViewModel?, onClose: () -> Unit) {
    val playbackState = playback?.state?.collectAsState()?.value
    if (playback != null && playbackState != null) SleepTimerContent(playbackState, playback, onClose)
}

@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), content = content)
}

@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int,
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

@Composable private fun ToggleSetting(title: Int, subtitle: Int, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(subtitle), color = StonePalette.Muted, fontSize = 13.sp)
        }
        Switch(value, change)
    }
}
