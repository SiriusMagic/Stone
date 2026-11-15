package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Enlace tab: the internet link, the main token, Wi-Fi addresses, port/test tools and how to connect. */
@Composable internal fun ApiLinkTab(copy: (String, String) -> Unit) {
    ApiModeLinkCard(copy)
    ApiSection(R.string.api_token, Icons.Default.Key) {
        Text(ApiMode.token, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        FilledTonalButton(onClick = { copy("token", ApiMode.token) }, Modifier.padding(top = 6.dp)) {
            Text(stringResource(R.string.api_copy_token))
        }
        Text(stringResource(R.string.api_header_hint), color = StonePalette.Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp))
    }
    ApiSection(R.string.api_link_lan_title, Icons.Default.Wifi) {
        ToggleSetting(R.string.api_lan, R.string.api_lan_hint, ApiMode.localNetwork, ApiMode::allowLocalNetwork)
        if (ApiMode.enabled) {
            Text(stringResource(R.string.api_address), color = StonePalette.Muted, fontSize = 13.sp)
            ApiMode.urls().forEach { url ->
                TextButton(onClick = { copy("MCP", url) }) { Text(url, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
            }
            ApiModeConnect(copy)
        }
    }
    ApiSection(R.string.api_howto_title, Icons.Default.Info) {
        listOf(R.string.api_howto_1, R.string.api_howto_2, R.string.api_howto_3, R.string.api_howto_4).forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", Modifier.width(24.dp), color = StonePalette.Yellow, fontWeight = FontWeight.Black)
                Text(stringResource(step), fontSize = 14.sp)
            }
        }
    }
}
