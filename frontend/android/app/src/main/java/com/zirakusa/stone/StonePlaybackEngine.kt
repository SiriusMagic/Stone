package com.zirakusa.stone

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import kotlin.math.log10
import kotlin.math.roundToInt

object StonePlaybackEngine {
  private const val MAX_BOOST_PERCENT = 300
  private const val NORMAL_VOLUME_PERCENT = 100
  private const val VOLUME_STEP = 5
  private var appContext: ReactApplicationContext? = null
  private var mediaPlayer: MediaPlayer? = null
  private var enhancer: LoudnessEnhancer? = null
  private var volumePercent = 0
  private var playbackRate = 1f
  private var completed = false
  private val handler = Handler(Looper.getMainLooper())
  private val reporter = object : Runnable {
    override fun run() {
      emit()
      handler.postDelayed(this, 250)
    }
  }

  fun initialize(context: ReactApplicationContext) {
    appContext = context
    if (volumePercent <= NORMAL_VOLUME_PERCENT) volumePercent = getAndroidVolumePercent()
  }

  fun load(context: ReactApplicationContext, uri: String, title: String, artist: String, artworkUri: String?, promise: Promise) {
    initialize(context)
    StonePlaybackMetadata.update(uri, title, artist, artworkUri)
    completed = false
    releasePlayer()
    runCatching {
      MediaPlayer().also { player ->
        mediaPlayer = player
        player.setAudioStreamType(AudioManager.STREAM_MUSIC)
        player.setDataSource(context, Uri.parse(uri))
        player.setOnPreparedListener {
          StoneEqualizer.attach(player.audioSessionId)
          applyRate()
          applyBoost()
          emit()
          promise.resolve(status())
        }
        player.setOnCompletionListener {
          completed = true
          emit()
          sendCommand("completed")
          refreshNotification()
        }
        player.setOnErrorListener { _, what, extra ->
          promise.reject("STONE_PLAYBACK", "No se pudo abrir el audio ($what/$extra)")
          true
        }
        player.prepareAsync()
      }
    }.onFailure { promise.reject("STONE_PLAYBACK", it.message, it) }
    handler.removeCallbacks(reporter)
    handler.post(reporter)
  }

  fun play() {
    runCatching {
      mediaPlayer?.let { player ->
        if (completed) player.seekTo(0)
        completed = false
        player.start()
      }
    }
    emit()
    refreshNotification()
  }
  fun pause(updateNotification: Boolean = true) {
    mediaPlayer?.pause(); emit()
    if (updateNotification) refreshNotification()
  }
  fun seekTo(seconds: Double) {
    mediaPlayer?.let { player ->
      val target = (seconds * 1000).roundToInt().coerceIn(0, player.duration.coerceAtLeast(0))
      player.seekTo(target)
      if (target < player.duration) completed = false
    }
    emit()
    refreshNotification()
  }

  fun setPlaybackRate(rate: Float) {
    playbackRate = rate.coerceIn(0.5f, 2f)
    applyRate()
    emit()
  }

  fun setEqualizer(enabled: Boolean, values: IntArray) {
    StoneEqualizer.configure(enabled, values)
  }

  fun isActive() = mediaPlayer != null

  fun getAndroidVolumePercent(): Int {
    val context: Context = currentActivity ?: appContext ?: return 0
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return (manager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max)
      .roundToInt().coerceIn(0, NORMAL_VOLUME_PERCENT)
  }

  fun setAndroidVolumePercent(percent: Int) {
    val context: Context = currentActivity ?: appContext ?: return
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val value = (percent.coerceIn(0, NORMAL_VOLUME_PERCENT) * max / 100f).roundToInt()
    manager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
  }

  fun getEffectiveVolumePercent(): Int {
    if (volumePercent > NORMAL_VOLUME_PERCENT) return volumePercent
    volumePercent = getAndroidVolumePercent()
    return volumePercent
  }

  fun handleVolumeDelta(delta: Int): Boolean {
    val current = getEffectiveVolumePercent()
    val stepped = current + delta * VOLUME_STEP
    val next = when {
      delta > 0 && current < NORMAL_VOLUME_PERCENT -> stepped.coerceAtMost(NORMAL_VOLUME_PERCENT)
      delta < 0 && current > NORMAL_VOLUME_PERCENT -> stepped.coerceAtLeast(NORMAL_VOLUME_PERCENT)
      else -> stepped
    }.coerceIn(0, MAX_BOOST_PERCENT)
    setVolumePercentFromUser(next)
    return true
  }

  fun setVolumePercentFromUser(percent: Int) {
    val value = percent.coerceIn(0, MAX_BOOST_PERCENT)
    if (value <= NORMAL_VOLUME_PERCENT) {
      setAndroidVolumePercent(value)
      volumePercent = getAndroidVolumePercent()
    } else {
      setAndroidVolumePercent(NORMAL_VOLUME_PERCENT)
      volumePercent = value
    }
    applyBoost()
    StoneVolumeOverlay.show(currentActivity, volumePercent)
    emit()
  }

  private fun applyRate() {
    runCatching {
      mediaPlayer?.let { player ->
        player.playbackParams = player.playbackParams.setSpeed(playbackRate)
      }
    }
  }

  private fun applyBoost() {
    val player = mediaPlayer ?: return
    player.setVolume(1f, 1f)
    val gainMb = if (volumePercent <= NORMAL_VOLUME_PERCENT) 0
      else (20 * log10(volumePercent / 100.0) * 1000).roundToInt()
    runCatching {
      if (enhancer == null && player.audioSessionId > 0) enhancer = LoudnessEnhancer(player.audioSessionId)
      enhancer?.setTargetGain(gainMb)
      enhancer?.enabled = gainMb > 0
    }
  }

  fun status() = Arguments.createMap().apply {
    val player = mediaPlayer
    val position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    val duration = runCatching { player?.duration ?: 0 }.getOrDefault(0)
    putBoolean("playing", player?.isPlaying == true)
    putDouble("positionSeconds", position / 1000.0)
    putDouble("durationSeconds", duration.coerceAtLeast(0) / 1000.0)
    putDouble("rate", playbackRate.toDouble())
    putInt("volumePercent", getEffectiveVolumePercent())
  }

  fun notificationState() = StonePlaybackMetadata.snapshot(mediaPlayer, playbackRate)
  fun sendCommand(command: String) { appContext?.let { StonePlaybackModule.emitCommand(it, command) } }

  private fun emit() { appContext?.let { StonePlaybackModule.emit(it) } }
  private fun refreshNotification() {
    appContext?.let { StonePlaybackService.refresh(it, mediaPlayer?.isPlaying == true) }
  }

  private fun releasePlayer() {
    StoneEqualizer.release()
    runCatching { enhancer?.release() }
    enhancer = null
    runCatching { mediaPlayer?.release() }
    mediaPlayer = null
  }

  var currentActivity: android.app.Activity? = null
  fun attachActivity(activity: android.app.Activity) {
    currentActivity = activity
    if (volumePercent <= NORMAL_VOLUME_PERCENT) volumePercent = getAndroidVolumePercent()
  }
  fun detachActivity(activity: android.app.Activity) { if (currentActivity === activity) currentActivity = null }
}
