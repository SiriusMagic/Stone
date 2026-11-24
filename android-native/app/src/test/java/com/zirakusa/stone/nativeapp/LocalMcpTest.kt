package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.automation.LocalMcp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalMcpTest {
    private val list = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""

    @Test fun localPermissionIsRequiredOnEveryRequest() {
        assertEquals("NOT_SAME_DEVELOPER", LocalMcp.answer(false, true, list).getString("error"))
        assertEquals("LOCAL_MCP_OFF", LocalMcp.answer(true, false, list).getString("error"))
        val result = JSONObject(LocalMcp.answer(true, true, list).getString("response")!!)
        val tools = result.getJSONObject("result").getJSONArray("tools")
        assertEquals(setOf("library_search", "player_state"),
            (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }.toSet())
        assertEquals("LOCAL_MCP_OFF", LocalMcp.answer(true, false, list).getString("error"))
    }

    @Test fun onlyExposesTheTwoReadToolsAndAcceptsAnObjectForArguments() {
        fun call(tool: String, args: String) =
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"$tool","arguments":$args}}"""
        fun dispatch(request: String, allowed: Set<String>): String {
            assertEquals(setOf("library_search", "player_state"), allowed)
            assertEquals("library_search", JSONObject(request).getJSONObject("params").getString("name"))
            return "{}"
        }
        assertNotNull(LocalMcp.answer(true, true, call("library_search", """{"query":"Daft Punk"}"""), ::dispatch)
            .getString("response"))
        assertEquals("TOOL_NOT_ALLOWED", LocalMcp.answer(true, true, call("player_pause", "{}"), ::dispatch).getString("error"))
        assertEquals("BAD_REQUEST", LocalMcp.answer(true, true, call("library_search", "[]"), ::dispatch).getString("error"))
        assertEquals("BAD_REQUEST", LocalMcp.answer(true, true, "[$list]", ::dispatch).getString("error"))
        assertEquals("NOT_READY", LocalMcp.answer(true, true, call("library_search", "{}")) { _, _ -> null }.getString("error"))
    }
}
