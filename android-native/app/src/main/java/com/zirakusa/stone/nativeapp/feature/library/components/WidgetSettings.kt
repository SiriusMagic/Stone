package com.zirakusa.stone.nativeapp.feature.library.components

import android.appwidget.AppWidgetManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.StoneWidgetProvider
import com.zirakusa.stone.nativeapp.ui.components.StoneAvatar
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/** Ajustes › Widgets: preview of the home-screen player and a button that asks the launcher to add it. */
@Composable internal fun WidgetSettings() {
    val context = LocalContext.current
    Text(stringResource(R.string.widget_intro), color = StonePalette.Muted)
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(StonePalette.Chip).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // Same layout as res/layout/widget_player.xml.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xF0101016)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoneAvatar(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(stringResource(R.string.app_name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(stringResource(R.string.widget_idle), color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Icons.Default.SkipPrevious, Icons.Default.PlayArrow, Icons.Default.SkipNext, Icons.Default.FavoriteBorder)
                    .forEach { Icon(it, null, Modifier.size(30.dp), tint = Color.White) }
            }
        }
        Text(stringResource(R.string.widget_player_name), Modifier.padding(top = 14.dp), fontSize = 18.sp, textAlign = TextAlign.Center)
        Text("4×2", color = StonePalette.Muted, fontSize = 13.sp)
        Button(onClick = {
            val manager = AppWidgetManager.getInstance(context)
            val pinned = manager.isRequestPinAppWidgetSupported &&
                runCatching { manager.requestPinAppWidget(StoneWidgetProvider.component(context), null, null) }.getOrDefault(false)
            if (!pinned) Toast.makeText(context, R.string.widget_manual, Toast.LENGTH_LONG).show()
        }, Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
            Text(stringResource(R.string.widget_add), fontWeight = FontWeight.Bold)
        }
    }
}
