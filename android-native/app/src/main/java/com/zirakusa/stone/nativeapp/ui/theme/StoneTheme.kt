package com.zirakusa.stone.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StonePalette {
    var Background = Color(0xFF0A0A0E)
    var Surface = Color(0xFF15151B)
    val Yellow = Color(0xFFF5C518)
    val Blue = Color(0xFF339CFF)
    var Chip = Color(0xFF15151B)
    var Border = Color(0xFF22222B)
    val Secondary = Color(0xFFA0A0A5)
    val Muted = Color(0xFF8B8B92)
    val Hint = Color(0xFF6B6B72)
    val EmptyIcon = Color(0xFF2A2A35)
    val Purple = Color(0xFF3A2674)
    val PurpleText = Color(0xFFF5EEFF)
    val Cover = Color(0xFF1C1A24)
    val PlayingCover = Color(0xFF244AA8)
    val SongSubtitle = Color(0xFFAEA7B8)
    val MiniBackground = Color(0xF2443100)
    val MiniBorder = Color(0x6BF5C518)
    val MiniSubtitle = Color(0xFFE8C35D)
    /** Accent used by the background glow; follows the theme. */
    var Glow = Color(0xFFF5C518)
    /** Whether the app backdrop draws grain and glow. AMOLED keeps pure black pixels. */
    var Textured = true
}

/** Visual themes. Keys are persisted; keep them stable. */
enum class StoneThemeSpec(val key: String, val background: Color, val surface: Color, val chip: Color,
    val border: Color, val glow: Color, val textured: Boolean) {
    DARK("dark", Color(0xFF0A0A0E), Color(0xFF15151B), Color(0xFF15151B), Color(0xFF22222B), Color(0xFFF5C518), true),
    GRAY("gray", Color(0xFF24242A), Color(0xFF303038), Color(0xFF2C2C33), Color(0xFF3A3A44), Color(0xFFF5C518), true),
    MIDNIGHT("midnight", Color(0xFF070B16), Color(0xFF111A2E), Color(0xFF0F1729), Color(0xFF1E2A44), Color(0xFF339CFF), true),
    AMOLED("amoled", Color(0xFF000000), Color(0xFF0C0C0F), Color(0xFF0C0C0F), Color(0xFF1A1A20), Color(0xFFF5C518), false);

    companion object {
        val keys = entries.map { it.key }.toSet()
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: DARK
    }
}

@Composable fun StoneTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val spec = StoneThemeSpec.from(mode)
    StonePalette.Background = spec.background
    StonePalette.Surface = spec.surface
    StonePalette.Chip = spec.chip
    StonePalette.Border = spec.border
    StonePalette.Glow = spec.glow
    StonePalette.Textured = spec.textured
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFF5C518), onPrimary = Color(0xFF0A0A0E),
        background = spec.background, surface = spec.surface,
        surfaceContainerLow = spec.chip, surfaceContainer = spec.surface,
        surfaceContainerHigh = spec.surface, outline = spec.border, outlineVariant = spec.border,
        onBackground = Color.White, onSurface = Color.White,
        onSurfaceVariant = Color(0xFFA0A0A5), error = Color(0xFFFF4C4C),
    ), content = content)
}
