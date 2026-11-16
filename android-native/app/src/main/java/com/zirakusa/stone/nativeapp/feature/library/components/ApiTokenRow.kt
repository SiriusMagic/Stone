package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.ApiToken
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Compact token card: name and state, its link with copy, then Revocar · Pausar · Configurar. */
@Composable internal fun ApiTokenCard(token: ApiToken, copy: (String, String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(StonePalette.Chip)
        .border(1.dp, Color.White.copy(alpha = .06f), shape).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(token.name, Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            val (state, color) = when {
                token.paused -> R.string.api_token_paused to StonePalette.Yellow
                token.expired -> R.string.api_token_expired to ApiRed
                else -> R.string.api_token_active to ApiGreen
            }
            ApiPill(stringResource(state), color, Modifier.padding(end = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ApiMode.linkFor(token.secret), Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = StonePalette.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { copy("Stone MCP · ${token.name}", ApiMode.shareText(token.secret)) }) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.api_token_copy_link), Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .06f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { confirmRevoke = true }) { Text(stringResource(R.string.api_token_revoke), color = ApiRed) }
            TextButton(onClick = { ApiTokens.setPaused(token.id, !token.paused) }) {
                Text(stringResource(if (token.paused) R.string.api_token_resume else R.string.api_token_pause), color = StonePalette.Text)
            }
            FilledTonalButton(onClick = { editing = true }) { Text(stringResource(R.string.api_token_configure_short)) }
        }
    }
    if (editing) ApiTokenEditor(token, copy) { editing = false }
    if (confirmRevoke) ApiRevokeDialog(token, { confirmRevoke = false })
}

/** Revoking cannot be undone, so it always asks first. */
@Composable internal fun ApiRevokeDialog(token: ApiToken, onDismiss: () -> Unit, onRevoked: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_token_revoke_title, token.name)) },
        text = { Text(stringResource(R.string.api_token_revoke_text)) },
        confirmButton = { TextButton(onClick = { onDismiss(); ApiTokens.revoke(token.id); onRevoked() }) {
            Text(stringResource(R.string.api_token_revoke), color = ApiRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
