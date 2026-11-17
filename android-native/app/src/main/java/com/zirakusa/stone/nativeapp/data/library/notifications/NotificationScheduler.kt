package com.zirakusa.stone.nativeapp.data.library.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.util.Calendar
import com.zirakusa.stone.nativeapp.data.library.NotificationAlarmReceiver

/**
 * Ajustes › Notificaciones: when notices arrive. New files can be announced at once or in one
 * summary at a chosen time; the music reminder comes daily at its own time. Alarms are inexact
 * (Android may shift them a few minutes to save battery) and are re-created after a reboot.
 */
object NotificationScheduler {
    const val ACTION_DIGEST = "com.zirakusa.stone.notify.DIGEST"
    const val ACTION_REMINDER = "com.zirakusa.stone.notify.REMINDER"

    /** "HH:mm" → minutes after midnight; fallback when malformed. */
    fun minutes(text: String, fallback: Int): Int = runCatching {
        val (h, m) = text.split(':').map(String::toInt); (h.coerceIn(0, 23) * 60 + m.coerceIn(0, 59))
    }.getOrDefault(fallback)

    fun format(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Re-creates both alarms from the current settings (cheap; call after any change). */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val store = StoneSettingsStore(app)
        val settings = store.read()
        val digest = settings.notifyNewFiles && store.text("notify.delivery", "instant") == "scheduled"
        set(app, ACTION_DIGEST, 1, if (digest) minutes(store.text("notify.time", "20:00"), 20 * 60) else null)
        set(app, ACTION_REMINDER, 2, if (settings.musicReminders) minutes(store.text("reminder.time", "19:00"), 19 * 60) else null)
    }

    private fun set(context: Context, action: String, code: Int, atMinutes: Int?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(context, code, Intent(context, NotificationAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.cancel(intent)
        if (atMinutes == null) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, atMinutes / 60); set(Calendar.MINUTE, atMinutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, intent)
    }
}
