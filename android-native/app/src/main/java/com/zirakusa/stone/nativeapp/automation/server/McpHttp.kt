package com.zirakusa.stone.nativeapp.automation.server

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URLDecoder

/** One parsed HTTP/1.1 request; just what the MCP endpoints need. */
internal class McpRequest(val method: String, val path: String, val query: Map<String, String>,
    val headers: Map<String, String>, val body: String) {
    fun header(name: String) = headers[name.lowercase()]

    companion object {
        private const val MAX_BODY = 1 shl 20
        private const val MAX_HEADERS = 64

        /** Reads the request line, headers and body; null when the client sent nothing usable. */
        fun read(client: Socket): McpRequest? {
            val input = BufferedInputStream(client.getInputStream())
            val parts = readLine(input)?.split(' ') ?: return null
            if (parts.size < 2) return null
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                if (headers.size >= MAX_HEADERS) return null
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(body, read, length - read)
                if (count < 0) break
                read += count
            }
            val target = parts[1]
            return McpRequest(parts[0].uppercase(), target.substringBefore('?'), parseQuery(target.substringAfter('?', "")),
                headers, String(body, 0, read, Charsets.UTF_8).trim())
        }

        private fun parseQuery(query: String): Map<String, String> = query.split('&').filter { it.contains('=') }
            .associate { pair ->
                val (key, value) = pair.split('=', limit = 2)
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }

        private fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte < 0) return if (line.isEmpty()) null else line.toString()
                if (byte == '\n'.code) return line.toString().trimEnd('\r')
                if (line.length > 8192) return null
                line.append(byte.toChar())
            }
        }
    }
}

internal object McpHttp {
    /** Status of the last answer written on this thread, for [ServerMetrics]. */
    val lastStatus = ThreadLocal<Int>()

    /** Lets browser-based MCP clients call Stone; every data request still needs the token. */
    val cors = listOf(
        "Access-Control-Allow-Origin: *",
        "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS",
        "Access-Control-Allow-Headers: Authorization, Content-Type, Accept, Mcp-Session-Id, Mcp-Protocol-Version, X-Api-Key, Last-Event-ID",
        "Access-Control-Expose-Headers: Mcp-Session-Id, X-Stone-Server",
    )

    fun respond(out: OutputStream, status: Int, body: Any?, extraHeaders: List<String> = emptyList(),
        contentType: String = "application/json") {
        lastStatus.set(status)
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 $status ${reason(status)}\r\n")
            if (bytes.isNotEmpty()) append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\nConnection: close\r\n")
            append("X-Stone-Server: ${McpServer.identity}\r\n")
            // Answers carry library data and tokens never belong in caches or other pages.
            append("Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n")
            (cors + extraHeaders).forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8)); out.write(bytes); out.flush()
    }

    /** Opens a Server-Sent Events stream (legacy MCP transport). */
    fun startEventStream(out: OutputStream) {
        lastStatus.set(200)
        val head = buildString {
            append("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\nX-Stone-Server: ${McpServer.identity}\r\n")
            McpHttp.cors.forEach { append(it).append("\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8)); out.flush()
    }

    fun event(out: OutputStream, name: String?, data: String) = synchronized(out) {
        val text = buildString {
            if (name != null) append("event: ").append(name).append('\n')
            data.lines().forEach { append("data: ").append(it).append('\n') }
            append('\n')
        }
        out.write(text.toByteArray(Charsets.UTF_8)); out.flush()
    }

    private fun reason(status: Int) = when (status) {
        429 -> "Too Many Requests"
        200 -> "OK"; 202 -> "Accepted"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
        403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 503 -> "Service Unavailable"; else -> "Error"
    }
}
