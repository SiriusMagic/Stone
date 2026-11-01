package com.zirakusa.stone

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import kotlin.math.roundToInt
import com.facebook.react.modules.core.DeviceEventManagerModule

class StonePlaybackModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  init { StonePlaybackEngine.initialize(context) }

  override fun getName() = "StonePlayback"

  @ReactMethod fun load(uri: String, title: String, artist: String, artworkUri: String?, promise: Promise) =
    StonePlaybackEngine.load(context, uri, title, artist, artworkUri, promise)
  @ReactMethod fun play() = StonePlaybackEngine.play()
  @ReactMethod fun pause() = StonePlaybackEngine.pause()
  @ReactMethod fun seekTo(seconds: Double) = StonePlaybackEngine.seekTo(seconds)
  @ReactMethod fun setPlaybackRate(rate: Double) = StonePlaybackEngine.setPlaybackRate(rate.toFloat())
  @ReactMethod fun setEqualizer(enabled: Boolean, values: ReadableArray) =
    StonePlaybackEngine.setEqualizer(enabled, IntArray(values.size()) { values.getDouble(it).roundToInt() })
  @ReactMethod fun setQueue(items: ReadableArray, currentIndex: Int) {
    val queue = (0 until items.size()).map { index ->
      val item = items.getMap(index)
      StoneQueueEntry(item?.getString("id").orEmpty(), item?.getString("title").orEmpty(), item?.getString("artist").orEmpty())
    }
    StonePlaybackService.setQueue(context, queue, currentIndex)
  }
  @ReactMethod fun setVolumePercentFromUser(percent: Int, promise: Promise) {
    StonePlaybackEngine.setVolumePercentFromUser(percent)
    promise.resolve(StonePlaybackEngine.status())
  }
  @ReactMethod fun getStatus(promise: Promise) = promise.resolve(StonePlaybackEngine.status())
  @ReactMethod fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) {}
  @ReactMethod fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Int) {}

  companion object {
    fun emit(context: ReactApplicationContext) {
      context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("StonePlaybackStatus", StonePlaybackEngine.status())
    }

    fun emitCommand(context: ReactApplicationContext, command: String) {
      context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("StonePlaybackCommand", command)
    }
  }
}
