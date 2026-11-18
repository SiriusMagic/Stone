package com.zirakusa.stone.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

object StonePalette {
    var Background by mutableStateOf(Color(0xFF0A0A0E))
    var Surface by mutableStateOf(Color(0xFF15151B))
    /** Accent ("dorado" by default); follows Personalizado > color de acento. */
    var Yellow by mutableStateOf(Color(0xFFF5C518))
    /** Text and icon colours chosen in Personalizado. */
    var Text by mutableStateOf(Color.White)
    var IconTint by mutableStateOf(Color.White)
    val Blue = Color(0xFF339CFF)
    var Chip by mutableStateOf(Color(0xFF15151B))
    var Border by mutableStateOf(Color(0xFF22222B))
    val Secondary = Color(0xFFA0A0A5)
    val Muted = Color(0xFF8B8B92)
    val Hint = Color(0xFF6B6B72)
    val EmptyIcon = Color(0xFF2A2A35)
    val Purple = Color(0xFF3A2674)
    val Cover = Color(0xFF1C1A24)
    val PlayingCover = Color(0xFF244AA8)
    val SongSubtitle = Color(0xFFAEA7B8)
    var MiniBackground by mutableStateOf(Color(0xF2443100))
    var MiniBorder by mutableStateOf(Color(0x6BF5C518))
    var MiniSubtitle by mutableStateOf(Color(0xFFE8C35D))
    /** Accent used by the background glow; follows the theme. */
    var Glow by mutableStateOf(Color(0xFFF5C518))
    /** Whether the app backdrop draws grain and glow. AMOLED keeps pure black pixels. */
    var Textured = true
}

/** Visual themes. Keys are persisted; keep them stable. */
enum class StoneThemeSpec(val key: String, val background: Color, val surface: Color, val chip: Color,
    val border: Color, val glow: Color, val textured: Boolean) {
    DARK("dark", Color(0xFF0A0A0E), Color(0xFF15151B), Color(0xFF15151B), Color(0xFF22222B), Color(0xFF8E9AB8), true),
    GRAY("gray", Color(0xFF24242A), Color(0xFF303038), Color(0xFF2C2C33), Color(0xFF3A3A44), Color(0xFFB8BCC8), true),
    MIDNIGHT("midnight", Color(0xFF070B16), Color(0xFF111A2E), Color(0xFF0F1729), Color(0xFF1E2A44), Color(0xFF339CFF), true),
    AMOLED("amoled", Color(0xFF000000), Color(0xFF0C0C0F), Color(0xFF0C0C0F), Color(0xFF1A1A20), Color(0xFFF5C518), false),
    COFFEE("coffee", Color(0xFF1A120C), Color(0xFF2A1E15), Color(0xFF261B13), Color(0xFF3B2B1F), Color(0xFFC89B6D), true),
    GOLDEN("golden", Color(0xFF15110A), Color(0xFF241D10), Color(0xFF211A0F), Color(0xFF3A2F17), Color(0xFFF5C518), true),
    OCEAN("ocean", Color(0xFF061417), Color(0xFF0E2328), Color(0xFF0C1F24), Color(0xFF17363D), Color(0xFF3FD0D4), true),
    FOREST("forest", Color(0xFF08130C), Color(0xFF122117), Color(0xFF101D14), Color(0xFF1E3526), Color(0xFF4CD27A), true),
    WINE("wine", Color(0xFF16080D), Color(0xFF261018), Color(0xFF220E15), Color(0xFF3D1B28), Color(0xFFFF5C7C), true),
    VIOLET("violet", Color(0xFF0E0A1A), Color(0xFF1A1430), Color(0xFF17122A), Color(0xFF2B2247), Color(0xFFB388FF), true),
    SLATE("slate", Color(0xFF12161C), Color(0xFF1D232C), Color(0xFF1A2028), Color(0xFF2C3440), Color(0xFF9FB3C8), true);

    companion object {
        val keys = entries.map { it.key }.toSet()
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: DARK
    }
}

@Composable fun StoneTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val spec = StoneThemeSpec.from(mode)
    val accent = StoneLook.accentColor
    StonePalette.Background = spec.background
    StonePalette.Surface = spec.surface
    StonePalette.Chip = spec.chip
    StonePalette.Border = spec.border
    StonePalette.Glow = spec.glow
    StonePalette.Textured = spec.textured
    StonePalette.Yellow = accent
    StonePalette.Text = StoneLook.textInk.resolve(accent)
    StonePalette.IconTint = StoneLook.iconInk.resolve(accent)
    // Mini player colours are derived from the accent so they follow every colour choice.
    StonePalette.MiniBackground = accent.copy(alpha = .22f).compositeOver(spec.background).copy(alpha = .95f)
    StonePalette.MiniBorder = accent.copy(alpha = .42f)
    StonePalette.MiniSubtitle = accent.copy(alpha = .9f)
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * StoneLook.fontScale)) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = accent, onPrimary = Color(0xFF0A0A0E),
            background = spec.background, surface = spec.surface,
            surfaceContainerLow = spec.chip, surfaceContainer = spec.surface,
            surfaceContainerHigh = spec.surface, outline = spec.border, outlineVariant = spec.border,
            onBackground = StonePalette.Text, onSurface = StonePalette.Text,
            onSurfaceVariant = Color(0xFFA0A0A5), error = Color(0xFFFF4C4C),
        ), typography = stoneTypography(StoneLook.fontStyle == "italic"), content = content)
    }
}

/** Material type scale; "Cursiva" slants every style (Text inherits it unless it sets its own). */
private fun stoneTypography(italic: Boolean): androidx.compose.material3.Typography {
    val base = androidx.compose.material3.Typography()
    if (!italic) return base
    fun androidx.compose.ui.text.TextStyle.slanted() = copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    return base.copy(displayLarge = base.displayLarge.slanted(), displayMedium = base.displayMedium.slanted(),
        displaySmall = base.displaySmall.slanted(), headlineLarge = base.headlineLarge.slanted(),
        headlineMedium = base.headlineMedium.slanted(), headlineSmall = base.headlineSmall.slanted(),
        titleLarge = base.titleLarge.slanted(), titleMedium = base.titleMedium.slanted(), titleSmall = base.titleSmall.slanted(),
        bodyLarge = base.bodyLarge.slanted(), bodyMedium = base.bodyMedium.slanted(), bodySmall = base.bodySmall.slanted(),
        labelLarge = base.labelLarge.slanted(), labelMedium = base.labelMedium.slanted(), labelSmall = base.labelSmall.slanted())
}
