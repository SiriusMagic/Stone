package com.zirakusa.stone.nativeapp.feature.settings.api

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Package of the Gateway Hub app (same developer); «Ir al Hub» opens it when it is installed. */
internal const val GATEWAY_HUB_PACKAGE = "com.zirakusa.gatewayhub"

/**
 * «Redireccionar a Hub»: hands everything (permissions, expiry, support, analytics) to Gateway Hub. «Ir al Hub» copies
 * the hub's link (in case it has to be pasted in the hub's Apps page) and opens the Gateway Hub app.
 */
@Composable internal fun ApiHubCard(copy: (String, String) -> Unit) {
    val context = LocalContext.current
    ApiActionCard(Icons.Default.Layers, R.string.api_hub_redirect, R.string.api_hub_redirect_hint,
        trailing = { Switch(ApiMode.hubRedirect, ApiMode::redirectToHub) }) {
        if (ApiMode.hubRedirect) Column(Modifier.padding(top = 10.dp).align(Alignment.End)) {
            Button(onClick = {
                copy("Stone › Gateway Hub", ApiMode.hubLink())
                val open = context.packageManager.getLaunchIntentForPackage(GATEWAY_HUB_PACKAGE)
                if (open != null) context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else Toast.makeText(context, R.string.api_hub_paste, Toast.LENGTH_LONG).show()
            }, shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StonePalette.Yellow, contentColor = Color.Black)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Text(stringResource(R.string.api_hub_open), Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
