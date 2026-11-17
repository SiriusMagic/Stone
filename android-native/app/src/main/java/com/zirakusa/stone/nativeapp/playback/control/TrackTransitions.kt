package com.zirakusa.stone.nativeapp.playback.control

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Song-to-song transitions on Stone's single player (no second decoder):
 *
 * - **Sin pausas**: ExoPlayer already joins consecutive files without a gap and trims encoder
 *   padding. On top of that, silence at the start and end of each song is skipped, so dead air
 *   recorded inside the files disappears too.
 * - **Crossfade**: the volume falls during the last N seconds of a song and rises during the
 *   first N seconds of the next one.
 *
 * Runs only while audio is playing and at least one option is on; video is never touched.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TrackTransitions(private val player: ExoPlayer, private val onFade: (Float) -> Unit) {
    var gapless = true; private set
    var crossfade = false; private set
    var crossfadeMs = 5_000L; private set

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var skipping = false
    private var factor = 1f
    private val tick = object : Runnable {
        override fun run() {
            update()
            if (running) handler.postDelayed(this, TICK_MS)
        }
    }
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = update()
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) = update()
    }

    init { player.addListener(listener) }

    fun configure(gapless: Boolean, crossfade: Boolean, crossfadeSeconds: Int) {
        this.gapless = gapless
        this.crossfade = crossfade
        crossfadeMs = crossfadeSeconds.coerceIn(1, 12) * 1_000L
        refresh()
    }

    fun release() {
        stop()
        player.removeListener(listener)
    }

    private fun refresh() {
        val wanted = player.isPlaying && (gapless || crossfade)
        if (wanted && !running) { running = true; handler.post(tick) }
        else if (!wanted && running) stop()
        else update()
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(tick)
        setSkip(false)
        setFactor(1f)
    }

    private fun update() {
        val duration = player.duration
        val position = player.currentPosition
        val audioOnly = !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        if (!audioOnly || duration == C.TIME_UNSET || duration <= 0) {
            setSkip(false); setFactor(1f); return
        }
        val remaining = duration - position
        setSkip(gapless && (position < EDGE_START_MS || remaining < EDGE_END_MS))
        setFactor(if (crossfade) TransitionMath.fade(position, duration, crossfadeMs, hasFollower()) else 1f)
    }

    /** A song follows when the queue continues or repeats; the last song of the queue just ends. */
    private fun hasFollower() = player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF

    private fun setSkip(value: Boolean) {
        if (skipping == value) return
        skipping = value
        runCatching { player.skipSilenceEnabled = value }
    }

    private fun setFactor(value: Float) {
        if (factor == value) return
        factor = value
        onFade(value)
    }

    private companion object {
        const val TICK_MS = 100L
        /** Silence is skipped only near the edges so quiet passages inside a song stay intact. */
        const val EDGE_START_MS = 4_000L
        const val EDGE_END_MS = 6_000L
    }
}

/** Pure fade curve, kept apart so it can be unit tested. */
object TransitionMath {
    /** Volume multiplier (0..1) at [position] of a song of [duration] with fades of [fadeMs]. */
    fun fade(position: Long, duration: Long, fadeMs: Long, hasFollower: Boolean): Float {
        if (fadeMs <= 0 || duration <= 0) return 1f
        // Never fade more than a third of a short song on each side.
        val window = fadeMs.coerceAtMost(duration / 3).coerceAtLeast(1)
        val fadeIn = (position.toFloat() / window).coerceIn(0f, 1f)
        val fadeOut = if (hasFollower) ((duration - position).toFloat() / window).coerceIn(0f, 1f) else 1f
        // Equal-power curve: sounds even across the change instead of dipping in the middle.
        val linear = minOf(fadeIn, fadeOut)
        return kotlin.math.sin(linear * Math.PI / 2).toFloat()
    }
}
