package com.zirakusa.stone.nativeapp.playback

import android.app.Application
import android.content.ComponentName
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
enum class AudioMode(val key: String) { BATTERY("battery"), BALANCED("balanced"), HIFI("hifi") }
enum class HardwareAcceleration(val key: String) {
    AUTOMATIC("automatic"), DISABLED("disabled"), DECODING("decoding"), FULL("full"), ADAPTIVE("adaptive")
}

data class PlaybackState(val ready: Boolean = false, val title: String = "", val artist: String = "",
    val playing: Boolean = false, val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(), val index: Int = -1, val failed: Boolean = false,
    val currentId: String = "", val favorite: Boolean = false, val speed: Float = 1f,
    val equalizerEnabled: Boolean = false, val equalizerValues: List<Int> = List(10) { 0 },
    val equalizerPreset: String = "Personalizado", val sleepMinutes: Int = 0,
    val sleepFinishTrack: Boolean = false, val volumePercent: Int = 100,
    val audioMode: AudioMode = AudioMode.BALANCED,
    val hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.AUTOMATIC)

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    private var controller: MediaController? = null
    private val data = PlaybackDataStore(application)
    private val settings = StoneSettingsStore(application)
    private val equalizer = PlaybackEqualizer()
    private var speed = data.float("speed", 1f)
    private var equalizerEnabled = data.boolean("equalizer-enabled", false)
    private var equalizerValues = IntArray(10) { data.int("equalizer-$it", 0) }
    private var equalizerPreset = data.string("equalizer-preset", "Personalizado")
    private var volumePercent = data.int("volume-percent", 100).coerceIn(0, 300)
    private var audioMode = runCatching { AudioMode.valueOf(settings.read().audioMode.uppercase()) }
        .getOrDefault(AudioMode.BALANCED)
    private var hardwareAcceleration = runCatching {
        HardwareAcceleration.valueOf(settings.read().hardwareAcceleration.uppercase())
    }.getOrDefault(HardwareAcceleration.AUTOMATIC)
    private var sleepMinutes = 0
    private var sleepFinishTrack = false
    private var sleepJob: Job? = null
    private var manualShuffle = false
    private var originalQueueIds: List<String> = emptyList()
    private val future = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, StonePlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onPlayerError(error: PlaybackException) = publish()
    }

    init {
        viewModelScope.launch {
            StonePlaybackService.volumeUi.collect { volume ->
                if (volume.revision > 0) {
                    volumePercent = volume.percent
                    data.put("volume-percent", volumePercent)
                    publish()
                }
            }
        }
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                it.setPlaybackSpeed(speed)
                applyAudioMixing(it, settings.read().playWithOthers)
                StonePlaybackService.setVolumePercent(volumePercent, showOverlay = false)
                StonePlaybackService.instance?.applyAudioMode(audioMode)
                if (it.mediaItemCount == 0) restoreQueue(it)
                equalizer.attach(StonePlaybackService.playbackAudioSessionId)
                equalizer.configure(audioMode != AudioMode.BATTERY && equalizerEnabled, equalizerValues)
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
            sleepMinutes = sleepMinutes, sleepFinishTrack = sleepFinishTrack,
            volumePercent = volumePercent, audioMode = audioMode,
            hardwareAcceleration = hardwareAcceleration)
    }

    fun play(song: Song, songs: List<Song>) {
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        originalQueueIds = songs.map { it.id }
        manualShuffle = false
        controller?.apply {
            shuffleModeEnabled = false
            setMediaItems(songs.map { MediaItem.Builder().setMediaId(it.id).setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist)
                    .setAlbumTitle(it.album).build()).build() }, index, 0)
            prepare()
            play()
        }
    }
    fun toggle() { controller?.run { if (isPlaying) pause() else { prepare(); play() } } }
    fun setVolumePercent(value: Int) { StonePlaybackService.setVolumePercent(value) }
    fun setAudioMode(mode: AudioMode) {
        audioMode = mode
        settings.setAudioMode(mode.key)
        when (mode) {
            AudioMode.BATTERY -> equalizer.configure(false, equalizerValues)
            AudioMode.BALANCED, AudioMode.HIFI -> equalizer.configure(equalizerEnabled, equalizerValues)
        }
        StonePlaybackService.instance?.applyAudioMode(mode)
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
        settings.setBoolean("playWithOthers", enabled)
        controller?.let { applyAudioMixing(it, enabled) }
    }
    private fun applyAudioMixing(player: Player, enabled: Boolean) {
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), !enabled)
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
            removeMediaItem(index)
            originalQueueIds = queueIds(this)
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
        equalizer.configure(value, equalizerValues); publish()
    }
    fun setEqualizerBand(index: Int, value: Int) {
        if (index !in equalizerValues.indices) return
        equalizerValues[index] = value.coerceIn(-10, 10)
        equalizerEnabled = true; equalizerPreset = "Personalizado"
        data.put("equalizer-$index", equalizerValues[index]); data.put("equalizer-enabled", true)
        data.put("equalizer-preset", equalizerPreset)
        equalizer.configure(true, equalizerValues); publish()
    }
    fun setEqualizerPreset(name: String, values: List<Int>) {
        equalizerPreset = name
        equalizerValues = IntArray(10) { values.getOrElse(it) { 0 }.coerceIn(-10, 10) }
        equalizerEnabled = true
        data.put("equalizer-enabled", true); data.put("equalizer-preset", name)
        equalizerValues.forEachIndexed { index, value -> data.put("equalizer-$index", value) }
        equalizer.configure(true, equalizerValues); publish()
    }
    fun setSleepTimer(minutes: Int, finishTrack: Boolean = sleepFinishTrack) {
        sleepJob?.cancel()
        sleepMinutes = minutes.coerceAtLeast(0); sleepFinishTrack = finishTrack
        if (sleepMinutes > 0) sleepJob = viewModelScope.launch {
            delay(sleepMinutes * 60_000L)
            if (sleepFinishTrack) {
                val player = controller ?: return@launch
                val previousRepeat = player.repeatMode
                player.repeatMode = Player.REPEAT_MODE_ONE
                var previous = player.currentPosition
                while (true) {
                    delay(200)
                    val current = player.currentPosition
                    if (current + 1_000 < previous || player.playbackState == Player.STATE_ENDED) {
                        player.pause(); player.repeatMode = previousRepeat; break
                    }
                    previous = current
                }
            } else controller?.pause()
            sleepMinutes = 0; publish()
        }
        publish()
    }
    fun setSleepFinishTrack(value: Boolean) { sleepFinishTrack = value; publish() }
    fun remove(id: String) {
        val player = controller ?: return
        for (index in player.mediaItemCount - 1 downTo 0) if (player.getMediaItemAt(index).mediaId == id)
            player.removeMediaItem(index)
    }
    fun progress(): Pair<Long, Long> = controller?.let {
        it.currentPosition.coerceAtLeast(0) to it.duration.coerceAtLeast(0)
    } ?: (0L to 0L)
    fun player(): Player? = controller
    fun persist() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        data.saveQueue(SavedPlayback(List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index); val metadata = item.mediaMetadata
            SavedQueueItem(item.mediaId, item.localConfiguration?.uri?.toString().orEmpty(),
                metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(),
                metadata.albumTitle?.toString().orEmpty())
        }, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0),
            manualShuffle || player.shuffleModeEnabled, player.repeatMode))
    }

    private fun restoreQueue(player: MediaController) {
        val saved = data.queue()?.takeIf { it.items.isNotEmpty() } ?: return
        val media = saved.items.filter { it.uri.isNotEmpty() }.map { item -> MediaItem.Builder()
            .setMediaId(item.id).setUri(item.uri).setMediaMetadata(MediaMetadata.Builder()
                .setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).build()).build() }
        if (media.isEmpty()) return
        originalQueueIds = media.map { it.mediaId }
        manualShuffle = saved.shuffle
        player.setMediaItems(media, saved.index.coerceIn(0, media.lastIndex), saved.position.coerceAtLeast(0))
        player.shuffleModeEnabled = false; player.repeatMode = saved.repeat; player.prepare()
    }

    override fun onCleared() {
        persist()
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        equalizer.release()
    }
}
