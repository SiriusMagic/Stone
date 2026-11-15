package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.McpCheck
import com.zirakusa.stone.nativeapp.automation.McpSelfTest
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.launch

/** Port, "Probar conexión" and ready-to-paste client configurations. State is local to this section. */
@Composable internal fun ApiModeConnect(copy: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var portText by remember { mutableStateOf(ApiMode.port.toString()) }
    var portError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<McpCheck>>(emptyList()) }

    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5); portError = false },
            Modifier.weight(1f), singleLine = true, isError = portError, label = { Text(stringResource(R.string.api_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedButton(onClick = { portError = !ApiMode.changePort(portText.toIntOrNull() ?: -1); results = emptyList() }) {
            Text(stringResource(R.string.api_port_apply))
        }
    }
    Button(enabled = !testing, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), onClick = {
        testing = true
        scope.launch { results = McpSelfTest.run(ApiMode.urls(), ApiMode.token); testing = false }
    }) { Text(stringResource(if (testing) R.string.api_test_running else R.string.api_test)) }
    results.forEach { check ->
        val (text, ok) = when (check) {
            is McpCheck.Ok -> stringResource(R.string.api_test_ok, check.url) to true
            is McpCheck.OtherProgram -> stringResource(R.string.api_test_other, check.url) + "\n" + check.answer to false
            is McpCheck.NoAnswer -> stringResource(R.string.api_test_down, check.url, check.reason) to false
        }
        Text((if (ok) "✓ " else "✗ ") + text, Modifier.padding(top = 6.dp), fontSize = 13.sp,
            color = if (ok) Color(0xFF4CD27A) else MaterialTheme.colorScheme.error)
    }

    Text(stringResource(R.string.api_clients_hint), color = StonePalette.Muted, fontSize = 12.sp,
        modifier = Modifier.padding(top = 14.dp))
    CopyRow(R.string.api_copy_url) { copy("MCP", ApiMode.urlWithToken()) }
    CopyRow(R.string.api_copy_sse) { copy("MCP SSE", ApiMode.sseUrlWithToken()) }
    CopyRow(R.string.api_copy_json) { copy("MCP JSON", ApiMode.jsonConfig()) }
    CopyRow(R.string.api_copy_cli) { copy("Claude Code", ApiMode.claudeCodeCommand()) }
}

@Composable private fun CopyRow(label: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(stringResource(label)) }
}
