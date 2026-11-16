package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.zirakusa.stone.nativeapp.automation.RelayClient
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** The two-click part of API mode: the user's link, a big Copy button and "change link". */
@Composable internal fun ApiModeLinkCard(copy: (String, String) -> Unit) {
    val link = ApiMode.shareText()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(22.dp))
        .background(StonePalette.Chip).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (color, label) = when {
                !ApiMode.running -> MaterialTheme.colorScheme.error to R.string.api_failed
                !ApiMode.relayAvailable -> StonePalette.Yellow to R.string.api_relay_missing
                ApiMode.relayState == RelayClient.State.ONLINE -> Color(0xFF4CD27A) to R.string.api_relay_online
                ApiMode.relayState == RelayClient.State.CONNECTING -> StonePalette.Yellow to R.string.api_relay_connecting
                else -> MaterialTheme.colorScheme.error to R.string.api_relay_offline
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(stringResource(label), Modifier.padding(start = 8.dp), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(stringResource(R.string.api_link_title), Modifier.padding(top = 14.dp), fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(link, Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            color = StonePalette.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Button(onClick = { copy("Stone MCP", link) }, Modifier.fillMaxWidth().padding(top = 14.dp).height(54.dp),
            shape = RoundedCornerShape(27.dp)) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.api_copy_link), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Text(stringResource(R.string.api_link_howto), Modifier.padding(top = 12.dp), color = StonePalette.Muted, fontSize = 13.sp)
        TextButton(onClick = ApiMode::regenerateToken, Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.api_change_link))
        }
    }
}
