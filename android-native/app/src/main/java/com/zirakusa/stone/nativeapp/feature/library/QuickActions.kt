package com.zirakusa.stone.nativeapp.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class QuickActionDefinition(val id: String, val label: String, val description: String,
    val icon: ImageVector, val available: Boolean)

/** Single source of truth for home shortcuts. Unavailable actions stay out of the operational UI. */
object QuickActionRegistry {
    /** Actions that stay at the front of every preset profile. Custom can reorder or hide them. */
    val pinnedIds = listOf("sort")

    /** Actions that have a real destination or operation in the current app. */
    private val available = setOf(
        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi", "audio_mode_sleepy", "diagnostics",
        "shuffle_all", "favorites", "equalizer", "sleep_timer", "api_mode"
    )

    /** Default playback shortcuts; "sort" is added by LibraryScreen. */
    val preferredIds = listOf(
        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi", "audio_mode_sleepy"
    )
    val defaultVisibleIds = preferredIds.toSet()

    val definitions = listOf(
        QuickActionDefinition("audio_mode_battery", "Ahorro de batería",
            "Menor consumo y efectos reducidos", Icons.Default.BatteryFull, true),
        QuickActionDefinition("audio_mode_balanced", "Balanceado",
            "Ruta automática y equilibrio de calidad", Icons.Default.Balance, true),
        QuickActionDefinition("audio_mode_hifi", "Cine",
            "Calidad prioritaria y aceleración completa", Icons.Default.GraphicEq, true),
        QuickActionDefinition("audio_mode_sleepy", "Dormilón",
            "Volumen máximo al 10 % para dormir", Icons.Default.Bedtime, true),
        QuickActionDefinition("diagnostics", "Diagnóstico de rendimiento", "CPU, RAM y audio", Icons.Default.Memory, true),
        QuickActionDefinition("shuffle_all", "Mezclar todo", "Reproduce toda la música en orden aleatorio", Icons.Default.Shuffle, true),
        QuickActionDefinition("favorites", "Favoritos", "Abre tus canciones favoritas", Icons.Default.Favorite, true),
        QuickActionDefinition("equalizer", "Ecualizador", "Bandas y preajustes de sonido", Icons.Default.Tune, true),
        QuickActionDefinition("sleep_timer", "Temporizador", "Pausa la música al dormir", Icons.Default.Timer, true),
        QuickActionDefinition("api_mode", "Modo API", "Conecta un asistente de IA", Icons.Default.Hub, true),
    )
    val operational = definitions.filter { it.available && it.id in available }
}
