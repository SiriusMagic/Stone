package com.zirakusa.stone.nativeapp.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.scan.DeviceMediaAccess
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.settings.api.color

/** Whole-device scanning (MediaStore + permission) and automatic rescans on open. */
@Composable internal fun LibraryScanSettings(store: StoneSettingsStore?) {
    val context = LocalContext.current
    var deviceScan by remember { mutableStateOf(store?.read()?.deviceScan ?: true) }
    var autoRescan by remember { mutableStateOf(store?.read()?.autoRescan ?: true) }
    var granted by remember { mutableStateOf(DeviceMediaAccess.audioGranted(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = DeviceMediaAccess.audioGranted(context)
        DeviceMediaAccess.rescanRequests.tryEmit(Unit)
    }
    Text(stringResource(R.string.device_scan_hint), color = StonePalette.Muted)
    SettingsGroup {
        ToggleSetting(R.string.device_scan, R.string.device_scan_subtitle, deviceScan) { enabled ->
            deviceScan = enabled
            store?.setDeviceScan(enabled)
            if (enabled && !granted) request.launch(DeviceMediaAccess.permissions())
            else DeviceMediaAccess.rescanRequests.tryEmit(Unit)
        }
        ToggleSetting(R.string.auto_rescan, R.string.auto_rescan_subtitle, autoRescan) { enabled ->
            autoRescan = enabled
            store?.setAutoRescan(enabled)
        }
    }
    if (deviceScan) {
        Text(stringResource(if (granted) R.string.device_scan_granted else R.string.device_scan_missing),
            color = if (granted) Color(0xFF4CD27A) else StonePalette.Yellow, modifier = Modifier.padding(top = 12.dp))
        if (!granted) {
            OutlinedButton(onClick = { request.launch(DeviceMediaAccess.permissions()) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.device_scan_grant))
            }
            // If Android stopped showing the dialog ("don't ask again"), the app page is the way.
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.device_scan_open_settings)) }
        }
    }
}
