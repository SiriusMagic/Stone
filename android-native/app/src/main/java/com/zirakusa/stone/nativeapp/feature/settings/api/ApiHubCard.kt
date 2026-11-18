package com.zirakusa.stone.nativeapp.feature.settings.api

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * Gateway Hub: one link for an AI to use Stone and other apps. "Vincular" copies the link of the
 * token reserved for the hub (created the first time) and opens the hub's panel to paste it there,
 * so the token never goes through a chat. Shown only when this build knows the hub and the relay.
 */
@Composable internal fun ApiHubCard(copy: (String, String) -> Unit) {
    if (!ApiMode.hubAvailable) return
    val context = LocalContext.current
    val title = stringResource(R.string.api_hub_title)
    ApiActionCard(Icons.Default.Hub, R.string.api_hub_title, R.string.api_hub_hint, trailing = {
        Button(enabled = ApiMode.enabled, onClick = {
            copy(title, ApiMode.shareText(ApiMode.hubToken().secret))
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, ApiMode.hubAdminUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) { /* no browser: the link is already copied */ }
        }, shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StonePalette.Yellow, contentColor = Color.Black)) {
            Text(stringResource(R.string.api_hub_link), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }) {
        // Redirección hub: the Gateway Hub app on this phone links Stone by itself (same developer only).
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.api_hub_redirect), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(stringResource(R.string.api_hub_redirect_hint), color = StonePalette.Muted, fontSize = 12.sp)
            }
            Switch(ApiMode.hubRedirect, ApiMode::allowHubRedirect, Modifier.padding(start = 8.dp))
        }
    }
}
