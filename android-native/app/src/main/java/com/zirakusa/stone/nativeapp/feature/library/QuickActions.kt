package com.zirakusa.stone.nativeapp.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class QuickActionDefinition(val id: String, val label: String, val description: String,
    val icon: ImageVector, val available: Boolean)

/** Single source of truth for home shortcuts. Unavailable actions stay out of the operational UI. */
object QuickActionRegistry {
    private val available = setOf("playlists", "favorites", "history", "lyrics", "albums", "artists", "appearance")
    val definitions = listOf(
        QuickActionDefinition("playlists", "Listas de reproducción", "Abrir tus listas", Icons.Default.QueueMusic, true),
        QuickActionDefinition("favorites", "Favoritos", "Canciones favoritas", Icons.Default.Favorite, true),
        QuickActionDefinition("history", "Historial", "Reproducciones recientes", Icons.Default.History, true),
        QuickActionDefinition("shuffle", "Reproducción aleatoria", "Alternar aleatorio", Icons.Default.Shuffle, false),
        QuickActionDefinition("repeat", "Repetición", "Cambiar repetición", Icons.Default.Repeat, false),
        QuickActionDefinition("lyrics", "Letras sincronizadas", "Abrir canciones con letras", Icons.Default.MusicNote, true),
        QuickActionDefinition("albums", "Álbumes", "Explorar álbumes", Icons.Default.Album, true),
        QuickActionDefinition("artists", "Artistas", "Explorar artistas", Icons.Default.Person, true),
        QuickActionDefinition("tag_editor", "Editor de etiquetas", "Editar metadatos", Icons.Default.Edit, true),
        QuickActionDefinition("cover_editor", "Editor de portadas", "Cambiar portadas", Icons.Default.Image, false),
        QuickActionDefinition("visualizer", "Visualizador de audio", "Visualización en tiempo real", Icons.Default.GraphicEq, false),
        QuickActionDefinition("audio_effects", "Efectos de sonido", "Procesamiento DSP", Icons.Default.Tune, false),
        QuickActionDefinition("diagnostics", "Diagnóstico de rendimiento", "CPU, RAM y audio", Icons.Default.Memory, true),
        QuickActionDefinition("spectrum", "Analizador de espectro", "Análisis de frecuencias", Icons.Default.Equalizer, false),
        QuickActionDefinition("statistics", "Estadísticas musicales", "Resumen de escucha", Icons.Default.BarChart, false),
        QuickActionDefinition("storage", "Administrador de almacenamiento", "Espacio de la biblioteca", Icons.Default.Storage, false),
        QuickActionDefinition("duplicates", "Detector de duplicados", "Buscar archivos repetidos", Icons.Default.FileCopy, false),
        QuickActionDefinition("audio_info", "Información técnica del audio", "Datos del archivo", Icons.Default.AudioFile, false),
        QuickActionDefinition("converter", "Convertidor de formatos", "Conversión de audio", Icons.Default.Autorenew, false),
        QuickActionDefinition("bluetooth", "Dispositivos Bluetooth", "Transferir por Bluetooth", Icons.Default.Bluetooth, false),
        QuickActionDefinition("audio_output", "Salida de audio", "Elegir salida", Icons.Default.Speaker, false),
        QuickActionDefinition("radio", "Radio musical", "Radio en línea", Icons.Default.Radio, false),
        QuickActionDefinition("karaoke", "Karaoke", "Modo karaoke", Icons.Default.Mic, false),
        QuickActionDefinition("recommendations", "Recomendaciones inteligentes", "Sugerencias musicales", Icons.Default.Psychology, false),
        QuickActionDefinition("queue", "Cola de reproducción", "Abrir A continuación", Icons.Default.QueueMusic, true),
        QuickActionDefinition("bookmarks", "Marcadores musicales", "Canciones guardadas", Icons.Default.Bookmark, false),
        QuickActionDefinition("song_identifier", "Identificador de canciones", "Identificar una canción", Icons.Default.Search, false),
        QuickActionDefinition("integrity", "Verificador de integridad", "Comprobar archivos", Icons.Default.VerifiedUser, false),
        QuickActionDefinition("appearance", "Personalización visual", "Tema de Stone", Icons.Default.Palette, true),
        QuickActionDefinition("battery_saver", "Modo ahorro energético", "Reproducción eficiente", Icons.Default.BatterySaver, true)
    )
    val operational = definitions.filter { it.available && it.id in available }
}
