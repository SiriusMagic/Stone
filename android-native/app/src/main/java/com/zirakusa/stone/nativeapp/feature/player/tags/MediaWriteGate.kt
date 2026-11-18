package com.zirakusa.stone.nativeapp.feature.player.tags

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.nativeapp.data.library.tags.MediaFileIo

/**
 * Runs a write action on a media file, first asking Android for write consent when the file
 * comes from the device-wide MediaStore index (Android 11+). Folder files need no dialog.
 */
@Composable internal fun rememberMediaWriteGate(): (uri: String, action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    return { uri, action ->
        val sender = if (MediaFileIo.needsWriteConsent(context, uri))
            MediaFileIo.writeConsentRequest(context, listOf(uri)) else null
        if (sender == null) action() else {
            pending = action
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
}
