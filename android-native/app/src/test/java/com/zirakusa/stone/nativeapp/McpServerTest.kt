package com.zirakusa.stone.nativeapp

import android.os.Looper
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.AutomationHost
import com.zirakusa.stone.nativeapp.automation.McpCheck
import com.zirakusa.stone.nativeapp.automation.McpSelfTest
import com.zirakusa.stone.nativeapp.automation.McpServer
import com.zirakusa.stone.nativeapp.automation.StoneAutomation
import com.zirakusa.stone.nativeapp.data.library.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.AudioMode
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.ui.theme.StoneInk
import com.zirakusa.stone.nativeapp.ui.theme.StoneLook
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpServerTest {
    private val token = "test-token"
    private val songs = listOf(
        Song("content://fixture/1.mp3", "After Dark", "Mr.Kitty", "Time", "Music", 258_000, 1, 1),
        Song("content://fixture/2.mp3", "Skyfall", "Adele", "Skyfall", "Music", 286_000, 1, 1))
    private var toggles = 0
    private var playlistsCreated = listOf<String>()
    private var sleepMinutes = 0
    private var launcherIcon = "penguin"

    @Before fun start() {
        StoneAutomation.host = object : AutomationHost {
            override val playback = PlaybackState(ready = true, title = "After Dark", artist = "Mr.Kitty", playing = false,
                currentId = songs[0].id)
            override val songs get() = this@McpServerTest.songs
            override val playlists = emptyList<StonePlaylist>()
            override val theme = "dark"
            override val language = "es"
            override fun progress() = 1000L to 258_000L
            override fun toggle() { toggles++ }
            override fun next() = Unit
            override fun previous() = Unit
            override fun seek(positionMs: Long) = Unit
            override fun setSpeed(speed: Float) = Unit
            override fun setVolume(percent: Int) = Unit
            override fun play(song: Song, queue: List<Song>) = Unit
            override fun playNext(songs: List<Song>) = Unit
            override fun setAudioMode(mode: AudioMode) = Unit
            override fun createPlaylist(name: String) { playlistsCreated = playlistsCreated + name }
            override fun addToPlaylist(playlistId: String, song: Song) = Unit
            override fun setTheme(key: String) = Unit
            override fun setLanguage(code: String) = Unit
            override fun refreshLibrary() = Unit
            override fun setSleepTimer(minutes: Int, finishTrack: Boolean, fadeOut: Boolean) { sleepMinutes = minutes }
            override fun extendSleepTimer(minutes: Int) { sleepMinutes += minutes }
            override fun setLauncherIcon(key: String) { launcherIcon = key }
        }
        assertTrue(McpServer.start(token, localNetwork = false))
    }

    @After fun stop() { McpServer.stop(); StoneAutomation.host = null }

    private fun post(body: String, auth: String? = "Bearer $token", origin: String? = null): Pair<Int, String> {
        // Actions run on the main looper, which Robolectric only advances when asked.
        val call = CompletableFuture.supplyAsync {
            val connection = URL("http://127.0.0.1:${McpServer.DEFAULT_PORT}${McpServer.PATH}")
                .openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            auth?.let { connection.setRequestProperty("Authorization", it) }
            origin?.let { connection.setRequestProperty("Origin", it) }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.readText() ?: "")
        }
        while (!call.isDone) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        return call.get()
    }

    private fun rpc(method: String, params: JSONObject = JSONObject()): JSONObject {
        val (code, text) = post(JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString())
        assertEquals(200, code)
        return JSONObject(text)
    }

    /** Raw HTTP exchange; returns the whole response (HttpURLConnection hides Origin and OPTIONS details). */
    private fun raw(request: String): String = java.net.Socket("127.0.0.1", McpServer.DEFAULT_PORT).use { socket ->
        socket.getOutputStream().write(request.toByteArray())
        socket.getInputStream().bufferedReader().readText()
    }

    @Test fun requiresTheTokenButAcceptsItInEveryForm() {
        val ping = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
        assertEquals(401, post(ping, auth = null).first)
        assertEquals(401, post(ping, auth = "Bearer wrong").first)
        assertEquals(200, post(ping).first)
        val viaQuery = raw("POST ${McpServer.PATH}?token=$token HTTP/1.1\r\nHost: x\r\nContent-Length: ${ping.length}\r\n\r\n$ping")
        assertTrue(viaQuery.startsWith("HTTP/1.1 200"))
        val viaApiKey = raw("POST ${McpServer.PATH} HTTP/1.1\r\nX-Api-Key: $token\r\nContent-Length: ${ping.length}\r\n\r\n$ping")
        assertTrue(viaApiKey.startsWith("HTTP/1.1 200"))
        // Browser clients: CORS preflight answers, and a request with Origin works when it carries the token.
        val preflight = raw("OPTIONS ${McpServer.PATH} HTTP/1.1\r\nOrigin: http://localhost:6274\r\n\r\n")
        assertTrue(preflight.startsWith("HTTP/1.1 204") && preflight.contains("Access-Control-Allow-Origin: *"))
        val withOrigin = raw("POST ${McpServer.PATH} HTTP/1.1\r\nAuthorization: Bearer $token\r\n" +
            "Origin: http://localhost:6274\r\nContent-Length: ${ping.length}\r\n\r\n$ping")
        assertTrue(withOrigin.startsWith("HTTP/1.1 200") && withOrigin.contains("X-Stone-Server: Stone"))
    }

    @Test fun legacySseTransportDeliversReplies() {
        java.net.Socket("127.0.0.1", McpServer.DEFAULT_PORT).use { stream ->
            stream.soTimeout = 5_000
            stream.getOutputStream().write("GET /sse?token=$token HTTP/1.1\r\nAccept: text/event-stream\r\n\r\n".toByteArray())
            val reader = stream.getInputStream().bufferedReader()
            assertTrue(reader.readLine().startsWith("HTTP/1.1 200"))
            val endpoint = generateSequence { reader.readLine() }.first { it.startsWith("data: /messages") }.removePrefix("data: ")
            val init = """{"jsonrpc":"2.0","id":7,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}"""
            // The session id alone is enough: some clients only put the token in the SSE URL.
            val posted = raw("POST ${endpoint.substringBefore("&token")} HTTP/1.1\r\nContent-Length: ${init.length}\r\n\r\n$init")
            assertTrue(posted.startsWith("HTTP/1.1 202"))
            val message = generateSequence { reader.readLine() }.first { it.startsWith("data: {") }
            assertEquals("stone", JSONObject(message.removePrefix("data: ")).getJSONObject("result")
                .getJSONObject("serverInfo").getString("name"))
        }
    }

    @Test fun selfTestRecognisesStone() {
        val url = "http://127.0.0.1:${McpServer.DEFAULT_PORT}${McpServer.PATH}"
        val results = kotlinx.coroutines.runBlocking { McpSelfTest.run(listOf(url), token) }
        assertTrue(results.single() is McpCheck.Ok)
        val wrong = kotlinx.coroutines.runBlocking { McpSelfTest.run(listOf(url), "nope") }
        assertTrue(wrong.single() is McpCheck.NoAnswer)
    }

    @Test fun initializeAndListTools() {
        val init = rpc("initialize", JSONObject().put("protocolVersion", "2025-06-18")).getJSONObject("result")
        assertEquals("2025-06-18", init.getString("protocolVersion"))
        assertEquals("stone", init.getJSONObject("serverInfo").getString("name"))
        val tools = rpc("tools/list").getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue(names.containsAll(listOf("player_play", "library_search", "metadata_update", "appearance_set_theme")))
        assertEquals("object", tools.getJSONObject(0).getJSONObject("inputSchema").getString("type"))
        // Notifications get 202 and no body.
        assertEquals(202, post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").first)
    }

    @Test fun callsToolsAndReportsToolErrorsToTheModel() {
        val search = rpc("tools/call", JSONObject().put("name", "library_search")
            .put("arguments", JSONObject().put("query", "adele"))).getJSONObject("result")
        assertFalse(search.getBoolean("isError"))
        val found = search.getJSONObject("structuredContent")
        assertEquals(1, found.getInt("total"))
        assertEquals("Skyfall", found.getJSONArray("results").getJSONObject(0).getString("title"))

        rpc("tools/call", JSONObject().put("name", "player_play"))
        assertEquals(1, toggles)
        rpc("tools/call", JSONObject().put("name", "playlist_create").put("arguments", JSONObject().put("name", "Rock")))
        assertEquals(listOf("Rock"), playlistsCreated)

        val bad = rpc("tools/call", JSONObject().put("name", "player_set_speed")
            .put("arguments", JSONObject().put("speed", 9))).getJSONObject("result")
        assertTrue(bad.getBoolean("isError"))
        val missing = rpc("tools/call", JSONObject().put("name", "player_seek")).getJSONObject("result")
        assertTrue(missing.getBoolean("isError"))
        assertEquals(-32601, rpc("nope").getJSONObject("error").getInt("code"))
    }

    @Test fun customisationAndSleepTimerTools() {
        rpc("tools/call", JSONObject().put("name", "player_sleep_timer").put("arguments", JSONObject().put("minutes", 20)))
        rpc("tools/call", JSONObject().put("name", "player_sleep_timer_extend").put("arguments", JSONObject().put("minutes", 5)))
        assertEquals(25, sleepMinutes)

        val look = rpc("tools/call", JSONObject().put("name", "appearance_set_look").put("arguments",
            JSONObject().put("accentHex", "#3FA7FF").put("textColor", "soft").put("fontScale", 1.2)
                .put("appTitle", "Mi música").put("launcherIcon", "panda"))).getJSONObject("result")
        assertFalse(look.getBoolean("isError"))
        assertEquals("custom", StoneLook.accent.key)
        assertEquals("#3FA7FF", StoneLook.hex(StoneLook.customAccent))
        assertEquals(StoneInk.SOFT, StoneLook.textInk)
        assertEquals(1.2f, StoneLook.fontScale)
        assertEquals("Mi música", StoneLook.appTitle)
        assertEquals("panda", launcherIcon)
        val state = rpc("tools/call", JSONObject().put("name", "appearance_get")).getJSONObject("result").getJSONObject("structuredContent")
        assertEquals("#3FA7FF", state.getString("accentHex"))

        val bad = rpc("tools/call", JSONObject().put("name", "appearance_set_look")
            .put("arguments", JSONObject().put("accentHex", "azul"))).getJSONObject("result")
        assertTrue(bad.getBoolean("isError"))
        rpc("tools/call", JSONObject().put("name", "appearance_reset_look"))
        assertEquals("gold", StoneLook.accent.key)
    }
}
