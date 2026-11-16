package com.zirakusa.stone.nativeapp.data.persistence

import android.content.Context
import android.os.Build
import com.zirakusa.stone.nativeapp.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the phone ("Stone se detuvo"), so it can be read and sent from
 * Ajustes › Diagnóstico. Stays on the device; Android's own crash dialog still appears.
 */
object CrashLog {
    @Volatile private var installed = false

    private fun file(context: Context) = File(context.applicationContext.filesDir, "last_crash.txt")

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file(app).writeText("Stone ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + " · hilo ${thread.name}\n\n" +
                    trace.take(20_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) { file(context).delete() }
}
