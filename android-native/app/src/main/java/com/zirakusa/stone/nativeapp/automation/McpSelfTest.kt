package com.zirakusa.stone.nativeapp.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/** Result of calling Stone's own MCP address the way an agent would. */
sealed class McpCheck(val url: String) {
    class Ok(url: String) : McpCheck(url)
    /** Something answered, but it is not this Stone (another app holds the port). */
    class OtherProgram(url: String, val answer: String) : McpCheck(url)
    class NoAnswer(url: String, val reason: String) : McpCheck(url)
}

/** "Probar conexión": sends a real MCP initialize with the token to every address Stone shows. */
object McpSelfTest {
    suspend fun run(urls: List<String>, token: String): List<McpCheck> = withContext(Dispatchers.IO) {
        urls.map { check(it, token) }
    }

    private fun check(url: String, token: String): McpCheck = try {
        val connection = URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 5_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.setRequestProperty("Authorization", "Bearer $token")
        val body = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",""" +
            """"capabilities":{},"clientInfo":{"name":"stone-self-test","version":"1"}}}"""
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val server = connection.getHeaderField("X-Stone-Server")
        val text = (if (code < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        connection.disconnect()
        val name = runCatching { JSONObject(text).getJSONObject("result").getJSONObject("serverInfo").getString("name") }.getOrNull()
        when {
            code == 200 && name == "stone" && server == McpServer.identity -> McpCheck.Ok(url)
            server == McpServer.identity -> McpCheck.NoAnswer(url, "HTTP $code ${text.take(120)}")
            else -> McpCheck.OtherProgram(url, "HTTP $code ${text.take(120)}")
        }
    } catch (failure: Exception) {
        McpCheck.NoAnswer(url, failure.javaClass.simpleName + (failure.message?.let { ": $it" } ?: ""))
    }
}
