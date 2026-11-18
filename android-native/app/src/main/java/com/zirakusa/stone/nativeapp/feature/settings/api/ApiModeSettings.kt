package com.zirakusa.stone.nativeapp.feature.settings.api

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Ajustes › Gateway Hub: Stone is only a provider. The link state Gateway reports, the execution packages to
 * approve or revoke, and what agents did here. Everything else (tokens, tunnel, Cloudflare) lives in Gateway Hub.
 */
@Composable internal fun ApiModeSettings() {
    ApiGatewayCard()
    ApiActivityTab()
    Spacer(Modifier.height(16.dp))
}
