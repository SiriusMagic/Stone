package com.zirakusa.stone.nativeapp

import android.os.Looper
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.AutomationHost
import com.zirakusa.stone.nativeapp.automation.provider.CallerGrant
import com.zirakusa.stone.nativeapp.automation.provider.McpDispatcher
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.data.library.user.StonePlaylist
import com.zirakusa.stone.nativeapp.playback.control.AudioMode
import com.zirakusa.stone.nativeapp.playback.control.PlaybackState
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
import java.util.concurrent.CompletableFuture

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** Stone's tools through its MCP dispatcher, as Gateway Hub reaches them (no HTTP server any more). */
class McpToolsTest {
    private val token = "test-token"
    private val songs = listOf(
        Song("content://fixture/1.mp3", "After Dark", "Mr.Kitty", "Time", "Music", 258_000, 1, 1),
        Song("content://fixture/2.mp3", "Skyfall", "Adele", "Skyfall", "Music", 286_000, 1, 1))
    private var toggles = 0
    private var playlistsCreated = listOf<String>()
    private var sleepMinutes = 0
    private var launcherIcon = "penguin"
    private var liked = setOf<String>()

    @Before fun start() {
        StoneAutomation.host = object : AutomationHost {
            override val playback = PlaybackState(ready = true, title = "After Dark", artist = "Mr.Kitty", playing = false,
                currentId = songs[0].id)
            override val songs get() = this@McpToolsTest.songs
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
            override fun setFavorite(songId: String, favorite: Boolean) { liked = if (favorite) liked + songId else liked - songId }
        }
    }

    @After fun stop() { StoneAutomation.host = null }

    /** Who each "token" is: the rights Gateway delegates for a request (Stone has no tokens of its own any more). */
    private val grants = mutableMapOf(token to CallerGrant("main", "Principal", AccessMode.WRITE, emptySet()))

    /** One MCP message through Stone's dispatcher; tools run on the main looper, which is pumped meanwhile. */
    private fun post(body: String, auth: String? = "Bearer $token"): Pair<Int, String> {
        val grant = auth?.removePrefix("Bearer ")?.let { grants[it] } ?: return 401 to ""
        val call = CompletableFuture.supplyAsync { McpDispatcher.dispatch(body, grant).first }
        while (!call.isDone) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        return call.get()?.let { 200 to it.toString() } ?: (202 to "")
    }

    private fun rpc(method: String, params: JSONObject = JSONObject()): JSONObject {
        val (code, text) = post(JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString())
        assertEquals(200, code)
        return JSONObject(text)
    }

    @Test fun eachTokenOnlySeesAndUsesItsAllowedTools() {
        grants[token] = CallerGrant("a", "GPT", AccessMode.FULL, setOf("player_state"))
        val list = post("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""").second
        assertFalse(JSONObject(list).getJSONObject("result").getJSONArray("tools").toString().contains("\"player_state\""))
        val call = post("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"player_state","arguments":{}}}""").second
        assertTrue(call.contains("TOOL_DISABLED"))
    }


