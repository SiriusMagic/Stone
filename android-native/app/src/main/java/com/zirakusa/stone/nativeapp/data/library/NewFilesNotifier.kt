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

/** "Notificación de nuevos archivos": one notification per scan that found songs or videos. */
object NewFilesNotifier {
    private const val CHANNEL = "new_files"
    private const val ID = 4201

    /** Posts the notice when allowed; silently does nothing without the Android permission. */
    fun notify(context: Context, added: List<Song>) {
        if (added.isEmpty() || !canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.notify_new_files), NotificationManager.IMPORTANCE_DEFAULT))
        val videos = added.count { it.isVideo }
        val title = context.resources.getQuantityString(R.plurals.new_files_title, added.size, added.size)
        val names = added.take(5).joinToString("\n") { song -> listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" · ") }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_new_media)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.new_files_split, added.size - videos, videos))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names + if (added.size > 5) "\n…" else ""))
            .setContentIntent(open).setAutoCancel(true).build()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }

    fun canPost(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
