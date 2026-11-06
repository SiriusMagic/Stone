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
        "audio_mode_balanced", "audio_mode_hifi", "cover_editor", "diagnostics"
    )

    /** Default playback shortcuts; search and legacy actions are added by LibraryScreen. */
    val preferredIds = listOf(
        "audio_mode_balanced", "audio_mode_hifi", "cover_editor"
    )
    val defaultVisibleIds = preferredIds.toSet()

    val definitions = listOf(
        QuickActionDefinition("audio_mode_balanced", "Balanceado",
            "Ruta automática y equilibrio de calidad", Icons.Default.Balance, true),
        QuickActionDefinition("audio_mode_hifi", "Cine",
            "Calidad prioritaria y aceleración completa", Icons.Default.GraphicEq, true),
        QuickActionDefinition("cover_editor", "Editor de portadas",
            "Ver la portada de la canción actual", Icons.Default.Image, true),
        QuickActionDefinition("diagnostics", "Diagnóstico de rendimiento", "CPU, RAM y audio", Icons.Default.Memory, true),
    )
    val operational = definitions.filter { it.available && it.id in available }
}
