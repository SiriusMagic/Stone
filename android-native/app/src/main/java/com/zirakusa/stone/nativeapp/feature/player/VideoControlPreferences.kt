package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore

/** Secondary actions that can be shown below a video preview or player. */
enum class VideoControlId(val key: String) {
    AudioOnly("audio_only"),
    Options("options"),
    Queue("queue"),
    Scale("scale"),
    Lock("lock")
}

data class VideoControlDefinition(
    val id: VideoControlId,
    val icon: ImageVector,
    val label: Int,
)

object VideoControlRegistry {
    val definitions = listOf(
        VideoControlDefinition(VideoControlId.AudioOnly, Icons.Default.Headphones, R.string.audio_only),
        VideoControlDefinition(VideoControlId.Options, Icons.Default.Tune, R.string.more_options),
        VideoControlDefinition(VideoControlId.Queue, Icons.Default.QueueMusic, R.string.queue),
        VideoControlDefinition(VideoControlId.Scale, Icons.Default.Crop, R.string.video_scale_toggle),
        VideoControlDefinition(VideoControlId.Lock, Icons.Default.Lock, R.string.video_lock),
    )

    val defaultIds = definitions.map { it.id }

    fun definition(id: VideoControlId): VideoControlDefinition =
        definitions.first { it.id == id }
}

data class VideoControlSettings(
    val order: List<VideoControlId>,
    val visible: Set<VideoControlId>,
) {
    val visibleIds: List<VideoControlId>
        get() = order.filter { it in visible }

    companion object {
        fun defaults() = VideoControlSettings(
            order = VideoControlRegistry.defaultIds,
            visible = VideoControlRegistry.defaultIds.toSet(),
        )

        fun from(store: StoneSettingsStore?): VideoControlSettings {
            if (store == null) return defaults()
            val defaults = VideoControlRegistry.defaultIds
            val order = store.videoControlOrder(defaults.map { it.key })
                .mapNotNull { key -> defaults.firstOrNull { it.key == key } }
            val visible = defaults.filter { id -> store.videoControlVisible(id.key, true) }.toSet()
            return VideoControlSettings(order.ifEmpty { defaults }, visible)
        }
    }
}
