package com.zirakusa.stone.nativeapp.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class QuickActionDefinition(val id: String, val label: String, val description: String,
    val icon: ImageVector, val available: Boolean)

/** Single source of truth for home shortcuts. Unavailable actions stay out of the operational UI. */
object QuickActionRegistry {
    /** Actions that have a real destination or operation in the current app. */
    private val available = setOf(
        "playlists", "favorites", "history", "shuffle", "repeat", "lyrics", "albums", "artists",
        "audio_mode", "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi",
        "hardware_acceleration",
        "hardware_accel_automatic", "hardware_accel_disabled", "hardware_accel_decoding",
        "hardware_accel_full", "hardware_accel_adaptive",
        "playback_settings", "appearance", "battery_saver"
    )

    /** New playback shortcuts shown by default without changing existing user choices. */
    val preferredIds = listOf(
        "battery_saver", "playback_settings", "shuffle", "repeat",
        "audio_mode_battery", "audio_mode_balanced", "audio_mode_hifi",
        "hardware_acceleration"
    )
    val defaultVisibleIds = preferredIds.toSet()

    val definitions = listOf(
        QuickActionDefinition("playlists", "Listas de reproducción", "Abrir tus listas", Icons.Default.QueueMusic, true),
        QuickActionDefinition("favorites", "Favoritos", "Canciones favoritas", Icons.Default.Favorite, true),
        QuickActionDefinition("history", "Historial", "Reproducciones recientes", Icons.Default.History, true),
        QuickActionDefinition("shuffle", "Reproducción aleatoria", "Alternar aleatorio", Icons.Default.Shuffle, true),
        QuickActionDefinition("repeat", "Repetición", "Cambiar repetición", Icons.Default.Repeat, true),
        QuickActionDefinition("lyrics", "Letras sincronizadas", "Abrir canciones con letras", Icons.Default.MusicNote, true),
        QuickActionDefinition("albums", "Álbumes", "Explorar álbumes", Icons.Default.Album, true),
        QuickActionDefinition("artists", "Artistas", "Explorar artistas", Icons.Default.Person, true),
        QuickActionDefinition("audio_mode", "Modo de reproducción", "Batería, equilibrado o alta fidelidad", Icons.Default.Tune, true),
        QuickActionDefinition("audio_mode_battery", "Larga duración de batería",
            "Usar la ruta de audio de menor consumo", Icons.Default.BatteryFull, true),
        QuickActionDefinition("audio_mode_balanced", "Equilibrado",
            "Equilibrio entre calidad y autonomía", Icons.Default.Balance, true),
        QuickActionDefinition("audio_mode_hifi", "Alta fidelidad",
            "Priorizar calidad y procesamiento de audio", Icons.Default.GraphicEq, true),
        QuickActionDefinition("hardware_acceleration", "Aceleración de hardware",
            "Elegir un modo compatible con el dispositivo", Icons.Default.Memory, true),
        QuickActionDefinition("hardware_accel_automatic", "Aceleración automática",
            "Usar la mejor ruta disponible", Icons.Default.Settings, true),
        QuickActionDefinition("hardware_accel_disabled", "Aceleración desactivada",
            "No solicitar aceleración adicional", Icons.Default.Block, true),
        QuickActionDefinition("hardware_accel_decoding", "Decodificación por hardware",
            "Preferir decodificación compatible", Icons.Default.Memory, true),
        QuickActionDefinition("hardware_accel_full", "Aceleración completa",
            "Priorizar rendimiento y offload", Icons.Default.Speed, true),
        QuickActionDefinition("hardware_accel_adaptive", "Aceleración adaptativa",
            "Equilibrar autonomía y estabilidad", Icons.Default.Tune, true),
        QuickActionDefinition("playback_settings", "Ajustes de reproducción", "Modos, salida y diagnóstico", Icons.Default.Headphones, true),
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
