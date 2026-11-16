package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.CrashLog
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Ajustes › Diagnóstico: the last "Stone se detuvo", to copy or send. Hidden when there is none. */
@Composable internal fun CrashReportCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val text = report ?: return
    Text(stringResource(R.string.crash_last_title), Modifier.padding(top = 18.dp), color = StonePalette.Yellow, fontWeight = FontWeight.Black)
    Text(stringResource(R.string.crash_last_hint), color = StonePalette.Muted, fontSize = 12.sp)
    Text(text.lines().take(14).joinToString("\n"), Modifier.padding(top = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Stone crash", text))
            Toast.makeText(context, R.string.api_copied, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.crash_copy)) }
        FilledTonalButton(onClick = {
            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text), null)) }
        }) { Text(stringResource(R.string.share)) }
        TextButton(onClick = { CrashLog.clear(context); report = null }) { Text(stringResource(R.string.delete)) }
    }
}
