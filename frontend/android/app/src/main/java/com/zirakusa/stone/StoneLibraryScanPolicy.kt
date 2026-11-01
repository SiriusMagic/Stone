package com.zirakusa.stone

/**
 * Decides whether an automatic library refresh is worthwhile.
 * It has no timer or background worker; the UI invokes scans on lifecycle events.
 */
class StoneLibraryScanPolicy(
  private val minimumIntervalMs: Long = 15 * 60 * 1000L,
) {
  fun shouldScan(lastScanAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Boolean {
    return lastScanAtMs == null || nowMs - lastScanAtMs >= minimumIntervalMs
  }
}
