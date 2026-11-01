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
    val Chip = Color(0xFF15151B)
    val Border = Color(0xFF22222B)
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
}

private fun stoneColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFFF5C518), onPrimary = Color(0xFF0A0A0E),
    background = Color(0xFF0A0A0E), surface = Color(0xFF1C1C24),
    onBackground = Color.White, onSurface = Color.White,
    onSurfaceVariant = Color(0xFFA0A0A5), error = Color(0xFFFF4C4C),
) else androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF8A6500), onPrimary = Color.White,
    background = Color(0xFFF7F6FA), surface = Color.White,
    onBackground = Color(0xFF15141A), onSurface = Color(0xFF15141A),
    onSurfaceVariant = Color(0xFF625F68), error = Color(0xFFBA1A1A),
)

@Composable fun StoneTheme(mode: String = "dark", content: @Composable () -> Unit) {
    StonePalette.Background = if (mode == "gray") Color(0xFF24242A) else Color(0xFF0A0A0E)
    StonePalette.Surface = if (mode == "gray") Color(0xFF303038) else Color(0xFF15151B)
    MaterialTheme(colorScheme = stoneColors(dark = true), content = content)
}
