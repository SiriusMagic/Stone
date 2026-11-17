package com.zirakusa.stone.nativeapp.data.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zirakusa.stone.nativeapp.data.library.notifications.NewFilesNotifier
import com.zirakusa.stone.nativeapp.data.library.notifications.NotificationScheduler

// Registered in AndroidManifest.xml by its full name: never move or rename it, or alarms set by an
// older version of Stone would point to a class that no longer exists.
/** Fires the scheduled summary / reminder, and restores the alarms after a reboot or update. */
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationScheduler.ACTION_DIGEST -> NewFilesNotifier.flush(context)
            NotificationScheduler.ACTION_REMINDER -> NewFilesNotifier.remind(context)
        }
        NotificationScheduler.schedule(context)
    }
}
