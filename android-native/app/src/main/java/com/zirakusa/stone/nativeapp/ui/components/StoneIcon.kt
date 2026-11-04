package com.zirakusa.stone.nativeapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

// Original Ionicons glyphs, bundled offline from Stone's existing icon dependency.
enum class StoneGlyph(val character: String) {
    Search("\uf55f"), Folder("\uf327"), Scan("\uf55a"), ScanButton("\uf556"),
    Download("\uf2ba"), Sort("\uf5b4"), Settings("\uf56c"), Video("\uf4ce"),
    Music("\uf46a"), Note("\uf467"), Close("\uf24b"), Play("\uf4c6"), Pause("\uf493"),
    Options("\uf2ce"), Back("\uf229"), Forward("\uf23b"), Check("\uf21d"),
    Person("\uf4ac"), Album("\uf110")
}
private val StoneIconFont = FontFamily(Font(R.font.stone_icons))

@Composable fun StoneIcon(glyph: StoneGlyph, modifier: Modifier = Modifier,
    color: Color = StonePalette.Yellow, size: Int = 22) {
    Text(glyph.character, modifier.clearAndSetSemantics { }, color = color,
        style = TextStyle(fontFamily = StoneIconFont, fontSize = size.sp, lineHeight = size.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable fun StoneIconButton(glyph: StoneGlyph, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.size(44.dp).clickable(enabled = enabled, role = Role.Button, onClickLabel = label,
        onClick = onClick).then(Modifier.semantics(mergeDescendants = true) {
            contentDescription = label
        }), contentAlignment = Alignment.Center) {
        StoneIcon(glyph, color = if (enabled) StonePalette.Yellow else StonePalette.Muted)
    }
}
