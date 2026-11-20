package com.zirakusa.stone.nativeapp

import android.os.Looper
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiToken
import com.zirakusa.stone.nativeapp.automation.actions.AutomationHost
import com.zirakusa.stone.nativeapp.automation.server.McpServer
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.data.library.user.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.control.AudioMode
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState
import kotlinx.coroutines.async
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Several agents at the same time must never see or use each other's permissions: every request
 * runs with its own token's mode and tool list, whatever the other agents are doing meanwhile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentIsolationTest {
    private val reader = ApiToken("r", "Reader", "reader-secret", AccessMode.READ, 0)
    private val writer = ApiToken("w", "Writer", "writer-secret", AccessMode.FULL, 0)
    private val limited = ApiToken("l", "Limited", "limited-secret", AccessMode.FULL, 0, disabledTools = setOf("stone_troubleshoot"))
    private var toggles = 0

    @Before fun start() {
        StoneAutomation.host = object : AutomationHost {
            override val playback = PlaybackState(ready = true)
            override val songs = listOf(Song("content://fixture/1.mp3", "One", "A", "B", "M", 1000, 1, 1))
            override val playlists = emptyList<StonePlaylist>()
            override val theme = "dark"; override val language = "es"
            override fun progress() = 0L to 0L
            override fun toggle() { toggles++ }
            override fun next() = Unit; override fun previous() = Unit
            override fun seek(positionMs: Long) = Unit; override fun setSpeed(speed: Float) = Unit
            override fun setVolume(percent: Int) = Unit; override fun play(song: Song, queue: List<Song>) = Unit
            override fun playNext(songs: List<Song>) = Unit; override fun setAudioMode(mode: AudioMode) = Unit
            override fun createPlaylist(name: String) = Unit; override fun addToPlaylist(playlistId: String, song: Song) = Unit
            override fun setTheme(key: String) = Unit; override fun setLanguage(code: String) = Unit
            override fun refreshLibrary() = Unit
            override fun setSleepTimer(minutes: Int, finishTrack: Boolean, fadeOut: Boolean) = Unit
            override fun extendSleepTimer(minutes: Int) = Unit; override fun setLauncherIcon(key: String) = Unit
            override fun setFavorite(songId: String, favorite: Boolean) = Unit
        }
        assertTrue(McpServer.start(listOf(reader, writer, limited)))
    }

    @After fun stop() { McpServer.stop(); StoneAutomation.host = null }

    /** One call from the test thread, pumping the main looper meanwhile (tools run on it). */
    private fun pumped(secret: String, tool: String, args: JSONObject = JSONObject()) = concurrently(listOf { call(secret, tool, args) }).single()

    private fun call(secret: String, tool: String, args: JSONObject = JSONObject()): JSONObject {
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
            .put("params", JSONObject().put("name", tool).put("arguments", args)).toString()
        val connection = URL("http://127.0.0.1:${McpServer.DEFAULT_PORT}${McpServer.PATH}").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $secret")
        connection.outputStream.use { it.write(body.toByteArray()) }
        return JSONObject(connection.inputStream.bufferedReader().readText()).getJSONObject("result")
    }

    /** Runs every call at once on separate threads while the main looper (where tools run) is pumped. */
    private fun <T> concurrently(calls: List<() -> T>): List<T> {
        val pool = Executors.newFixedThreadPool(12)
        val futures = calls.map { CompletableFuture.supplyAsync({ it() }, pool) }
        while (futures.any { !it.isDone }) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2) }
        pool.shutdown()
        return futures.map { it.get() }
    }

    private fun toolNames(result: JSONObject): Set<String> {
        val tools = result.getJSONObject("structuredContent").getJSONArray("tools")
        return (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }.toSet()
    }

    @Test fun concurrentAgentsOnlySeeTheirOwnTools() {
        val writeTools = StoneAutomation.available(AccessMode.FULL).filter { it.risk != com.zirakusa.stone.nativeapp.automation.actions.ActionRisk.READ }
            .map { it.toolName }.toSet()
        val tokens = List(60) { listOf(reader, writer, limited) }.flatten()
        val results = concurrently(tokens.map { token -> { token to toolNames(call(token.secret, "stone_help")) } })
        results.forEach { (token, names) ->
            when (token) {
                reader -> assertTrue("reader saw write tools: ${names.intersect(writeTools).take(3)}", names.intersect(writeTools).isEmpty())
                limited -> assertFalse("limited saw a tool it has turned off", "stone_troubleshoot" in names)
                writer -> assertTrue("writer lost its own tools", "stone_troubleshoot" in names && "player_play" in names)
            }
        }
    }

    @Test fun aReadOnlyTokenNeverWritesEvenWhileAFullTokenWorks() {
        val calls = List(40) { listOf(reader to "player_play", writer to "player_state") }.flatten()
        val results = concurrently(calls.map { (token, tool) -> { Triple(token, tool, call(token.secret, tool)) } })
        results.filter { it.first == reader }.forEach { (_, _, result) ->
            assertTrue(result.getBoolean("isError"))
            assertEquals("READ_ONLY_TOKEN", result.getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        }
        assertEquals(0, toggles)
    }

    @Test fun authorizationTravelsWithEachCoroutineNotInSharedState() = kotlinx.coroutines.runBlocking {
        val a = com.zirakusa.stone.nativeapp.automation.auth.AuthContext("a", "A", AccessMode.READ, setOf("x"))
        val b = com.zirakusa.stone.nativeapp.automation.auth.AuthContext("b", "B", AccessMode.FULL, emptySet())
        val seen = (1..200).map { index ->
            val auth = if (index % 2 == 0) a else b
            async(kotlinx.coroutines.Dispatchers.Default + auth) {
                kotlinx.coroutines.delay((index % 7).toLong())
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.yield(); auth to com.zirakusa.stone.nativeapp.automation.auth.currentAuth() }
            }
        }.map { it.await() }
        seen.forEach { (expected, actual) -> assertSame(expected, actual) }
    }

    @Test fun jobsAndActivityBelongToTheTokenThatMadeThem() {
        val jobs = com.zirakusa.stone.nativeapp.automation.actions.StoneJobs
        val item = com.zirakusa.stone.nativeapp.automation.actions.BatchItem("x", null, JSONObject())
        val started = jobs.start("t", listOf(item), owner = writer.id) { _, _ -> JSONObject() }
        val id = started.getString("jobId")
        // The reader asks about the writer's job: it does not exist for it, and it cannot touch it.
        // Another full-access token asks about it: the job does not exist for it and it cannot touch it.
        listOf("stone_job_status", "stone_job_cancel", "stone_job_resume").forEach { tool ->
            val result = pumped(limited.secret, tool, JSONObject().put("jobId", id))
            assertTrue(tool, result.getBoolean("isError"))
            assertEquals(tool, "NOT_FOUND", result.getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        }
        // A read-only token is stopped even earlier for the changing ones.
        assertEquals("READ_ONLY_TOKEN", pumped(reader.secret, "stone_job_cancel", JSONObject().put("jobId", id))
            .getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        assertEquals(0, pumped(reader.secret, "stone_job_status").getJSONObject("structuredContent").getJSONArray("jobs").length())
        assertEquals(id, pumped(writer.secret, "stone_job_status").getJSONObject("structuredContent").getJSONArray("jobs").getJSONObject(0).getString("jobId"))
        // Activity: each token reads only its own calls.
        pumped(writer.secret, "player_state")
        val readerView = pumped(reader.secret, "stone_activity").getJSONObject("structuredContent").getJSONArray("calls")
        (0 until readerView.length()).forEach { assertEquals("Reader", readerView.getJSONObject(it).getString("token")) }
    }
}
