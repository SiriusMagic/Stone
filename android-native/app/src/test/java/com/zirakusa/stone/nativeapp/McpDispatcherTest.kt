package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.provider.McpDispatcher
import com.zirakusa.stone.nativeapp.automation.provider.CallerGrant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stone's MCP handling without any network server: protocol errors and notifications. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpDispatcherTest {
    private val grant = CallerGrant("t1", "Prueba", AccessMode.READ, setOf("library_search"))


    @Test fun notificationsAndBadJson() {
        assertEquals(null, McpDispatcher.dispatch("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", grant).first)
        assertEquals(-32700, (McpDispatcher.dispatch("{", grant).first as JSONObject).getJSONObject("error").getInt("code"))
    }

    @Test fun initializeAndReadOnlyListing() {
        val init = McpDispatcher.dispatch("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""", grant).first as JSONObject
        assertEquals("2025-06-18", init.getJSONObject("result").getString("protocolVersion"))
        val tools = (McpDispatcher.dispatch("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", grant).first as JSONObject).getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue(names.isNotEmpty() && "library_search" !in names && "player_play" !in names)
    }
}
