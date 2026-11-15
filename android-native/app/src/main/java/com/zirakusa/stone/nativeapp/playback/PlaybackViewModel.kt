package com.zirakusa.stone.nativeapp.playback

import android.app.Application
import android.content.ComponentName
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.data.persistence.PlaybackDataStore
import com.zirakusa.stone.nativeapp.data.persistence.SavedPlayback
import com.zirakusa.stone.nativeapp.data.persistence.SavedQueueItem
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class QueueEntry(val id: String, val title: String, val artist: String, val uri: String? = null)
/**
 * [gain] is a software attenuation applied on top of Android's volume. Dormilón uses 0.1
 * (-20 dB): the whole volume range maps to 0-10 % of the normal output, so even Android's
 * lowest step becomes quiet enough for listening in bed next to someone sleeping.
 */
enum class AudioMode(val key: String, val defaultMaxPercent: Int, val defaultGain: Float = 1f) {
    BATTERY("battery", 100), BALANCED("balanced", 150), HIFI("hifi", 300),
    SLEEPY("sleepy", 100, defaultGain = 0.1f),
    /** The user's own level. */
    CUSTOM("custom", 150);

    /** Effective values: the defaults unless changed in Ajustes › Niveles de audio. */
    val maxVolumePercent: Int get() = AudioLevels.maxPercent(this)
    val gain: Float get() = AudioLevels.gain(this)
}

/**
 * Per-mode volume ceiling and software gain. [gain] is applied on top of Android's volume
 * (Dormilón defaults to 0.1 = -20 dB so even the lowest step is soft). Ahorro always keeps
 * Android's plain 100 % because it must stay effect-free.
 */
object AudioLevels {
    private val max = java.util.concurrent.ConcurrentHashMap<AudioMode, Int>()
    private val gains = java.util.concurrent.ConcurrentHashMap<AudioMode, Float>()
    val adjustable = listOf(AudioMode.SLEEPY, AudioMode.BALANCED, AudioMode.HIFI, AudioMode.CUSTOM)

    fun maxPercent(mode: AudioMode) = max[mode] ?: mode.defaultMaxPercent
    fun gain(mode: AudioMode) = gains[mode] ?: mode.defaultGain

    fun load(store: com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore) {
        adjustable.forEach { mode ->
            store.look("audio.${mode.key}.max", "").toIntOrNull()?.let { max[mode] = it.coerceIn(10, 300) }
            store.look("audio.${mode.key}.gain", "").toFloatOrNull()?.let { gains[mode] = it.coerceIn(.01f, 1f) }
        }
    }

    fun set(store: com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore?, mode: AudioMode,
        maxPercent: Int, gain: Float) {
        if (mode !in adjustable) return
        max[mode] = maxPercent.coerceIn(10, 300); gains[mode] = gain.coerceIn(.01f, 1f)
        store?.setLook("audio.${mode.key}.max", max.getValue(mode).toString())
        store?.setLook("audio.${mode.key}.gain", gains.getValue(mode).toString())
    }

    fun reset(store: com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore?) {
        max.clear(); gains.clear()
        adjustable.forEach { store?.setLook("audio.${it.key}.max", ""); store?.setLook("audio.${it.key}.gain", "") }
    }
}

enum class HardwareAcceleration(val key: String) {
    AUTOMATIC("automatic"), DISABLED("disabled"), DECODING("decoding"), FULL("full"), ADAPTIVE("adaptive")
}

