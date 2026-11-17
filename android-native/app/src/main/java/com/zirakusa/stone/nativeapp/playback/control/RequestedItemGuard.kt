package com.zirakusa.stone.nativeapp.playback.control

import android.os.SystemClock

/**
 * Keeps the song the user just tapped as the current one while the player loads the new list.
 * Meanwhile the media controller can report other items for an instant (the first one of the
 * list, then the tapped one again), which made the library highlight jump. The tapped id is
 * shown until the player has that item loaded and ready, or [holdMs] passes (e.g. a broken file).
 */
internal class RequestedItemGuard(private val holdMs: Long = 3_000L,
    private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private var id: String? = null
    private var until = 0L

    fun request(id: String) { this.id = id; until = clock() + holdMs }
    /** Another action (next, previous, queue tap) takes over: show the player as it is. */
    fun clear() { id = null }

    fun shown(actualId: String, ready: Boolean): String {
        val wanted = id ?: return actualId
        if ((actualId == wanted && ready) || clock() > until) { id = null; return actualId }
        return wanted
    }
}
