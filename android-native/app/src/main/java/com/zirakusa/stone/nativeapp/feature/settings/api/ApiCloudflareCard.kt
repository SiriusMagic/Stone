package com.zirakusa.stone.nativeapp.feature.settings.api

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.cloudflare.Cloudflare
import com.zirakusa.stone.nativeapp.automation.cloudflare.CloudflareException
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Cloudflare: the person's API token for the account that holds the tunnel (Stone's relay) and the hub.
 * Update it before it expires; it is shared with Gateway Hub. "Comprobar túnel" looks for the relay Worker.
 */
@Composable internal fun ApiCloudflareCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // What the last action said: a text resource and its optional argument.
    var note by remember { mutableStateOf<Pair<Int, String?>?>(null) }
    fun work(block: () -> Pair<Int, String?>) {
        busy = true; note = null
        scope.launch {
            note = withContext(Dispatchers.IO) {
                try { block() } catch (e: CloudflareException) { cloudflareError(e.code) to null }
            }
            busy = false
        }
    }
    ApiActionCard(Icons.Default.Cloud, R.string.api_cf_title, R.string.api_cf_hint, trailing = {}) {
        val days = Cloudflare.daysLeft()
        val (status, color) = when {
            !Cloudflare.saved -> stringResource(R.string.api_cf_none) to StonePalette.Muted
            days == null -> stringResource(R.string.api_cf_no_expiry) to ApiGreen
            days < 0 -> stringResource(R.string.api_cf_expired) to ApiRed
            else -> pluralStringResource(R.plurals.api_cf_expires_in, days.toInt(), days.toInt(),
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(Cloudflare.expiresAt))) to if (days <= 2) StonePalette.Yellow else ApiGreen
        }
        Text(status, Modifier.padding(top = 10.dp), color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text(stringResource(R.string.api_cf_token)) }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && token.isNotBlank(), onClick = {
                val value = token; token = ""
                work { Cloudflare.update(value); R.string.api_cf_saved to null }
            }) { Text(stringResource(R.string.api_cf_update)) }
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Cloudflare.CREATE_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) { }
            }) { Text(stringResource(R.string.api_cf_create)) }
        }
        if (Cloudflare.saved) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { work { tunnelNote() } }) { Text(stringResource(R.string.api_cf_check_tunnel)) }
            TextButton(onClick = { Cloudflare.forget(); note = null }) { Text(stringResource(R.string.api_cf_forget), color = ApiRed) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        note?.let { (id, arg) -> Text(if (arg == null) stringResource(id) else stringResource(id, arg), Modifier.padding(top = 6.dp), color = StonePalette.Secondary, fontSize = 13.sp) }
    }
}

private fun tunnelNote(): Pair<Int, String?> {
    val found = Cloudflare.relayAddress() ?: return R.string.api_cf_tunnel_missing to null
    val same = found.trimEnd('/') == ApiMode.relayUrl.trimEnd('/')
    return (if (same) R.string.api_cf_tunnel_ok else R.string.api_cf_tunnel_other) to found.removePrefix("https://")
}

internal fun cloudflareError(code: String) = when (code) {
    "EMPTY" -> R.string.api_cf_err_empty
    "INVALID" -> R.string.api_cf_err_invalid
    "INACTIVE" -> R.string.api_cf_err_inactive
    "FORBIDDEN" -> R.string.api_cf_err_forbidden
    "NETWORK" -> R.string.api_cf_err_network
    "MISSING" -> R.string.api_cf_none
    else -> R.string.api_cf_err_other
}
