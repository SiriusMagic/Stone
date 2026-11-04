package com.zirakusa.stone.nativeapp.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DiagnosticsReportExporter {
    fun json(context: Context, summary: DiagnosticSessionSummary): File {
        val root = JSONObject().apply {
            put("date", summary.startedAt); put("durationMs", summary.durationMs)
            put("method", "Android process, Debug.MemoryInfo, ActivityManager and BatteryManager")
            put("samples", JSONArray().apply { summary.samples.forEach { sample -> put(JSONObject().apply {
                put("cpuPercent", sample.cpuPercent ?: JSONObject.NULL); put("pssTotalMb", sample.pssTotalMb)
                put("pssNativeMb", sample.pssNativeMb); put("heapMb", sample.processHeapMb)
                put("batteryPercent", sample.batteryPercent); put("thermalStatus", sample.thermalStatus)
                put("audioRoute", sample.audioRoute); put("errorCount", sample.errorCount)
            }) } })
        }
        return File(context.cacheDir, "stone-diagnostics-${summary.startedAt}.json").also { it.writeText(root.toString(2)) }
    }

    fun share(context: Context, summary: DiagnosticSessionSummary) {
        val file = json(context, summary)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartir diagnóstico"))
    }
}
