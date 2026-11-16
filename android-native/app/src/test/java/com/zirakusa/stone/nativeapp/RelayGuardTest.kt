package com.zirakusa.stone.nativeapp

import android.os.Looper
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.AccessMode
import com.zirakusa.stone.nativeapp.automation.ApiToken
import com.zirakusa.stone.nativeapp.automation.AuthThrottle
import com.zirakusa.stone.nativeapp.automation.AutomationHost
import com.zirakusa.stone.nativeapp.automation.McpServer
import com.zirakusa.stone.nativeapp.automation.ReconnectPolicy
import com.zirakusa.stone.nativeapp.automation.RelayGuard
import com.zirakusa.stone.nativeapp.automation.ServerMetrics
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

/** Requests forwarded by the internet relay: trusted origin, stale work, repeated changes, metrics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayGuardTest {
    private val token = ApiToken("w", "Writer", "writer-secret", AccessMode.FULL, 0)
    private var toggles = 0

    @Before fun start() {
        AuthThrottle.reset(); RelayGuard.clearForTests(); ServerMetrics.reset()
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
        assertTrue(McpServer.start(listOf(token), localNetwork = false))
    }

    @After fun stop() { McpServer.stop(); StoneAutomation.host = null; AuthThrottle.reset(); RelayGuard.clearForTests() }

    /** A request as RelayClient sends it ([relayKey] null = an app on the phone pretending to be the relay). */
    private fun send(secret: String?, body: String, client: String? = null, relayKey: String? = RelayGuard.key,
        receivedAt: Long = System.currentTimeMillis(), relayId: String = "1", extra: String = ""): String {
        val head = StringBuilder("POST ${McpServer.PATH} HTTP/1.1\r\nContent-Length: ${body.toByteArray().size}\r\n")
        secret?.let { head.append("Authorization: Bearer $it\r\n") }
        relayKey?.let { head.append("X-Stone-Relay-Key: $it\r\nX-Stone-Relay-Received: $receivedAt\r\nX-Stone-Relay-Id: $relayId\r\n") }
        client?.let { head.append("X-Stone-Relay-Client: $it\r\n") }
        head.append(extra).append("\r\n").append(body)
        val call = CompletableFuture.supplyAsync {
            java.net.Socket("127.0.0.1", McpServer.DEFAULT_PORT).use { socket ->
                socket.getOutputStream().write(head.toString().toByteArray())
                socket.getInputStream().bufferedReader().readText()
            }
        }
        while (!call.isDone) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2) }
        return call.get()
    }

    private val ping = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
    private fun tool(name: String, args: JSONObject = JSONObject()) = JSONObject().put("jsonrpc", "2.0").put("id", 7)
        .put("method", "tools/call").put("params", JSONObject().put("name", name).put("arguments", args)).toString()

    @Test fun oneCallerWithWrongTokensDoesNotLockOutTheOthers() {
        repeat(20) { send("wrong", ping, client = "203.0.113.5") }
        assertTrue(send("wrong", ping, client = "203.0.113.5").startsWith("HTTP/1.1 429"))
        // Another internet caller with a wrong token is only told the token is wrong…
        assertTrue(send("wrong", ping, client = "198.51.100.7").startsWith("HTTP/1.1 401"))
        // …and every valid token keeps working, even from the blocked address.
        assertTrue(send(token.secret, ping, client = "198.51.100.7").startsWith("HTTP/1.1 200"))
        assertTrue(send(token.secret, ping, client = "203.0.113.5").startsWith("HTTP/1.1 200"))
    }

    @Test fun relayDetailsWithoutTheKeyAndForwardedForAreNotTrusted() {
        // An app on the phone claims to be many different internet callers: all count as one local caller.
        repeat(20) { i -> send("wrong", ping, client = "192.0.2.$i", relayKey = "fake",
            extra = "X-Forwarded-For: 10.0.0.$i\r\n") }
        assertTrue(send("wrong", ping, client = "192.0.2.99", relayKey = null).startsWith("HTTP/1.1 429"))
        assertTrue(send(token.secret, ping, relayKey = null).startsWith("HTTP/1.1 200"))
    }

    @Test fun workThatWaitedPastTheRelayDeadlineIsNotRun() {
        val answer = send(token.secret, tool("player_play"), receivedAt = System.currentTimeMillis() - 61_000)
        assertTrue(answer.startsWith("HTTP/1.1 503") && answer.contains("Retry-After: 10") && answer.contains("did not run"))
        assertEquals(0, toggles)
        assertEquals(1, ServerMetrics.count("staleRequestsNotRun"))
    }

    @Test fun aRetriedChangeThatFinishedLateIsNotDoneTwice() {
        RelayGuard.lateAfterMs = 0 // every change counts as finished after the relay gave up
        val first = send(token.secret, tool("player_play"), relayId = "10")
        assertEquals(1, toggles)
        val retry = send(token.secret, tool("player_play"), relayId = "11")
        assertEquals("the retry must not toggle again", 1, toggles)
        assertTrue(retry.contains("\"replayed\":true"))
        // Used once: a later identical request is a new, deliberate change.
        send(token.secret, tool("player_play"), relayId = "12")
        assertEquals(2, toggles)
        assertTrue(first.startsWith("HTTP/1.1 200"))
        // Reads are never answered from memory.
        send(token.secret, tool("player_state")); send(token.secret, tool("player_state"))
        assertEquals(0, ServerMetrics.count("replayedLateResults") - 1)
    }

    @Test fun theRelaysTimeoutNoticeMarksAChangeAsLate() {
        RelayGuard.relayTimedOut("20")
        send(token.secret, tool("player_play"), relayId = "20")
        send(token.secret, tool("player_play"), relayId = "21")
        assertEquals(1, toggles)
        // Without a notice and in time, a repeat is a real second change.
        send(token.secret, tool("player_play"), relayId = "22"); send(token.secret, tool("player_play"), relayId = "23")
        assertEquals(3, toggles)
    }

    @Test fun argumentsInAnotherOrderAreTheSameChange() {
        val a = RelayGuard.callKey("w", "t", JSONObject("{\"b\":1,\"a\":{\"y\":2,\"x\":[1,\"s\"]}}"))
        val b = RelayGuard.callKey("w", "t", JSONObject("{\"a\":{\"x\":[1,\"s\"],\"y\":2},\"b\":1}"))
        assertEquals(a, b)
        assertNotEquals(a, RelayGuard.callKey("other", "t", JSONObject("{\"b\":1}")))
    }

    @Test fun metricsCountRoutesStatusAndToolsButNeverSecrets() {
        send(token.secret, tool("player_state"))
        send("wrong-but-secret-looking", ping, client = "203.0.113.9")
        // Metrics are written just after the answer is sent: wait for both requests to be counted.
        val deadline = System.currentTimeMillis() + 5_000
        while (ServerMetrics.count("requestsViaRelay") < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        val metrics = ServerMetrics.snapshot()
        val snapshot = metrics.toString()
        assertEquals(snapshot, 2, metrics.getJSONObject("routes").getInt("POST /mcp"))
        assertEquals(1, metrics.getJSONObject("status").getInt("401")); assertEquals(1, metrics.getJSONObject("status").getInt("200"))
        assertEquals(1, metrics.getJSONObject("tools").getInt("player_state")); assertEquals(1, metrics.getJSONObject("rpcMethods").getInt("tools/call"))
        listOf(token.secret, "wrong-but-secret-looking", "203.0.113.9").forEach { assertFalse(it, snapshot.contains(it)) }
    }

    @Test fun reconnectionStopsOnAnswersThatWillNotChange() {
        listOf(400, 401, 403, 404, 426).forEach { assertTrue(it.toString(), ReconnectPolicy.isPermanent(it)) }
        assertTrue(ReconnectPolicy.isPermanent(null, "replaced"))
        listOf(500, 502, 503, 521, null).forEach { assertFalse(it.toString(), ReconnectPolicy.isPermanent(it)) }
        val random = Random(42)
        val delays = (1..20).map { ReconnectPolicy.delaySeconds(it, random) }
        assertTrue(delays.first() in 1..3)
        assertTrue(delays.all { it <= ReconnectPolicy.MAX_DELAY_SECONDS })
        assertTrue(delays.last() >= 240) // capped at 5 min ±20 %
        // Worst case for a failure that keeps going: ~300 s apart → at most ~290 attempts a day instead of 2,880.
        assertTrue(86_400 / delays.last() < 400)
    }
}
