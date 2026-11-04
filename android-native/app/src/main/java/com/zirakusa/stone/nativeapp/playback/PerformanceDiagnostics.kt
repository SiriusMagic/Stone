package com.zirakusa.stone.nativeapp.playback

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager

data class PerformanceSnapshot(
    val usedRamMb: Long,
    val availableRamMb: Long,
    val batteryPercent: Int,
    val processHeapMb: Long,
)

object PerformanceDiagnostics {
    fun read(context: Context): PerformanceSnapshot {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
        val battery = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it >= 0 } ?: -1
        val runtime = Runtime.getRuntime()
        val heap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return PerformanceSnapshot(
            usedRamMb = ((memory.totalMem - memory.availMem).coerceAtLeast(0)) / (1024 * 1024),
            availableRamMb = memory.availMem / (1024 * 1024), batteryPercent = battery,
            processHeapMb = heap)
    }
}
