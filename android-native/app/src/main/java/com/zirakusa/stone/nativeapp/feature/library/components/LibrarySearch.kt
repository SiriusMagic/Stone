package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable fun LibrarySearch(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val label = stringResource(R.string.search)
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth()
        .border(1.dp, StonePalette.Border, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp), color = StonePalette.Chip) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StoneIcon(StoneGlyph.Search, color = StonePalette.Muted, size = 18)
            Spacer(Modifier.width(8.dp))
            BasicTextField(query, onChange, Modifier.weight(1f).focusRequester(focus)
                .semantics { contentDescription = label }, singleLine = true,
                cursorBrush = SolidColor(StonePalette.Yellow), textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                decorationBox = { field ->
                    if (query.isEmpty()) Text(label, color = StonePalette.Muted, fontSize = 15.sp)
                    field()
                })
            if (query.isNotEmpty()) StoneIconButton(StoneGlyph.Close, stringResource(R.string.clear_search), { onChange("") })
        }
    }
}
