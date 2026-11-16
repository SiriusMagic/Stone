package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiMode
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.data.library.EditJournal
import com.zirakusa.stone.nativeapp.data.library.MediaFileIo
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Seguridad tab: tokens with their own mode, Android's all-files access and edit backups. */
@Composable internal fun ApiSecurityTab(copy: (String, String) -> Unit) {
    val context = LocalContext.current
    val stoneResources = androidx.compose.ui.platform.LocalResources.current
    var creating by remember { mutableStateOf(false) }
    ApiSection(R.string.api_tokens_title, Icons.Default.Key, R.string.api_tokens_hint, R.string.api_token_create to { creating = true }) {
        ApiTokens.tokens.forEachIndexed { index, token -> ApiTokenRow(token, index == 0, copy) }
    }
    if (creating) ApiTokenDialog(onDismiss = { creating = false }) { name, mode, days ->
        val token = ApiTokens.create(name, mode, days)
        copy("Stone MCP", ApiMode.linkFor(token.secret))
        creating = false
    }
    if (Build.VERSION.SDK_INT >= 30) {
        var granted by remember { mutableStateOf(MediaFileIo.hasAllFilesAccess()) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = MediaFileIo.hasAllFilesAccess() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        ApiSection(R.string.api_files_title, Icons.Default.FolderOpen, R.string.api_files_hint) {
            if (granted) ApiPill(stringResource(R.string.api_files_on), ApiGreen)
            else Button(onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
                runCatching { context.startActivity(intent) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }) { Text(stringResource(R.string.api_files_grant)) }
        }
    }
    val history by StoneAutomation.history.collectAsState()
    val backups = remember(history.size) { EditJournal(context.applicationContext).list().size }
    val scope = rememberCoroutineScope()
    ApiSection(R.string.api_backups_title, Icons.Default.Restore) {
        Text(stringResource(R.string.api_backups_hint, backups), color = StonePalette.Secondary, fontSize = 13.sp)
        OutlinedButton(enabled = backups > 0, modifier = Modifier.padding(top = 8.dp), onClick = {
            scope.launch {
                val message = runCatching { StoneAutomation.execute("metadata_undo", JSONObject(), AccessMode.FULL, "Stone") }
                    .fold({ stoneResources.getString(R.string.api_backups_undone) }, { it.message ?: "" })
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }) { Text(stringResource(R.string.api_backups_undo)) }
    }
    ApiNote(R.string.api_tokens_care_title, R.string.api_tokens_care)
}
