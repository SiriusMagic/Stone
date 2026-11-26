package com.zirakusa.stone.nativeapp.automation.actions

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.core.VoicePlay
import com.zirakusa.stone.nativeapp.playback.control.AudioMode
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** The signed local client makes one request. This action owns selection and ordering of all effects. */
internal object PlayByTitle {
    const val TOOL = "player_play_by_title"
    private const val MAX_CANDIDATES = 20
    private val settings = listOf(
        ActionParam("volumen", "integer", "Android media volume percent, from 0 through 100."),
        ActionParam("ecualizador", "boolean", "Enable or disable the equalizer."),
        ActionParam("segundoPlano", "boolean", "Whether the caller should return to the assistant after confirmation.")
    )

    val action = StoneAction("player.play_by_title", "Find and play one audio track by title on this phone.",
        ActionRisk.REVERSIBLE, listOf(
            ActionParam("titulo", "string", "Title or part of a title.", true),
            ActionParam("idPista", "string", "An ID returned among this title's ambiguous matches.")
        ) + settings, localOnly = true
    ) { args -> run(StoneAutomation.requireHost(), args) }

    val collection = StoneAction("player.play_collection", "Play the shuffled audio library or the daily mix.",
        ActionRisk.REVERSIBLE, listOf(ActionParam("coleccion", "string", "Audio collection.", true,
            options = listOf("biblioteca", "diaria"))) + settings, localOnly = true
    ) { args -> run(StoneAutomation.requireHost(), args) }

    private fun title(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

    private fun Song.json() = JSONObject().put("id", id).put("title", title).put("artist", artist).put("album", album)

    internal suspend fun run(host: AutomationHost, args: JSONObject): JSONObject {
        val collection = args.optString("coleccion").takeUnless { args.isNull("coleccion") }
        val query = if (collection == null) title(args.getString("titulo")) else ""
        val requestedVolume = args.optLong("volumen", -1)
        val volume = requestedVolume.takeUnless { args.isNull("volumen") }?.also {
            if (it !in 0..100) throw ActionException("El volumen debe estar entre 0 y 100.", "INVALID_PARAM")
        }?.toInt()
        if (collection == null && (query.isBlank() || query.length > 200)) {
            throw ActionException("Indica un título de hasta 200 caracteres.", "INVALID_PARAM")
        }
        val equalizer = args.optBoolean("ecualizador").takeUnless { args.isNull("ecualizador") }
        val background = args.optBoolean("segundoPlano")
        val steps = JSONObject()
        fun result(status: String) = JSONObject().put("status", status).put("steps", steps).put("segundoPlano", background)
        fun code(step: String, status: String) { steps.put(step, JSONObject().put("code", status)) }

        if (!host.canPlayForIntent()) {
            code("play", "PLAYER_NOT_READY")
            return result("failed")
        }
        val queue = when (collection) {
            "biblioteca" -> VoicePlay.shuffled(host.songs, System.currentTimeMillis())
            "diaria" -> VoicePlay.daily(host.songs, host.playCounts, host.favoriteIds, LocalDate.now())
            null -> {
                val matching = host.songs.filter { !it.isVideo && title(it.title).contains(query) }.distinctBy { it.id }
                val candidates = matching.filter { title(it.title) == query }.ifEmpty { matching }
                val id = args.optString("idPista").takeUnless { args.isNull("idPista") }
                val selected = if (id == null) candidates.singleOrNull() else candidates.take(MAX_CANDIDATES).firstOrNull { it.id == id }
                if (selected == null) {
                    val reason = when {
                        id != null -> "INVALID_SELECTION"
                        candidates.isEmpty() -> "TRACK_NOT_FOUND"
                        else -> "AMBIGUOUS_TITLE"
                    }
                    code("search", reason)
                    return result(if (reason == "AMBIGUOUS_TITLE") "ambiguous" else "failed")
                        .put("matches", JSONArray(candidates.take(MAX_CANDIDATES).map { it.json() })).put("total", candidates.size)
                }
                listOf(selected)
            }
            else -> throw ActionException("Colección desconocida.", "INVALID_PARAM")
        }
        val selected = queue.firstOrNull() ?: run {
            code("search", "EMPTY_LIBRARY")
            return result("failed")
        }
        code("search", "OK")
        var partial = false
        if (volume != null) {
            val actual = attempt { host.applyIntentVolume(volume) }
            if (actual == null) {
                code("volume", "VOLUME_REJECTED")
                return result("failed").put("selected", selected.json())
            }
            steps.put("volume", JSONObject().put("code", "OK").put("requested", volume).put("actual", actual))
        }
        val eqAllowed = equalizer != true || host.playback.audioMode != AudioMode.BATTERY
        if (equalizer != null) {
            val applied = eqAllowed && attempt { host.applyIntentEqualizer(equalizer) } == true
            if (!applied) {
                partial = true
                code("equalizer", if (eqAllowed) "EQ_NOT_ACTIVE" else "EQ_BATTERY_MODE")
            } else code("equalizer", "OK")
        }
        if (attempt { host.play(selected, queue); true } != true) {
            code("play", "PLAYBACK_FAILED")
            return result("failed").put("selected", selected.json())
        }
        val confirmed = withTimeoutOrNull(1_800) {
            while (host.audibleIntentTrack() != selected.id && !host.intentPlaybackFailed()) delay(75)
            host.audibleIntentTrack() == selected.id
        } == true
        if (equalizer == true && eqAllowed && host.isIntentEqualizerActive() && steps.getJSONObject("equalizer").optString("code") == "EQ_NOT_ACTIVE") {
            partial = false
            code("equalizer", "OK")
        }
        code("play", if (confirmed) "OK" else if (host.intentPlaybackFailed()) "PLAYBACK_FAILED" else "PLAYBACK_UNCONFIRMED")
        return result(if (!confirmed) "failed" else if (partial) "partial" else "playing")
            .put(if (confirmed) "track" else "selected", selected.json())
    }

    private inline fun <T> attempt(action: () -> T): T? = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
