package com.zirakusa.stone.nativeapp.feature.library.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.library.NotificationScheduler
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

/**
 * Ajustes › Notificaciones, in one place: new songs/videos (at once or in a summary at a chosen
 * time) and the daily music reminder at its own time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NotificationSettings(store: StoneSettingsStore?) {
    val context = LocalContext.current
    fun reschedule() = NotificationScheduler.schedule(context)
    var newFiles by remember(store) { mutableStateOf(store?.read()?.notifyNewFiles ?: false) }
    var audio by remember(store) { mutableStateOf(store?.text("notify.audio", "true") != "false") }
    var video by remember(store) { mutableStateOf(store?.text("notify.video", "true") != "false") }
    var scheduled by remember(store) { mutableStateOf(store?.text("notify.delivery", "instant") == "scheduled") }
    var digestTime by remember(store) { mutableStateOf(store?.text("notify.time", "20:00") ?: "20:00") }
    var reminders by remember(store) { mutableStateOf(store?.read()?.musicReminders ?: false) }
    var reminderTime by remember(store) { mutableStateOf(store?.text("reminder.time", "19:00") ?: "19:00") }
    var picking by remember { mutableStateOf<String?>(null) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission?.invoke()
        afterPermission = null
    }
    /** Turns a notice on only after Android allows notifications (13+). */
    fun enable(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) { afterPermission = action; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else action()
    }

    Section(R.string.notify_new_files)
    ToggleSetting(R.string.notify_new_files, R.string.notify_new_files_hint, newFiles) { on ->
        val apply = { store?.setBoolean("notifyNewFiles", on); newFiles = on; reschedule() }
        if (on) enable(apply) else apply()
    }
    if (newFiles) {
        CheckRow(R.string.notify_songs, audio) { audio = it; store?.setText("notify.audio", it.toString()) }
        CheckRow(R.string.notify_videos, video) { video = it; store?.setText("notify.video", it.toString()) }
        Text(stringResource(R.string.notify_when), Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
        RadioRow(R.string.notify_instant, !scheduled) { scheduled = false; store?.setText("notify.delivery", "instant"); reschedule() }
        RadioRow(R.string.notify_scheduled, scheduled) { scheduled = true; store?.setText("notify.delivery", "scheduled"); reschedule() }
        if (scheduled) TimeRow(R.string.notify_time, digestTime) { picking = "notify.time" }
    }

    Section(R.string.music_reminders)
    ToggleSetting(R.string.music_reminders, R.string.music_reminders_hint, reminders) { on ->
        val apply = { store?.setBoolean("musicReminders", on); reminders = on; reschedule() }
        if (on) enable(apply) else apply()
    }
    if (reminders) TimeRow(R.string.reminder_time, reminderTime) { picking = "reminder.time" }
    Text(stringResource(R.string.notify_inexact), Modifier.padding(top = 14.dp, bottom = 16.dp), color = StonePalette.Muted, fontSize = 12.sp)

    picking?.let { key ->
        val current = NotificationScheduler.minutes(if (key == "notify.time") digestTime else reminderTime, 20 * 60)
        val state = rememberTimePickerState(current / 60, current % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { picking = null },
            confirmButton = { TextButton(onClick = {
                val value = NotificationScheduler.format(state.hour * 60 + state.minute)
                store?.setText(key, value)
                if (key == "notify.time") digestTime = value else reminderTime = value
                picking = null; reschedule()
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) })
    }
}

@Composable private fun Section(title: Int) = Text(stringResource(title), Modifier.padding(top = 18.dp, bottom = 4.dp),
    color = StonePalette.Yellow, fontWeight = FontWeight.Black, fontSize = 14.sp)

@Composable private fun CheckRow(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, change); Text(stringResource(label))
    }
}

@Composable private fun RadioRow(label: Int, selected: Boolean, select: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = select), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, select); Text(stringResource(label))
    }
}

@Composable private fun TimeRow(label: Int, time: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Text(time, color = StonePalette.Yellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
