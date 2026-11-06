package com.zirakusa.stone.nativeapp.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class QuickActionDefinition(val id: String, val label: String, val description: String,
    val icon: ImageVector, val available: Boolean)

/** Single source of truth for home shortcuts. Unavailable actions stay out of the operational UI. */
object QuickActionRegistry {
    /** Actions that stay at the front of every preset profile. Custom can reorder or hide them. */
    val pinnedIds = listOf("search", "sort")

    /** Actions that have a real destination or operation in the current app. */
    private val available = setOf(
        "playlists", "favorites", "history", "shuffle", "repeat",
        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi",
        "hardware_acceleration", "diagnostics"
    )

    /** Default playback shortcuts; search and legacy actions are added by LibraryScreen. */
    val preferredIds = listOf(
        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi", "hardware_acceleration"
    )
    val defaultVisibleIds = preferredIds.toSet()

    val definitions = listOf(
        QuickActionDefinition("playlists", "Listas de reproducción", "Abrir tus listas", Icons.Default.QueueMusic, true),
        QuickActionDefinition("favorites", "Favoritos", "Canciones favoritas", Icons.Default.Favorite, true),
        QuickActionDefinition("history", "Historial", "Reproducciones recientes", Icons.Default.History, true),
        QuickActionDefinition("shuffle", "Reproducción aleatoria", "Alternar aleatorio", Icons.Default.Shuffle, true),
        QuickActionDefinition("repeat", "Repetición", "Cambiar repetición", Icons.Default.Repeat, true),
        QuickActionDefinition("audio_mode_battery", "Ultra ahorro de energía",
            "Modo de bajo consumo y aceleración adaptativa", Icons.Default.BatteryFull, true),
        QuickActionDefinition("audio_mode_balanced", "Balanceado",
            "Ruta automática y equilibrio de calidad", Icons.Default.Balance, true),
        QuickActionDefinition("audio_mode_hifi", "Cine",
            "Calidad prioritaria y aceleración completa", Icons.Default.GraphicEq, true),
        QuickActionDefinition("hardware_acceleration", "Aceleración de hardware",
            "Elegir un modo compatible con el dispositivo", Icons.Default.Memory, true),
        QuickActionDefinition("diagnostics", "Diagnóstico de rendimiento", "CPU, RAM y audio", Icons.Default.Memory, true),
        // Kept at the end as a future storage-only feature; it is not operational in this version.
        QuickActionDefinition("storage", "Administrador de almacenamiento", "Próximamente: espacio y archivos de música", Icons.Default.Storage, false)
    )
    val operational = definitions.filter { it.available && it.id in available }
}