data class PlaybackState(val ready: Boolean = false, val title: String = "", val artist: String = "",
    val playing: Boolean = false, val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(), val index: Int = -1, val failed: Boolean = false,
    val currentId: String = "", val favorite: Boolean = false, val speed: Float = 1f,
    val equalizerEnabled: Boolean = false, val equalizerValues: List<Int> = List(10) { 0 },
    val equalizerPreset: String = "Personalizado", val sleepMinutes: Int = 0,
    val sleepEndAtMs: Long = 0L,
    val sleepFinishTrack: Boolean = false, val sleepFadeOut: Boolean = true,
    /** Last duration set through "Tiempo personalizado", to prefill the picker. */
    val sleepCustomMinutes: Int = 30, val volumePercent: Int = 100,
    val audioMode: AudioMode = AudioMode.BALANCED,
    val hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.AUTOMATIC)

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private data class ArtworkSource(val uri: String, val modifiedMs: Long)

    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private var controller: MediaController? = null
    private val data = PlaybackDataStore(application)
    private val settings = StoneSettingsStore(application)
    private val equalizer = PlaybackEqualizer()
    private val analyzer = AudioGenreAnalyzer(application)
    private var playWithOthers = settings.read().playWithOthers
    private var audioFocusPolicy = if (playWithOthers) {
        "Media3: handleAudioFocus=false (reproducir con otras apps)"
    } else {
        "Media3: handleAudioFocus=true"
    }
    private var speed = data.float("speed", 1f)
    private var equalizerEnabled = data.boolean("equalizer-enabled", false)
    private var equalizerValues = IntArray(10) { data.int("equalizer-$it", 0) }
    private var equalizerPreset = data.string("equalizer-preset", "Personalizado")
    private var volumePercent = 100
    private var audioMode = runCatching { AudioMode.valueOf(settings.read().audioMode.uppercase()) }
        .getOrDefault(AudioMode.BALANCED)
    private var hardwareAcceleration = runCatching {
        HardwareAcceleration.valueOf(settings.read().hardwareAcceleration.uppercase())
    }.getOrDefault(HardwareAcceleration.AUTOMATIC)
    private var sleepMinutes = 0
    private var sleepEndAtMs = 0L
    private var sleepFinishTrack = settings.read().sleepFinishTrack
    private var sleepFadeOut = settings.read().sleepFadeOut
    private var sleepCustomMinutes = settings.read().sleepMinutes.takeIf { it > 0 } ?: 30
    private var sleepJob: Job? = null
    /** Volume the player had before a sleep fade started; restored after the pause. */
    private var volumeBeforeFade: Float? = null
    private var systemArtworkJob: Job? = null
    private var systemArtworkGeneration = 0L
    private val systemArtworkSources = mutableMapOf<String, ArtworkSource>()
    private var manualShuffle = false
    private var originalQueueIds: List<String> = emptyList()

    private fun diagnosticState(player: Player? = controller): AudioDiagnostics.State {
        val serviceState = StonePlaybackService.diagnosticState(player, audioFocusPolicy)
        return serviceState.copy(effects = "${serviceState.effects};${equalizer.diagnostics()}")
    }

    private val future = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, StonePlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            publish()
            requestSystemArtwork()
            AudioDiagnostics.log(getApplication(), "MediaController.onMediaItemTransition(reason=$reason)",
                state = diagnosticState(controller))
        }
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioDiagnostics.log(getApplication(),
                "MediaController.onAudioSessionIdChanged($audioSessionId)",
                state = diagnosticState(controller), includeSystemEffects = true)
        }
        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
            AudioDiagnostics.log(getApplication(),
                "MediaController.onAudioAttributesChanged(usage=${audioAttributes.usage}," +
                    "content=${audioAttributes.contentType},flags=${audioAttributes.flags})",
                state = diagnosticState(controller))
        }
        override fun onVolumeChanged(volume: Float) {
            AudioDiagnostics.log(getApplication(), "MediaController.onVolumeChanged($volume)",
                state = diagnosticState(controller))
        }
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) {
            AudioDiagnostics.log(getApplication(), "MediaController.onDeviceInfoChanged",
                state = diagnosticState(controller), includeSystemEffects = true)
        }
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            AudioDiagnostics.log(getApplication(),
                "MediaController.onDeviceVolumeChanged(volume=$volume,muted=$muted)",
                state = diagnosticState(controller))
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            AudioDiagnostics.log(getApplication(), "MediaController.onPlaybackStateChanged($playbackState)",
                state = diagnosticState(controller))
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            AudioDiagnostics.log(getApplication(), "MediaController.onIsPlayingChanged($isPlaying)",
                state = diagnosticState(controller))
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            AudioDiagnostics.log(getApplication(),
                "MediaController.onPlayWhenReadyChanged(ready=$playWhenReady,reason=$reason)",
                state = diagnosticState(controller))
        }
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            AudioDiagnostics.log(getApplication(),
                "MediaController.onPlaybackSuppressionReasonChanged($playbackSuppressionReason)",
                state = diagnosticState(controller))
        }
        override fun onPlayerError(error: PlaybackException) {
            PerformanceDiagnostics.recordPlaybackError()
            AudioDiagnostics.log(getApplication(),
                "MediaController.onPlayerError(${error.errorCodeName})",
                state = diagnosticState(controller), includeSystemEffects = true)
            publish()
        }
    }

    init {
        viewModelScope.launch {
            StonePlaybackService.volumeUi.collect { volume ->
                if (volume.revision > 0) {
                    volumePercent = volume.percent
                    publish()
                }
            }
        }
        future.addListener({
            val before = AudioDiagnostics.capture(getApplication(), diagnosticState(player = null))
            runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                it.setPlaybackSpeed(speed)
                applyAudioMixing(it, playWithOthers)
                StonePlaybackService.instance?.applyAudioMode(audioMode)
                StonePlaybackService.instance?.applyHardwareAcceleration(hardwareAcceleration)
                if (it.mediaItemCount == 0) restoreQueue(it)
                configureEqualizer(equalizerEnabled)
                AudioDiagnostics.log(getApplication(), "MediaController.connected", before,
                    diagnosticState(it), includeSystemEffects = true)
                publish()
            }.onFailure { mutable.value = PlaybackState(failed = true) }
        }, ContextCompat.getMainExecutor(application))
    }

    private fun publish() {
        val player = controller ?: return
        mutable.value = PlaybackState(ready = true,
            title = player.mediaMetadata.title?.toString().orEmpty(),
            artist = player.mediaMetadata.artist?.toString().orEmpty(), playing = player.isPlaying,
            shuffle = manualShuffle || player.shuffleModeEnabled, repeat = player.repeatMode,
            queue = List(player.mediaItemCount) {
                val item = player.getMediaItemAt(it)
                QueueEntry(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(),
                    item.mediaMetadata.artist?.toString().orEmpty(),
                    item.localConfiguration?.uri?.toString())
            }, index = player.currentMediaItemIndex, failed = player.playerError != null,
            currentId = player.currentMediaItem?.mediaId.orEmpty(),
            favorite = data.isFavorite(player.currentMediaItem?.mediaId.orEmpty()),
            speed = speed, equalizerEnabled = equalizerEnabled,
            equalizerValues = equalizerValues.toList(), equalizerPreset = equalizerPreset,
            sleepMinutes = sleepMinutes, sleepEndAtMs = sleepEndAtMs,
            sleepFinishTrack = sleepFinishTrack, sleepFadeOut = sleepFadeOut,
            sleepCustomMinutes = sleepCustomMinutes,
            volumePercent = volumePercent, audioMode = audioMode,
            hardwareAcceleration = hardwareAcceleration)
    }

    fun play(song: Song, songs: List<Song>) {
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        prepareSystemArtworkSources(songs)
        originalQueueIds = songs.map { it.id }
        manualShuffle = false
        controller?.apply {
            shuffleModeEnabled = false
            setMediaItems(songs.map(::mediaItem), index, 0)
            prepare()
            play()
        }
        requestSystemArtwork(index)
    }
    fun toggle() { controller?.run { if (isPlaying) pause() else { prepare(); play() } } }
    fun setVolumePercent(value: Int) { StonePlaybackService.setVolumePercent(value) }
    fun setAudioMode(mode: AudioMode) {
        audioMode = mode
        volumePercent = volumePercent.coerceAtMost(mode.maxVolumePercent)
        settings.setAudioMode(mode.key)
        if (mode == AudioMode.BATTERY) {
            hardwareAcceleration = HardwareAcceleration.ADAPTIVE
            settings.setHardwareAcceleration(hardwareAcceleration.key)
        }
        configureEqualizer(equalizerEnabled)
        StonePlaybackService.instance?.applyAudioMode(mode)
        if (mode == AudioMode.BATTERY) {
            StonePlaybackService.instance?.applyHardwareAcceleration(hardwareAcceleration)
        }
        publish()
    }
    /** Applies a user-facing playback profile, including its hardware acceleration policy. */
    fun setPlaybackProfile(mode: AudioMode, acceleration: HardwareAcceleration) {
        audioMode = mode
        hardwareAcceleration = acceleration
        volumePercent = volumePercent.coerceAtMost(mode.maxVolumePercent)
        settings.setAudioMode(mode.key)
        settings.setHardwareAcceleration(acceleration.key)
        configureEqualizer(equalizerEnabled)
        StonePlaybackService.instance?.applyAudioMode(mode)
        StonePlaybackService.instance?.applyHardwareAcceleration(acceleration)
        publish()
    }
    fun setHardwareAcceleration(mode: HardwareAcceleration) {
        hardwareAcceleration = mode
        settings.setHardwareAcceleration(mode.key)
        StonePlaybackService.instance?.applyHardwareAcceleration(mode)
        publish()
    }
    fun previous() { controller?.seekToPrevious() }
    fun next() { controller?.seekToNext() }
    fun shuffle() { setShuffle(!mutable.value.shuffle) }
    fun repeat() { setRepeat((mutable.value.repeat + 1) % 3) }
    fun setRepeat(mode: Int) {
        val player = controller ?: return
        val current = player.currentMediaItemIndex
        if (current > 0) player.moveMediaItem(current, 0)
        if (player.mediaItemCount > 0 && !manualShuffle) originalQueueIds = queueIds(player)
        player.repeatMode = mode
    }
    fun setPlayWithOthers(enabled: Boolean) {
        playWithOthers = enabled
        settings.setBoolean("playWithOthers", enabled)
        controller?.let { applyAudioMixing(it, enabled) }
    }
    private fun applyAudioMixing(player: Player, enabled: Boolean) {
        val before = AudioDiagnostics.capture(getApplication(), diagnosticState(player))
        audioFocusPolicy = if (enabled) {
            "Media3: handleAudioFocus=false (reproducir con otras apps)"
        } else {
            "Media3: handleAudioFocus=true"
        }
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), !enabled)
        AudioDiagnostics.log(getApplication(), "MediaController.applyAudioMixing(enabled=$enabled)",
            before, diagnosticState(player))
    }
    fun setShuffle(enabled: Boolean) {
        val player = controller ?: return
        if (enabled == (manualShuffle || player.shuffleModeEnabled)) return
        val currentId = player.currentMediaItem?.mediaId ?: return
        if (enabled) {
            if (originalQueueIds.isEmpty()) originalQueueIds = queueIds(player)
            val randomized = listOf(currentId) + queueIds(player).filterNot { it == currentId }.shuffled()
            applyQueueOrder(player, randomized)
            player.shuffleModeEnabled = false
            manualShuffle = true
        } else {
            val restored = listOf(currentId) + originalQueueIds.filterNot { it == currentId }
            applyQueueOrder(player, restored)
            player.shuffleModeEnabled = false
            manualShuffle = false
            originalQueueIds = restored
        }
        publish()
    }
    fun playAt(index: Int) {
        controller?.takeIf { index in 0 until it.mediaItemCount }?.run {
            seekToDefaultPosition(index); play()
        }
    }
    fun move(from: Int, to: Int) {
        val player = controller ?: return
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount || from == to) return
        player.moveMediaItem(from, to)
        originalQueueIds = queueIds(player)
        publish()
    }

    fun removeAt(index: Int) {
        controller?.takeIf { index in 0 until it.mediaItemCount }?.run {
            val removedId = getMediaItemAt(index).mediaId
            removeMediaItem(index)
            systemArtworkSources.remove(removedId)
            originalQueueIds = queueIds(this)
            publish()
        }
    }

    fun clearQueue() {
        controller?.let {
            it.clearMediaItems()
            cancelSystemArtworkRequests(clearSources = true)
            originalQueueIds = emptyList()
            manualShuffle = false
            data.saveQueue(SavedPlayback(emptyList(), 0, 0L, false, Player.REPEAT_MODE_OFF))
            publish()
        }
    }

    private fun queueIds(player: Player): List<String> = List(player.mediaItemCount) {
        player.getMediaItemAt(it).mediaId
    }

    private fun applyQueueOrder(player: Player, ids: List<String>) {
        ids.forEachIndexed { target, id ->
            val from = (target until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == id
            } ?: return@forEachIndexed
            if (from != target) player.moveMediaItem(from, target)
        }
    }
    fun toggleFavorite() {
        val id = controller?.currentMediaItem?.mediaId.orEmpty()
        if (id.isEmpty()) return
        data.toggleFavorite(id)
        publish()
    }
    fun seek(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0)) }
    fun setSpeed(value: Float) {
        speed = value.coerceIn(.5f, 2f)
        data.put("speed", speed)
        controller?.setPlaybackSpeed(speed)
        publish()
    }
    fun setEqualizerEnabled(value: Boolean) {
        equalizerEnabled = value
        data.put("equalizer-enabled", value)
        configureEqualizer(value); publish()
    }
    fun analyzeAndApply(song: Song, result: (AudioAnalysis) -> Unit = {}) {
        viewModelScope.launch {
            val analysis = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                analyzer.analyze(song.uri)
            }
            analysis.genre?.let { genre ->
                val values = genrePreset(genre)
                if (values != null) setEqualizerPreset(genre, values)
            }
            result(analysis)
        }
    }
    fun setEqualizerBand(index: Int, value: Int) {
        if (index !in equalizerValues.indices) return
        equalizerValues[index] = value.coerceIn(-10, 10)
        equalizerEnabled = true; equalizerPreset = "Personalizado"
        data.put("equalizer-$index", equalizerValues[index]); data.put("equalizer-enabled", true)
        data.put("equalizer-preset", equalizerPreset)
        configureEqualizer(true); publish()
    }
    fun setEqualizerPreset(name: String, values: List<Int>) {
        equalizerPreset = name
        equalizerValues = IntArray(10) { values.getOrElse(it) { 0 }.coerceIn(-10, 10) }
        equalizerEnabled = true
        data.put("equalizer-enabled", true); data.put("equalizer-preset", name)
        equalizerValues.forEachIndexed { index, value -> data.put("equalizer-$index", value) }
        configureEqualizer(true); publish()
    }

    /** Reconnects lazily because Android may expose the audio session before its effects route. */
    private fun configureEqualizer(enabled: Boolean) {
        val before = AudioDiagnostics.capture(getApplication(), diagnosticState(), includeSystemEffects = true)
        equalizer.attach(StonePlaybackService.playbackAudioSessionId)
        // The battery profile keeps the user's EQ values, but suspends the effect while active.
        equalizer.configure(enabled && audioMode != AudioMode.BATTERY, equalizerValues)
        AudioDiagnostics.log(getApplication(), "PlaybackEqualizer.configure(enabled=$enabled,mode=${audioMode.key})",
            before, diagnosticState(), includeSystemEffects = true)
    }
    private fun genrePreset(genre: String): List<Int>? = when (genre) {
        "Electrónica" -> listOf(4,7,6,-1,-2,0,5,8,3,5)
        "Rock" -> listOf(5,3,2,0,0,1,0,2,2,4)
        "HipHop" -> listOf(5,5,3,0,-2,-2,2,3,4,4)
        "R&B" -> listOf(5,5,4,0,-2,-1,2,2,2,3)
        "Jazz" -> listOf(3,3,2,0,-1,-1,0,2,3,4)
        "Clásico" -> listOf(0,1,-2,0,-1,0,2,-2,-2,3)
        "Folk" -> listOf(0,2,3,0,0,0,6,3,2,-1)
        "Latín" -> listOf(1,3,2,0,0,-1,0,-1,0,3)
        "Pop" -> listOf(-1,-1,0,1,1,2,2,1,0,-1)
        "Baile" -> listOf(5,5,4,2,1,3,4,3,2,0)
        else -> null
    }
    fun setSleepTimer(minutes: Int, finishTrack: Boolean = sleepFinishTrack) {
        sleepFinishTrack = finishTrack
        scheduleSleep(minutes.coerceAtLeast(0) * 60_000L)
    }
    /** Custom duration from the picker; remembered for next time. */
    fun setCustomSleepTimer(minutes: Int) {
        if (minutes > 0) { sleepCustomMinutes = minutes; settings.setSleepMinutes(minutes) }
        setSleepTimer(minutes)
    }
    /** Adds [minutes] to a running timer (or starts one when none is active). */
    fun extendSleepTimer(minutes: Int) {
        val remaining = (sleepEndAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        scheduleSleep(remaining + minutes.coerceAtLeast(0) * 60_000L)
    }
    /** Pauses when the current song ends, whatever is left of it. */
    fun sleepAtEndOfTrack() {
        val (position, duration) = progress()
        sleepFinishTrack = true
        scheduleSleep((duration - position).coerceAtLeast(1_000L), fade = false)
    }
    fun setSleepFinishTrack(value: Boolean) { sleepFinishTrack = value; settings.setBoolean("sleepFinishTrack", value); publish() }
    fun setSleepFadeOut(value: Boolean) { sleepFadeOut = value; settings.setBoolean("sleepFadeOut", value); publish() }

    private fun scheduleSleep(durationMs: Long, fade: Boolean = sleepFadeOut) {
        sleepJob?.cancel()
        restoreVolumeAfterFade()
        sleepMinutes = ((durationMs + 59_999L) / 60_000L).toInt()
        sleepEndAtMs = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
        if (durationMs > 0) sleepJob = viewModelScope.launch {
            val fadeMs = if (fade && !sleepFinishTrack) SLEEP_FADE_MS.coerceAtMost(durationMs) else 0L
            delay(durationMs - fadeMs)
            if (fadeMs > 0) fadeOut(fadeMs)
            if (sleepFinishTrack) waitForTrackEnd()
            controller?.pause()
            restoreVolumeAfterFade()
            sleepMinutes = 0
            sleepEndAtMs = 0L
            publish()
        }
        publish()
    }
    private suspend fun fadeOut(fadeMs: Long) {
        val player = controller ?: return
        val start = player.volume.also { volumeBeforeFade = it }
        val steps = 30
        repeat(steps) { step ->
            delay(fadeMs / steps)
            player.volume = start * (1f - (step + 1f) / steps)
        }
    }
    private fun restoreVolumeAfterFade() {
        volumeBeforeFade?.let { controller?.volume = it }
        volumeBeforeFade = null
    }
    private suspend fun waitForTrackEnd() {
        val player = controller ?: return
        val previousRepeat = player.repeatMode
        player.repeatMode = Player.REPEAT_MODE_ONE
        var previous = player.currentPosition
        while (true) {
            delay(200)
            val current = player.currentPosition
            if (current + 1_000 < previous || player.playbackState == Player.STATE_ENDED) break
            previous = current
        }
        player.repeatMode = previousRepeat
    }
    fun remove(id: String) {
        val player = controller ?: return
        for (index in player.mediaItemCount - 1 downTo 0) if (player.getMediaItemAt(index).mediaId == id)
            player.removeMediaItem(index)
        systemArtworkSources.remove(id)
    }
    fun progress(): Pair<Long, Long> = controller?.let {
        it.currentPosition.coerceAtLeast(0) to it.duration.coerceAtLeast(0)
    } ?: (0L to 0L)
    fun player(): Player? = controller
    fun persist() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            data.saveQueue(SavedPlayback(emptyList(), 0, 0L, false, Player.REPEAT_MODE_OFF))
            return
        }
        data.saveQueue(SavedPlayback(List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index); val metadata = item.mediaMetadata
            val mediaUri = item.localConfiguration?.uri?.toString().orEmpty()
            val artwork = systemArtworkSources[item.mediaId]
                ?.takeIf { it.uri.isNotBlank() && it.uri != mediaUri }
            SavedQueueItem(item.mediaId, mediaUri,
                metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(),
                metadata.albumTitle?.toString().orEmpty(), artwork?.uri.orEmpty(),
                artwork?.modifiedMs ?: 0L)
        }, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0),
            manualShuffle || player.shuffleModeEnabled, player.repeatMode))
    }

    private fun restoreQueue(player: MediaController) {
        val saved = data.queue()?.takeIf { it.items.isNotEmpty() } ?: return
        val media = saved.items.filter { it.uri.isNotEmpty() }.map { item ->
            val metadata = MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
                .setAlbumTitle(item.album)
            if (item.artworkUri.isNotBlank()) metadata.setArtworkUri(item.artworkUri.toUri())
            MediaItem.Builder().setMediaId(item.id).setUri(item.uri)
                .setMediaMetadata(metadata.build()).build()
        }
        if (media.isEmpty()) return
        cancelSystemArtworkRequests(clearSources = true)
        saved.items.filter { it.uri.isNotEmpty() }.forEach { item ->
            systemArtworkSources[item.id] = ArtworkSource(
                item.artworkUri.ifBlank { item.uri }, item.modifiedMs)
        }
        originalQueueIds = media.map { it.mediaId }
        manualShuffle = saved.shuffle
        player.setMediaItems(media, saved.index.coerceIn(0, media.lastIndex), saved.position.coerceAtLeast(0))
        player.shuffleModeEnabled = false; player.repeatMode = saved.repeat; player.prepare()
        requestSystemArtwork()
    }

    /** Inserts [songs] right after the current item ("Reproducir a continuación"). */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) { play(songs.first(), songs); return }
        prepareSystemArtworkSources(songs)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map(::mediaItem))
    }

    private fun mediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .setAlbumTitle(song.album)
        // An extracted cover is a safe fallback while the derived watermark is
        // being prepared. Embedded artwork is loaded by ArtworkLoader below.
        if (song.artworkUri.isNotBlank()) metadata.setArtworkUri(song.artworkUri.toUri())
        return MediaItem.Builder().setMediaId(song.id).setUri(song.uri)
            .setMediaMetadata(metadata.build()).build()
    }

    private fun prepareSystemArtworkSources(songs: List<Song>) {
        cancelSystemArtworkRequests(clearSources = true)
        songs.forEach { song ->
            systemArtworkSources[song.id] = ArtworkSource(song.artworkSource, song.modifiedMs)
        }
    }

    /**
     * Media3's default notification provider reads MediaMetadata.artworkData.
     * The player still owns the original media file; only the immutable media
     * item metadata is replaced with the cached, derived image for Android's
     * system controls.
     */
    private fun requestSystemArtwork(index: Int = controller?.currentMediaItemIndex ?: -1) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        if (item.mediaMetadata.artworkData != null) return
        val source = systemArtworkSources[item.mediaId]
            ?: ArtworkSource(item.localConfiguration?.uri?.toString().orEmpty(), 0L)
        if (source.uri.isBlank()) return
        val generation = systemArtworkGeneration
        systemArtworkJob?.cancel()
        systemArtworkJob = viewModelScope.launch {
            val artwork = SystemArtworkOverlay.load(getApplication(), source.uri, source.modifiedMs)
                ?: return@launch
            if (generation != systemArtworkGeneration) return@launch
            val activePlayer = controller ?: return@launch
            val activeIndex = activePlayer.currentMediaItemIndex
            if (activeIndex !in 0 until activePlayer.mediaItemCount) return@launch
            val activeItem = activePlayer.getMediaItemAt(activeIndex)
            if (activeItem.mediaId != item.mediaId || activeItem.mediaMetadata.artworkData != null) return@launch
            val metadata = activeItem.mediaMetadata.buildUpon()
                .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
            // ExoPlayer keeps the current URI and playback position when a
            // playlist item is replaced with the same media source.
            activePlayer.replaceMediaItem(activeIndex,
                activeItem.buildUpon().setMediaMetadata(metadata).build())
        }
    }

    private fun cancelSystemArtworkRequests(clearSources: Boolean = false) {
        systemArtworkGeneration++
        systemArtworkJob?.cancel()
        systemArtworkJob = null
        if (clearSources) systemArtworkSources.clear()
    }

    override fun onCleared() {
        AudioDiagnostics.log(getApplication(), "PlaybackViewModel.onCleared", state = diagnosticState(),
            includeSystemEffects = true)
        cancelSystemArtworkRequests(clearSources = true)
        persist()
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        equalizer.release()
    }

    private companion object {
        /** Length of the volume fade before the sleep timer pauses. */
        const val SLEEP_FADE_MS = 30_000L
    }
}
