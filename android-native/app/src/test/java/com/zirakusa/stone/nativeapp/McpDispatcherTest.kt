package com.zirakusa.stone.nativeapp

import com.zirakusa.stone.nativeapp.automation.auth.AccessMode
import com.zirakusa.stone.nativeapp.automation.auth.ApiToken
import com.zirakusa.stone.nativeapp.automation.provider.McpDispatcher
import com.zirakusa.stone.nativeapp.automation.provider.callerGrant
import com.zirakusa.stone.nativeapp.automation.server.McpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/** The MCP handling moved out of the HTTP server: the same request gives the same answer either way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpDispatcherTest {
    private val port = 18_766
    private val token = ApiToken("t1", "Prueba", "stn_dispatcher_test_token", AccessMode.READ, 0, disabledTools = setOf("library_search"))

    @After fun stop() = McpServer.stop()

    private fun overHttp(body: String): JSONObject {
        val c = URL("http://127.0.0.1:$port/mcp").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer ${token.secret}"); c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
    }

    @Test fun httpAndDispatcherAnswerTheSame() {
        assertTrue(McpServer.start(listOf(token), port))
        for (body in listOf("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""",
            """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", """{"jsonrpc":"2.0","id":3,"method":"nope"}""")) {
            val direct = McpDispatcher.dispatch(body, token.callerGrant()).first.toString()
            assertEquals(body, JSONObject(direct).toString(), overHttp(body).toString())
        }
    }

    @Test fun notificationsAndBadJson() {
        assertEquals(null, McpDispatcher.dispatch("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", token.callerGrant()).first)
        assertEquals(-32700, (McpDispatcher.dispatch("{", token.callerGrant()).first as JSONObject).getJSONObject("error").getInt("code"))
    }
}
