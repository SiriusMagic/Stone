package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.core.LandscapeLayoutMode
import com.zirakusa.stone.nativeapp.R

@Composable fun HorizontalLayoutSettings(mode: LandscapeLayoutMode,
    onChange: (LandscapeLayoutMode) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(stringResource(R.string.horizontal_layout_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LandscapeLayoutMode.entries.forEach { choice ->
            val always = choice == LandscapeLayoutMode.ALWAYS
            Row(Modifier.fillMaxWidth().selectable(mode == choice, role = Role.RadioButton,
                onClick = { onChange(choice) }).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == choice, onClick = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(if (always) R.string.horizontal_always else R.string.horizontal_library_only))
                    Text(stringResource(if (always) R.string.horizontal_always_hint else R.string.horizontal_library_only_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
