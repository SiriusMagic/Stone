package com.zirakusa.stone.nativeapp.playback

/** Hardware keys use small steps for normal volume and deliberate presses for boost. */
internal fun nextVolumeKeyLevel(current: Int, direction: Int, maxPercent: Int, repeated: Boolean): Int {
    val maximum = maxPercent.coerceIn(100, 300)
    val value = current.coerceIn(0, maximum)
    if (direction == 0) return value
    val levels = listOf(100, 150, 200, 300).filter { it <= maximum }
    if (direction > 0) {
        if (value < 100) return (value + 5).coerceAtMost(100)
        if (repeated) return value
        return levels.firstOrNull { it > value } ?: maximum
    }
    if (value > 100) {
        if (repeated) return value
        return levels.lastOrNull { it < value } ?: 100
    }
    return (value - 5).coerceAtLeast(0)
}
