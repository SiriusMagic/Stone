package com.zirakusa.stone.nativeapp.data.library

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.MainActivity
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import org.json.JSONArray
import org.json.JSONObject

/** "Notificación de nuevos archivos": one notification per scan that found songs or videos. */
object NewFilesNotifier {
    private const val CHANNEL = "new_files"
    private const val ID = 4201
    private const val REMINDER_CHANNEL = "music_reminders"
    private const val REMINDER_ID = 4202

    /**
     * Called after a scan. Keeps only the kinds the user wants (songs and/or videos) and either
     * posts now or queues them for the daily summary.
     */
    fun notify(context: Context, added: List<Song>) {
        val store = StoneSettingsStore(context)
        val wanted = added.filter { if (it.isVideo) store.text("notify.video", "true") == "true" else store.text("notify.audio", "true") == "true" }
        if (wanted.isEmpty()) return
        if (store.text("notify.delivery", "instant") == "scheduled") {
            val pending = runCatching { JSONArray(store.text("notify.pending", "[]")) }.getOrDefault(JSONArray())
            wanted.forEach { if (pending.length() < 300) pending.put(JSONObject().put("title", it.title).put("artist", it.artist).put("video", it.isVideo)) }
            store.setText("notify.pending", pending.toString())
        } else post(context, wanted.map { Triple(it.title, it.artist, it.isVideo) })
    }

    /** The scheduled summary: everything found since the last one. */
    fun flush(context: Context) {
        val store = StoneSettingsStore(context)
        val pending = runCatching { JSONArray(store.text("notify.pending", "[]")) }.getOrDefault(JSONArray())
        if (pending.length() == 0) return
        store.setText("notify.pending", "[]")
        post(context, (0 until pending.length()).map { pending.getJSONObject(it) }
            .map { Triple(it.optString("title"), it.optString("artist"), it.optBoolean("video")) })
    }

    /** Daily music reminder. */
    fun remind(context: Context) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL,
            context.getString(R.string.music_reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_new_media)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(openApp(context)).setAutoCancel(true).build()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        runCatching { NotificationManagerCompat.from(context).notify(REMINDER_ID, notification) }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    /** One notification for a list of (title, artist, isVideo). */
    private fun post(context: Context, added: List<Triple<String, String, Boolean>>) {
        if (added.isEmpty() || !canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.notify_new_files), NotificationManager.IMPORTANCE_DEFAULT))
        val videos = added.count { it.third }
        val title = context.resources.getQuantityString(R.plurals.new_files_title, added.size, added.size)
        val names = added.take(5).joinToString("\n") { (name, artist, _) -> listOf(name, artist).filter { it.isNotBlank() }.joinToString(" · ") }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_new_media)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.new_files_split, added.size - videos, videos))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names + if (added.size > 5) "\n…" else ""))
            .setContentIntent(openApp(context)).setAutoCancel(true).build()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }

    fun canPost(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