    @Test fun helpFindsToolsByIntentionAndUnknownToolsAreProtocolErrors() {
        val help = rpc("tools/call", JSONObject().put("name", "stone_help")
            .put("arguments", JSONObject().put("query", "brightness"))).getJSONObject("result").getJSONObject("structuredContent")
        val names = help.getJSONArray("tools").let { found -> (0 until found.length()).map { found.getJSONObject(it).getString("name") } }
        assertTrue("video_set_brightness" in names)
        val unknown = rpc("tools/call", JSONObject().put("name", "player_fly"))
        assertEquals(-32602, unknown.getJSONObject("error").getInt("code"))
        val band = rpc("tools/call", JSONObject().put("name", "equalizer_set_band")
            .put("arguments", JSONObject().put("band", 12).put("value", 3))).getJSONObject("result")
        assertTrue(band.getBoolean("isError"))
        val bands = rpc("tools/call", JSONObject().put("name", "equalizer_set_bands")
            .put("arguments", JSONObject().put("values", "5,4,2,0,0,0,1,2,3,4"))).getJSONObject("result")
        assertFalse(bands.getBoolean("isError"))
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

    @Test fun eachTokenHasItsOwnModeAndErrorsCarryCodes() {
        grants += mapOf(token to CallerGrant("w", "Claude", AccessMode.WRITE, emptySet()),
            "read-token" to CallerGrant("r", "Lector", AccessMode.READ, emptySet()), "full-token" to CallerGrant("f", "Total", AccessMode.FULL, emptySet()))
        fun rpcAs(secret: String, method: String, params: JSONObject = JSONObject()): JSONObject {
            val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()
            val (code, text) = post(body, auth = "Bearer $secret")
            assertEquals(200, code)
            return JSONObject(text)
        }
        fun names(secret: String) = rpcAs(secret, "tools/list").getJSONObject("result").getJSONArray("tools")
            .let { tools -> (0 until tools.length()).map { tools.getJSONObject(it).getString("name") } }
        assertTrue(names(token).containsAll(listOf("metadata_update", "metadata_undo", "metadata_rename", "playlist_delete",
            "appearance_set_artwork_shape", "appearance_set_font_style", "metadata_suggest_tags")))
        val readable = names("read-token")
        assertFalse("player_play" in readable)
        assertTrue(readable.containsAll(listOf("library_search", "metadata_get", "metadata_history", "player_state")))

        val blocked = rpcAs("read-token", "tools/call", JSONObject().put("name", "player_play")).getJSONObject("result")
        assertTrue(blocked.getBoolean("isError"))
        assertEquals("READ_ONLY_TOKEN", blocked.getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        assertEquals(0, toggles)
        // Full access never waits for the phone: a critical tool fails fast on its own error instead.
        val delete = rpcAs("full-token", "tools/call", JSONObject().put("name", "playlist_delete")
            .put("arguments", JSONObject().put("playlistId", "nope"))).getJSONObject("result")
        assertTrue(delete.getBoolean("isError"))
        assertFalse(delete.toString().contains("USER_DENIED"))
        val unknown = rpcAs(token, "tools/call", JSONObject().put("name", "player_seek")
            .put("arguments", JSONObject().put("bogus", 1))).getJSONObject("result")
        assertEquals("INVALID_PARAM", unknown.getJSONObject("structuredContent").getJSONObject("error").getString("code"))
        assertEquals(401, post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", auth = "Bearer revoked").first)
    }

    @Test fun accessModesOnlyAskForCriticalActions() {
        assertFalse(AccessMode.WRITE.needsApproval(ActionRisk.PERSISTENT))
        assertTrue(AccessMode.WRITE.needsApproval(ActionRisk.CRITICAL))
        assertFalse(AccessMode.FULL.needsApproval(ActionRisk.CRITICAL))
        assertFalse(AccessMode.READ.permits(ActionRisk.REVERSIBLE))
        assertTrue(AccessMode.READ.permits(ActionRisk.READ))
    }

    @Test fun likesAnySongAndSingleToolsCanBeSwitchedOff() {
        fun call(name: String, args: JSONObject = JSONObject()) =
            rpc("tools/call", JSONObject().put("name", name).put("arguments", args)).getJSONObject("result")
        val like = call("library_set_favorite", JSONObject().put("songIds", songs[1].id).put("favorite", true))
        assertFalse(like.getBoolean("isError"))
        assertEquals(setOf(songs[1].id), liked)
        val main = grants.getValue(token)
        try {
            grants[token] = main.copy(disabledTools = setOf("library_set_favorite"))
            val names = rpc("tools/list").getJSONObject("result").getJSONArray("tools")
                .let { tools -> (0 until tools.length()).map { tools.getJSONObject(it).getString("name") } }
            assertFalse("library_set_favorite" in names)
            assertTrue(names.containsAll(listOf("audio_set_level", "settings_set_transitions", "playlist_rename")))
            assertTrue(call("library_set_favorite", JSONObject().put("songIds", songs[1].id).put("favorite", false)).getBoolean("isError"))
            assertEquals(setOf(songs[1].id), liked)
        } finally { grants[token] = main }
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
