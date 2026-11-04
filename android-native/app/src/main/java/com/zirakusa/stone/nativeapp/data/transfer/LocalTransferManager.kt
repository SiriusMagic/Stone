package com.zirakusa.stone.nativeapp.data.transfer

import android.content.Context
import android.net.wifi.WifiManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

data class LocalTransferHost(val address: String, val code: String, val file: File)
data class LocalTransferUiState(
    val busy: Boolean = false,
    val address: String = "",
    val code: String = "",
    val message: String = "",
    val progress: Float = 0f,
)

/** One-shot LAN transfer. It exposes only the selected package and closes after one download. */
class LocalTransferManager(private val context: Context) {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var worker: Thread? = null

    @Synchronized fun startHost(file: File): LocalTransferHost {
        stop()
        val socket = ServerSocket(0)
        val code = SecureRandom().nextInt(900_000) + 100_000
        server = socket
        worker = thread(name = "stone-local-transfer", isDaemon = true) {
            try {
                val client = socket.accept()
                client.use { connection ->
                    connection.soTimeout = 15_000
                    val request = connection.getInputStream().bufferedReader().readLine().orEmpty()
                    val expected = "GET /stone/$code HTTP/1.1"
                    if (request == expected) {
                        val output = connection.getOutputStream().bufferedWriter()
                        output.write("HTTP/1.1 200 OK\r\n")
                        output.write("Content-Type: ${StonePackage.MIME}\r\n")
                        output.write("Content-Length: ${file.length()}\r\n")
                        output.write("Content-Disposition: attachment; filename=stone-library.musp\r\n")
                        output.write("Connection: close\r\n\r\n")
                        output.flush()
                        file.inputStream().use { it.copyTo(connection.getOutputStream()) }
                    } else {
                        connection.getOutputStream().bufferedWriter().use {
                            it.write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n")
                        }
                    }
                }
            } catch (_: Exception) {
                // Cancellation and a disconnected peer are expected for a local one-shot transfer.
            } finally { stop() }
        }
        return LocalTransferHost(localAddress(socket.localPort), code.toString(), file)
    }

    fun download(address: String, code: String, target: File, progress: (Long, Long) -> Unit = { _, _ -> }) {
        val cleanAddress = address.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        require(Regex("^[A-Za-z0-9.:-]+$").matches(cleanAddress)) { "Dirección local inválida" }
        require(code.matches(Regex("\\d{6}"))) { "Código inválido" }
        val connection = (URL("http://$cleanAddress/stone/$code").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 60_000; requestMethod = "GET"
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { "El teléfono emisor rechazó la transferencia" }
            val total = connection.contentLengthLong
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { output -> connection.inputStream.use { input ->
                val buffer = ByteArray(32 * 1024); var done = 0L
                while (true) {
                    val count = input.read(buffer); if (count <= 0) break
                    output.write(buffer, 0, count); done += count; progress(done, total)
                }
                require(total <= 0 || done == total) { "Transferencia incompleta" }
            } }
        } finally { connection.disconnect() }
    }

    @Synchronized fun stop() {
        runCatching { server?.close() }
        server = null; worker = null
    }

    private fun localAddress(port: Int): String {
        val manager = context.applicationContext.getSystemService(WifiManager::class.java)
        val address = manager?.connectionInfo?.ipAddress ?: 0
        if (address == 0) return "IP-no-disponible:$port"
        val ip = listOf(address and 0xff, address shr 8 and 0xff, address shr 16 and 0xff,
            address shr 24 and 0xff).joinToString(".")
        return "$ip:$port"
    }
}
