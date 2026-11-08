package com.zirakusa.stone.nativeapp.playback

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

data class PerformanceSnapshot(
    val usedRamMb: Long, val availableRamMb: Long, val batteryPercent: Int,
    val processHeapMb: Long, val pssTotalMb: Int = -1, val pssNativeMb: Int = -1,
    val pssDalvikMb: Int = -1, val nativeHeapMb: Long = -1, val cpuPercent: Double? = null,
    val threadCount: Int = -1, val cpuCount: Int = Runtime.getRuntime().availableProcessors(),
    val batteryTemperatureC: Double? = null, val charging: Boolean = false,
    val lowMemory: Boolean = false, val lowMemoryThresholdMb: Long = -1,
    val audioRoute: String = "No disponible", val thermalStatus: String = "No disponible",
    val errorCount: Int = 0, val timestampMs: Long = System.currentTimeMillis()
)

data class DiagnosticSessionSummary(val samples: List<PerformanceSnapshot>, val durationMs: Long,
    val startedAt: Long, val events: Int)

object PerformanceDiagnostics {
    private var previousCpu: Pair<Long, Long>? = null
    private val playbackErrors = AtomicInteger(0)
    fun recordPlaybackError() { playbackErrors.incrementAndGet() }

    fun read(context: Context): PerformanceSnapshot {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val battery = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: -1
        val runtime = Runtime.getRuntime(); val debug = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val cpuTime = Process.getElapsedCpuTime(); val now = System.nanoTime() / 1_000_000
        val cpu = previousCpu?.let { (oldCpu, oldTime) ->
            val elapsed = now - oldTime
            if (elapsed > 0) ((cpuTime - oldCpu).toDouble() / elapsed * 100.0).coerceIn(0.0, 100.0 * runtime.availableProcessors()) else null
        }
        previousCpu = cpuTime to now
        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) when (context.getSystemService(PowerManager::class.java)?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"; PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"; PowerManager.THERMAL_STATUS_SEVERE -> "Grave"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"; PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado"; else -> "No disponible"
        } else "No disponible"
        val temperature: Double? = null
        val audio = context.getSystemService(AudioManager::class.java)
        val device = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull()
        val route = device?.productName?.toString()?.takeIf { it.isNotBlank() } ?: when (device?.type) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Auriculares con cable"
            else -> "No disponible"
        }
        return PerformanceSnapshot(
            ((memory.totalMem - memory.availMem).coerceAtLeast(0)) / 1_048_576,
            memory.availMem / 1_048_576, battery, (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
            debug.totalPss / 1024, debug.nativePss / 1024, debug.dalvikPss / 1024,
            Debug.getNativeHeapAllocatedSize() / 1_048_576, cpu, processThreadCount(),
            runtime.availableProcessors(), temperature, batteryManager?.isCharging == true,
            memory.lowMemory, memory.threshold / 1_048_576, route, thermal, playbackErrors.get())
    }

    /** /proc exposes this count without allocating a stack trace for every thread. */
    private fun processThreadCount(): Int = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("Threads:") }.substringAfter(':').trim().toInt()
        }
    }.getOrDefault(-1)

    suspend fun session(context: Context, durationMs: Long, intervalMs: Long = 1000L,
        onSample: (PerformanceSnapshot) -> Unit): DiagnosticSessionSummary {
        val started = System.currentTimeMillis(); val samples = mutableListOf<PerformanceSnapshot>()
        while (System.currentTimeMillis() - started < durationMs) {
            val sample = withContext(Dispatchers.Default) { read(context) }
            samples += sample; onSample(sample); delay(intervalMs)
        }
        return DiagnosticSessionSummary(samples, System.currentTimeMillis() - started, started, playbackErrors.get())
    }
}
