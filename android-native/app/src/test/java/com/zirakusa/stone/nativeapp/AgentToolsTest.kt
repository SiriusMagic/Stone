package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.actions.ActionException
import com.zirakusa.stone.nativeapp.automation.actions.ActionRecord
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.activity.ActivityLog
import com.zirakusa.stone.nativeapp.automation.actions.AutomationHost
import com.zirakusa.stone.nativeapp.automation.actions.BatchItem
import com.zirakusa.stone.nativeapp.automation.actions.MetadataBatch
import com.zirakusa.stone.nativeapp.automation.actions.RecordStatus
import com.zirakusa.stone.nativeapp.automation.actions.PlayByTitle
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.automation.actions.StoneJobs
import com.zirakusa.stone.nativeapp.automation.actions.StoneSolutions
import com.zirakusa.stone.nativeapp.data.library.web.RateLimitedException
import com.zirakusa.stone.nativeapp.data.library.user.StonePlaylist
import com.zirakusa.stone.nativeapp.data.library.tags.TitleCleaner
import com.zirakusa.stone.nativeapp.data.library.web.WebPacer
import com.zirakusa.stone.nativeapp.playback.control.AudioMode
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rate limits, title clean-up, background jobs, activity log and the solutions dictionary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentToolsTest {
    private val songs = (1..6).map { Song("content://fixture/$it.mp3", "Song $it", "A", "B", "M", 100_000, 1, 1) }
    private var refreshes = 0

    @Before fun host() {
        StoneJobs.minWaitMs = 10
        StoneAutomation.host = object : AutomationHost {
            override val playback = PlaybackState()
            override val songs get() = this@AgentToolsTest.songs
            override val playlists = emptyList<StonePlaylist>()
            override val theme = "dark"; override val language = "es"
            override fun progress() = 0L to 0L
            override fun toggle() = Unit; override fun next() = Unit; override fun previous() = Unit
            override fun seek(positionMs: Long) = Unit; override fun setSpeed(speed: Float) = Unit
            override fun setVolume(percent: Int) = Unit; override fun play(song: Song, queue: List<Song>) = Unit
            override fun playNext(songs: List<Song>) = Unit; override fun setAudioMode(mode: AudioMode) = Unit
            override fun createPlaylist(name: String) = Unit; override fun addToPlaylist(playlistId: String, song: Song) = Unit
            override fun setTheme(key: String) = Unit; override fun setLanguage(code: String) = Unit
            override fun refreshLibrary() { refreshes++ }
            override fun setSleepTimer(minutes: Int, finishTrack: Boolean, fadeOut: Boolean) = Unit
            override fun extendSleepTimer(minutes: Int) = Unit; override fun setLauncherIcon(key: String) = Unit
            override fun setFavorite(songId: String, favorite: Boolean) = Unit
        }
    }
    @After fun clear() { StoneAutomation.host = null }

    @Test fun titleActionChangesSettingsOnceAndConfirmsTheCorrectSong() = runBlocking {
        val song = songs[0].copy(title = "New Americana")
        var current: String? = null
        val calls = mutableListOf<String>()
        StoneAutomation.host = object : AutomationHost by requireNotNull(StoneAutomation.host) {
            override val songs = listOf(song, this@AgentToolsTest.songs[1].copy(title = "New Americana Live"))
            override fun canPlayForIntent() = true
            override fun audibleIntentTrack() = current
            override fun applyIntentVolume(percent: Int): Int { calls += "volume:$percent"; return 53 }
            override fun applyIntentEqualizer(enabled: Boolean): Boolean { calls += "eq:$enabled"; return false }
            override fun play(song: Song, queue: List<Song>) { calls += "play"; assertEquals(listOf(song), queue); current = song.id }
        }
        val args = JSONObject().put("titulo", "New Americana").put("volumen", 50).put("ecualizador", true).put("segundoPlano", true)
        val result = PlayByTitle.run(requireNotNull(StoneAutomation.host), args)
        assertEquals(listOf("volume:50", "eq:true", "play"), calls)
        assertEquals("partial", result.getString("status"))
        assertEquals(song.id, result.getJSONObject("track").getString("id"))
        assertEquals(53, result.getJSONObject("steps").getJSONObject("volume").getInt("actual"))
        assertEquals("EQ_NOT_ACTIVE", result.getJSONObject("steps").getJSONObject("equalizer").getString("code"))
        assertTrue(result.getBoolean("segundoPlano"))
    }

    @Test fun titleActionAsksOnAmbiguityAndStopsOnRejectedVolume() = runBlocking {
        val candidates = songs.take(2).map { it.copy(title = "New Americana") }
        var volume = 0
        var plays = 0
        StoneAutomation.host = object : AutomationHost by requireNotNull(StoneAutomation.host) {
            override val songs = candidates
            override fun canPlayForIntent() = true
            override fun applyIntentVolume(percent: Int): Int? { volume++; return null }
            override fun play(song: Song, queue: List<Song>) { plays++ }
        }
        val args = JSONObject().put("titulo", "New Americana").put("volumen", 50)
        val ambiguous = PlayByTitle.run(requireNotNull(StoneAutomation.host), args)
        assertEquals("ambiguous", ambiguous.getString("status"))
        assertEquals(2, ambiguous.getJSONArray("matches").length())
        assertEquals(0, volume)
        args.put("idPista", candidates.last().id)
        val denied = PlayByTitle.run(requireNotNull(StoneAutomation.host), args)
        assertEquals("VOLUME_REJECTED", denied.getJSONObject("steps").getJSONObject("volume").getString("code"))
        assertEquals(1, volume)
        assertEquals(0, plays)
        args.remove("idPista")
        args.put("titulo", "Missing title")
        val missing = PlayByTitle.run(requireNotNull(StoneAutomation.host), args)
        assertEquals("TRACK_NOT_FOUND", missing.getJSONObject("steps").getJSONObject("search").getString("code"))
        assertEquals(1, volume)
    }

    @Test fun titleActionRequiresTheLocalChannelAndIsDiscoverableThere() = runBlocking {
        val tool = "player_play_by_title"
        val access = com.zirakusa.stone.nativeapp.automation.auth.AccessMode.FULL
        assertTrue(StoneAutomation.available(access, local = true).any { it.toolName == tool })
        assertFalse(StoneAutomation.available(access).any { it.toolName == tool })
        assertEquals("LOCAL_ONLY", runCatching {
            StoneAutomation.execute(tool, JSONObject().put("titulo", "Song 1"))
        }.exceptionOrNull().let { (it as ActionException).code })
        assertEquals("INVALID_PARAM", runCatching {
            StoneAutomation.execute(tool, JSONObject(), local = true)
        }.exceptionOrNull().let { (it as ActionException).code })
    }

    private fun items() = songs.map { BatchItem(it.id, it, JSONObject()) }

    @Test fun pacerSpacesRequestsHonoursRetryAfterAndFailsFastDuringCoolDown() {
        var now = 0L
        val slept = mutableListOf<Long>()
        val pacer = WebPacer("test", minGapMs = 700, clock = { now }, sleep = { slept += it; now += it })
        assertEquals("a", pacer.call { 200 to "a" })
        assertEquals("b", pacer.call { 200 to "b" })
        assertEquals(listOf(700L), slept) // second request waited its turn
        var calls = 0
        assertEquals("ok", pacer.call { if (calls++ == 0) 429 to "3" else 200 to "ok" })
        assertTrue(3_000L in slept) // Retry-After: 3
        val blocked = assertThrows(RateLimitedException::class.java) { pacer.call { 429 to "60" } }
        assertEquals(60, blocked.retryAfterSeconds) // too long to wait inside the call
        assertThrows(RateLimitedException::class.java) { pacer.call { error("must not be called during cool-down") } }
        now += 61_000 // cool-down over
        assertNull(pacer.call { 404 to null })
    }

    @Test fun rateLimitErrorsHaveACodeAndASolution() {
        val error = ActionException.of(RateLimitedException("iTunes", 30)).json()
        assertEquals("RATE_LIMITED", error.getString("code"))
        assertEquals("stone_troubleshoot code=RATE_LIMITED", error.getString("solution"))
        listOf("LOW_CONFIDENCE", "LYRICS_NOT_FOUND", "ANDROID_CONSENT", "TITLE_HAS_EXTRAS", "NETWORK", "WORKFLOW_LYRICS")
            .forEach { assertTrue(it, StoneSolutions.has(it)) }
        assertEquals("RATE_LIMITED", StoneSolutions.lookup("rate_limited", null).getString("key"))
        assertTrue(StoneSolutions.lookup(null, "genre").getInt("total") >= 1)
    }

    @Test fun titlesKeepOnlyTheSongName() {
        assertEquals("In the End", TitleCleaner.clean("Linkin Park - In the End (Official Video)", "Linkin Park"))
        assertEquals("Faded", TitleCleaner.clean("Faded [Lyrics] | Alan Walker", "Alan Walker"))
        assertEquals("After Dark", TitleCleaner.clean("After Dark (slowed + reverb)", "Mr.Kitty"))
        assertEquals("Demons (Remix)", TitleCleaner.clean("Demons (Remix) (Letra)", ""))
        assertEquals("Alone (Sped Up)", TitleCleaner.clean("Alone (Sped Up) HD", ""))
        assertEquals("Alan Walker Mashup", TitleCleaner.clean("Alan Walker Mashup | Naresh Parmar | On My Way | Faded", "Naresh Parmar"))
        assertFalse(TitleCleaner.hasExtras("Another Love", "Tom Odell"))
    }

    @Test fun syncBatchStopsAtARateLimitAndListsWhatIsLeft() = runBlocking {
        val result = MetadataBatch.run("t", items(), false, preview = { _, c -> c }) { item, _ ->
            if (item.songId == songs[2].id) throw RateLimitedException("lrclib.net", 20)
            JSONObject().put("id", item.songId)
        }
        assertEquals(2, result.getInt("updated"))
        assertEquals(20, result.getInt("retryAfterSeconds"))
        assertEquals("RATE_LIMITED", result.getJSONObject("stoppedBy").getString("code"))
        assertEquals(4, result.getJSONArray("remainingSongIds").length())
    }

    @Test fun bigBatchesBecomeJobsThatWaitOnRateLimitsAndReportEachSong() {
        var limitedOnce = false
        val started = runBlocking { MetadataBatch.runOrStart("t", items(), JSONObject(), preview = { _, c -> c }) { item, _ ->
            if (item.songId == songs[3].id && !limitedOnce) { limitedOnce = true; throw RateLimitedException("iTunes", 1) }
            if (item.songId == songs[4].id) throw ActionException("nope", "LOW_CONFIDENCE")
            JSONObject().put("id", item.songId)
        } }
        // 6 items are under SYNC_LIMIT: answered directly. Below, background=true forces a job.
        assertFalse(started.has("jobId"))
        limitedOnce = false
        val job = runBlocking { MetadataBatch.runOrStart("t", items(), JSONObject().put("background", true), preview = { _, c -> c }) { item, _ ->
            if (item.songId == songs[1].id && !limitedOnce) { limitedOnce = true; throw RateLimitedException("iTunes", 1) }
            JSONObject().put("id", item.songId)
        } }
        val id = job.getString("jobId")
        // The job refreshes the library on the main thread: keep it running while waiting.
        val deadline = System.currentTimeMillis() + 10_000
        while (StoneJobs.status(id, 0).getString("status") != "done" && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        assertTrue(limitedOnce)
        val status = StoneJobs.status(id, 0)
        assertEquals("done", status.getString("status"))
        assertEquals(6, status.getInt("done")); assertEquals(100, status.getInt("percent"))
        assertEquals(6, status.getJSONArray("results").length())
        assertEquals(2, StoneJobs.status(id, 4).getJSONArray("results").length())
        assertTrue(status.has("batchId"))
    }

    @Test fun activitySurvivesAsJsonAndFiltersErrors() {
        val records = listOf(
            ActionRecord(3, "metadata.suggest_tags", false, "iTunes is limiting requests", RecordStatus.FAILED, ActionRisk.READ, "Principal", "RATE_LIMITED"),
            ActionRecord(2, "player.state", true, "{}", RecordStatus.OK, ActionRisk.READ, "GPT"),
            ActionRecord(1, "metadata.fetch_lyrics", false, "No result", RecordStatus.FAILED, ActionRisk.PERSISTENT, "GPT", "LOW_CONFIDENCE"))
        val restored = ActivityLog.parse(ActivityLog.serialize(records))
        assertEquals(records, restored)
        val errors = ActivityLog.query(restored, onlyErrors = true, tool = null, token = null, limit = 10)
        assertEquals(2, errors.getInt("total"))
        assertEquals(1, errors.getJSONObject("errorCodes").getInt("RATE_LIMITED"))
        assertEquals("metadata_suggest_tags", errors.getJSONArray("calls").getJSONObject(0).getString("tool"))
        assertEquals(1, ActivityLog.query(restored, true, null, "gpt", 10).getInt("total"))
    }

    @Test fun newToolsAreRegisteredForAgents() {
        listOf("stone_activity", "stone_troubleshoot", "stone_job_status", "stone_job_resume", "stone_job_cancel",
            "metadata_suggest_tags_many", "metadata_prepare_write").forEach { assertNotNull(it, StoneAutomation.find(it)) }
        val help = runBlocking { StoneAutomation.find("stone_troubleshoot")!!.run(JSONObject().put("code", "LOW_CONFIDENCE")) }
        assertEquals("LOW_CONFIDENCE", (help as JSONObject).getString("key"))
    }

}
